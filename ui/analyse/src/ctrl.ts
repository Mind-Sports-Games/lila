import * as cg from 'chessground/types';
import {
  oppositeOrientation,
  oppositeOrientationForLOA,
  orientationForLOA,
  oppositeOrientationForBackgammon,
  orientationForBackgammon,
} from 'chessground/util';
import * as stratUtils from 'stratutils';
import * as game from 'game';
import * as keyboard from './keyboard';
import * as promotion from './promotion';
import * as speech from './speech';
import * as util from './util';
import * as xhr from 'common/xhr';
import debounce from 'common/debounce';
import explorerCtrl from './explorer/explorerCtrl';
import GamebookPlayCtrl from './study/gamebook/gamebookPlayCtrl';
import makeStudy from './study/studyCtrl';
import throttle from 'common/throttle';
import { AnalyseOpts, AnalyseData, ServerEvalData, Key, JustCaptured, NvuiPlugin, Redraw } from './interfaces';
import { Api as ChessgroundApi } from 'chessground/api';
import { Autoplay, AutoplayDelay } from './autoplay';
import { build as makeTree, path as treePath, ops as treeOps, TreeWrapper } from 'tree';
import { compute as computeAutoShapes } from './autoShape';
import { Config as ChessgroundConfig } from 'chessground/config';
import { setDropMode, cancelDropMode } from 'chessground/drop';
import { ActionMenuCtrl } from './actionMenu';
import { ctrl as cevalCtrl, isEvalBetter, sanIrreversible, CevalCtrl, Work as CevalWork, CevalOpts } from 'ceval';
import { ctrl as treeViewCtrl, TreeView } from './treeView/treeView';
import { defined, prop, Prop } from 'common';
import { DrawShape } from 'chessground/draw';
import { ExplorerCtrl } from './explorer/interfaces';
import { ForecastCtrl } from './forecast/interfaces';
import { amazonsChessgroundFen } from 'stratops/compat';
import { make as makeEvalCache, EvalCache } from './evalCache';
import { make as makeForecast } from './forecast/forecastCtrl';
import { make as makeFork, ForkCtrl } from './fork';
import { make as makePractice, PracticeCtrl } from './practice/practiceCtrl';
import { make as makeRetro, RetroCtrl } from './retrospect/retroCtrl';
import { make as makeSocket, Socket } from './socket';
import { nextGlyphSymbol } from './nodeFinder';
import { opposite, parseUci, makeSquare, roleToChar } from 'stratops/util';
import { PLAYERINDEXES, Outcome, isNormal } from 'stratops/types';
import { SquareSet } from 'stratops/squareSet';
import { parseFen } from 'stratops/fen';
import { Position, PositionError } from 'stratops/chess';
import { Result } from '@badrap/result';
import { storedProp, StoredBooleanProp } from 'common/storage';
import { AnaMove, AnaDrop, AnaPass, StudyCtrl } from './study/interfaces';
import { StudyPracticeCtrl } from './study/practice/interfaces';
import { valid as crazyValid } from './crazy/crazyCtrl';
import { isOnlyDropsPly } from './util';
import { variantClassFromKey, variantKeyToRules } from 'stratops/variants/util';

export default class AnalyseCtrl {
  data: AnalyseData;
  element: HTMLElement;

  tree: TreeWrapper;
  socket: Socket;
  chessground: ChessgroundApi;
  trans: Trans;
  ceval: CevalCtrl;
  evalCache: EvalCache;

  // current tree state, cursor, and denormalized node lists
  path: Tree.Path;
  node: Tree.Node;
  nodeList: Tree.Node[];
  mainline: Tree.Node[];

  // sub controllers
  actionMenu: ActionMenuCtrl;
  autoplay: Autoplay;
  explorer: ExplorerCtrl;
  forecast?: ForecastCtrl;
  retro?: RetroCtrl;
  fork: ForkCtrl;
  practice?: PracticeCtrl;
  study?: StudyCtrl;
  studyPractice?: StudyPracticeCtrl;

  // state flags
  justPlayed?: string; // pos
  justDropped?: string; // role
  justCaptured?: JustCaptured;
  autoScrollRequested = false;
  redirecting = false;
  onMainline = true;
  synthetic: boolean; // false if coming from a real game
  ongoing: boolean; // true if real game is ongoing

  // display flags
  flipped = false;
  embed: boolean;
  showComments = true; // whether to display comments in the move tree
  showAutoShapes: StoredBooleanProp = storedProp('show-auto-shapes', true);
  showGauge: StoredBooleanProp = storedProp('show-gauge', true);
  showComputer: StoredBooleanProp = storedProp('show-computer', true);
  showMoveAnnotation: StoredBooleanProp = storedProp('show-move-annotation', true);
  keyboardHelp: boolean = location.hash === '#keyboard';
  threatMode: Prop<boolean> = prop(false);
  treeView: TreeView;
  cgVersion = {
    js: 1, // increment to recreate chessground
    dom: 1,
  };

  // underboard inputs
  fenInput?: string;
  pgnInput?: string;
  sgfInput?: string;

  // other paths
  initialPath: Tree.Path;
  contextMenuPath?: Tree.Path;
  gamePath?: Tree.Path;

  // misc
  cgConfig: any; // latest chessground config (useful for revert)
  music?: any;
  nvui?: NvuiPlugin;

  constructor(
    readonly opts: AnalyseOpts,
    readonly redraw: Redraw,
  ) {
    this.data = opts.data;
    this.element = opts.element;
    this.embed = opts.embed;
    this.trans = opts.trans;
    this.treeView = treeViewCtrl(opts.embed ? 'inline' : 'column');

    if (this.data.forecast) this.forecast = makeForecast(this.data.forecast, this.data, redraw);

    if (playstrategy.AnalyseNVUI) this.nvui = playstrategy.AnalyseNVUI(redraw) as NvuiPlugin;

    this.instanciateEvalCache();

    this.initialize(this.data, false);

    this.instanciateCeval();

    this.initialPath = treePath.root;

    {
      const loc = window.location,
        hashPly = loc.hash === '#last' ? this.tree.lastPly() : parseInt(loc.hash.substr(1));
      if (hashPly) {
        // remove location hash - https://stackoverflow.com/questions/1397329/how-to-remove-the-hash-from-window-location-with-javascript-without-page-refresh/5298684#5298684
        window.history.replaceState(null, '', loc.pathname + loc.search);
        const mainline = treeOps.mainlineNodeList(this.tree.root);
        this.initialPath = treeOps.takePathWhile(mainline, n => n.ply <= hashPly);
      }
    }

    this.setPath(this.initialPath);

    this.showGround();
    this.onToggleComputer();
    this.startCeval();
    this.explorer.setNode();
    this.study = opts.study
      ? makeStudy(opts.study, this, (opts.tagTypes || '').split(','), opts.practice, opts.relay)
      : undefined;
    this.studyPractice = this.study ? this.study.practice : undefined;

    if (location.hash === '#practice' || (this.study && this.study.data.chapter.practice)) this.togglePractice();
    else if (location.hash === '#menu') playstrategy.requestIdleCallback(this.actionMenu.toggle, 500);

    keyboard.bind(this);

    playstrategy.pubsub.on('jump', (ply: string) => {
      this.jumpToMain(parseInt(ply));
      this.redraw();
    });

    playstrategy.pubsub.on('sound_set', (set: string) => {
      if (!this.music && set === 'music')
        playstrategy.loadScriptCJS('javascripts/music/replay.js').then(() => {
          this.music = window.playstrategyReplayMusic();
        });
      if (this.music && set !== 'music') this.music = null;
    });

    playstrategy.pubsub.on('analysis.change.trigger', this.onChange);
    playstrategy.pubsub.on('analysis.chart.click', index => {
      this.jumpToIndex(index);
      this.redraw();
    });

    speech.setup();
  }

  initialize(data: AnalyseData, merge: boolean): void {
    this.data = data;
    this.synthetic = data.game.id === 'synthetic';
    this.ongoing = !this.synthetic && game.playable(data);

    const prevTree = merge && this.tree.root;
    this.tree = makeTree(util.treeReconstruct(this.data.treeParts));
    if (prevTree) this.tree.merge(prevTree);

    this.actionMenu = new ActionMenuCtrl();
    this.autoplay = new Autoplay(this);
    if (this.socket) this.socket.clearCache();
    else this.socket = makeSocket(this.opts.socketSend, this);
    this.explorer = explorerCtrl(this, this.opts.explorer, this.explorer ? this.explorer.allowed() : !this.embed);
    this.gamePath =
      this.synthetic || this.ongoing ? undefined : treePath.fromNodeList(treeOps.mainlineNodeList(this.tree.root));
    this.fork = makeFork(this);

    playstrategy.sound.preloadBoardSounds();
  }

  private setPath = (path: Tree.Path): void => {
    this.path = path;
    this.nodeList = this.tree.getNodeList(path);
    this.node = treeOps.last(this.nodeList) as Tree.Node;
    this.mainline = treeOps.mainlineNodeList(this.tree.root);
    this.onMainline = this.tree.pathIsMainline(path);
    this.fenInput = undefined;
    this.pgnInput = undefined;
    this.sgfInput = undefined;
  };

  flip = () => {
    this.flipped = !this.flipped;
    this.chessground.set({
      orientation: this.getOrientation(),
    });
    if (this.retro && this.data.game.variant.key !== 'racingKings') {
      this.retro = makeRetro(this, this.bottomPlayerIndex());
    }
    if (this.practice) this.restartPractice();
    this.onChange();
    this.redraw();
  };

  topPlayerIndex(): PlayerIndex {
    return opposite(this.bottomPlayerIndex());
  }

  bottomPlayerIndex(): PlayerIndex {
    return this.getOrientation() === 'p1' ? 'p1' : 'p2';
  }

  bottomIsP1 = () => this.bottomPlayerIndex() === 'p1';

  getOrientation(): Orientation {
    if (this.data.game.variant.key === 'linesOfAction' || this.data.game.variant.key === 'scrambledEggs') {
      const c = this.data.player.playerIndex;
      return this.flipped ? oppositeOrientationForLOA(c) : orientationForLOA(c);
    } else if (
      this.data.game.variant.key === 'backgammon' ||
      this.data.game.variant.key === 'hyper' ||
      this.data.game.variant.key === 'nackgammon'
    ) {
      const c = this.data.player.playerIndex;
      return this.flipped ? oppositeOrientationForBackgammon(c) : orientationForBackgammon(c);
    } else if (this.data.game.variant.key === 'racingKings') {
      return 'p1';
    } else {
      const o = this.data.orientation;
      return this.flipped ? oppositeOrientation(o) : o;
    }
  }
  getNode(): Tree.Node {
    // required by ui/ceval
    return this.node;
  }

  turnPlayerIndex(): PlayerIndex {
    return this.node.playerIndex;
  }

  togglePlay(delay: AutoplayDelay): void {
    this.autoplay.toggle(delay);
    this.actionMenu.open = false;
  }

  private uciToLastMove(uci?: Uci): Key[] | undefined {
    if (!uci || uci == 'pass' || uci.substring(0, 3) == 'ss:') return;
    const pos = uci.match(/[a-z][1-9][0-9]?/g) as Key[];
    if (uci[1] === '@') return [pos[0], pos[0]] as Key[];
    return [pos[0], pos[1]] as Key[];
  }

  setDropMode(cg: ChessgroundApi) {
    const playerIndex = cg.state.movable.playerIndex as cg.PlayerIndex;
    setDropMode(cg.state, stratUtils.onlyDropsVariantPiece(cg.state.variant as VariantKey, playerIndex));
    cg.set({
      dropmode: {
        showDropDests: !['go9x9', 'go13x13', 'go19x19', 'backgammon', 'hyper', 'nackgammon'].includes(
          cg.state.variant as VariantKey,
        ),
        dropDests: stratUtils.readDropsByRole(this.node.dropsByRole),
      },
    });
  }

  private showGround(): void {
    this.onChange();
    if (!defined(this.node.dests)) this.getDests();
    this.withCg(cg => {
      cg.set(this.makeCgOpts());
      this.setAutoShapes();
      this.setDropMode(cg);
      if (this.node.shapes) cg.setShapes(this.node.shapes as DrawShape[]);
    });
  }

  getDests: () => void = throttle(800, () => {
    if (!this.embed && !defined(this.node.dests))
      this.socket.sendAnaDests({
        variant: this.data.game.variant.key,
        lib: this.data.game.variant.lib,
        fen: this.node.fen,
        path: this.path,
      });
  });

  makeCgOpts(): ChessgroundConfig {
    const node = this.node,
      playerIndex = this.turnPlayerIndex(),
      dests = stratUtils.readDests(this.node.dests),
      drops = stratUtils.readDrops(this.node.drops),
      dropsByRole = stratUtils.readDrops(this.node.dropsByRole),
      variantKey = this.data.game.variant.key,
      movablePlayerIndex = this.gamebookPlay()
        ? playerIndex
        : this.practice
          ? this.bottomPlayerIndex()
          : !this.embed &&
              ((dests && dests.size > 0) || drops === null || drops.length || dropsByRole == null || dropsByRole.length)
            ? playerIndex
            : undefined,
      config: ChessgroundConfig = {
        fen: this.data.game.variant.key == 'amazons' ? amazonsChessgroundFen(node.fen) : node.fen,
        turnPlayerIndex: playerIndex,
        movable: this.embed
          ? {
              playerIndex: undefined,
              dests: new Map(),
            }
          : {
              playerIndex: movablePlayerIndex,
              dests: (movablePlayerIndex === playerIndex && dests) || new Map(),
            },
        check: !!node.check,
        lastMove: this.uciToLastMove(node.uci),
        onlyDropsVariant: isOnlyDropsPly(node, variantKey, this.data.onlyDropsVariant),
      };
    if (!dests && !node.check) {
      // premove while dests are loading from server
      // can't use when in check because it highlights the wrong king
      config.turnPlayerIndex = opposite(playerIndex);
      config.movable!.playerIndex = playerIndex;
    }
    config.premovable = {
      enabled: false,
    };
    this.cgConfig = config;
    return config;
  }

  private throttleSound = (name: string) => throttle(100, () => playstrategy.sound.play(name));

  private sound = {
    move: this.throttleSound('move'),
    capture: this.throttleSound('capture'),
    check: this.throttleSound('check'),
  };

  private onChange: () => void = throttle(300, () => {
    playstrategy.pubsub.emit('analysis.change', this.node.fen, this.path, this.onMainline ? this.node.ply : false);
  });

  private updateHref: () => void = debounce(() => {
    if (!this.opts.study) window.history.replaceState(null, '', '#' + this.node.ply);
  }, 750);

  autoScroll(): void {
    this.autoScrollRequested = true;
  }

  playedLastMoveMyself = () => !!this.justPlayed && !!this.node.uci && this.node.uci.startsWith(this.justPlayed);

  private onCancelDropMode = () => {
    //redraw pocket - due to possible selection in CG and dropmode cancelled
    if (['crazyhouse', 'shogi', 'minishogi'].includes(this.data.game.variant.key)) {
      this.redraw();
    }
  };

  makeCgHooks = () => ({
    onCancelDropMode: this.onCancelDropMode,
  });

  jump(path: Tree.Path): void {
    const pathChanged = path !== this.path,
      isForwardStep = pathChanged && path.length == this.path.length + 2;
    this.setPath(path);
    this.showGround();
    if (
      (this.data.game.variant.key === 'togyzkumalak' || this.data.game.variant.key === 'bestemshe') &&
      this.chessground
    ) {
      this.chessground.redrawAll(); //redraw board scores
    }
    if (pathChanged) {
      const playedMyself = this.playedLastMoveMyself();
      if (this.study) this.study.setPath(path, this.node, playedMyself);
      if (isForwardStep) {
        if (!this.node.uci) this.sound.move();
        // initial position
        else if (!playedMyself) {
          if (this.node.san!.includes('x')) this.sound.capture();
          else this.sound.move();
        }
        if (/\+|#/.test(this.node.san!)) this.sound.check();
      }
      this.threatMode(false);
      this.ceval.stop();
      this.startCeval();
      speech.node(this.node);
    }
    this.justPlayed = this.justDropped = this.justCaptured = undefined;
    this.explorer.setNode();
    this.updateHref();
    this.autoScroll();
    promotion.cancel(this);
    if (pathChanged) {
      if (this.retro) this.retro.onJump();
      if (this.practice) this.practice.onJump();
      if (this.study) this.study.onJump();
    }
    if (this.music) this.music.jump(this.node);
    playstrategy.pubsub.emit('ply', this.node.ply);
  }

  userJump = (path: Tree.Path): void => {
    this.autoplay.stop();
    this.withCg(cg => cg.selectSquare(null));
    if (this.practice) {
      const prev = this.path;
      this.practice.preUserJump(prev, path);
      this.jump(path);
      this.practice.postUserJump(prev, this.path);
    } else this.jump(path);
  };

  private canJumpTo(path: Tree.Path): boolean {
    return !this.study || this.study.canJumpTo(path);
  }

  userJumpIfCan(path: Tree.Path): void {
    if (this.canJumpTo(path)) this.userJump(path);
  }

  mainlinePathToPly(ply: Ply): Tree.Path {
    return treeOps.takePathWhile(this.mainline, n => n.ply <= ply);
  }

  jumpToMain = (ply: Ply): void => {
    this.userJump(this.mainlinePathToPly(ply));
  };

  jumpToIndex = (index: number): void => {
    this.jumpToMain(index + 1 + this.tree.root.ply);
  };

  jumpToGlyphSymbol(playerIndex: PlayerIndex, symbol: string): void {
    const node = nextGlyphSymbol(playerIndex, symbol, this.mainline, this.node.ply);
    if (node) this.jumpToMain(node.ply);
    this.redraw();
  }

  reloadData(data: AnalyseData, merge: boolean): void {
    this.initialize(data, merge);
    this.redirecting = false;
    this.setPath(treePath.root);
    this.instanciateCeval();
    this.instanciateEvalCache();
    this.cgVersion.js++;
  }

  changePgn(pgn: string): void {
    this.redirecting = true;
    xhr
      .json('/analysis/pgn', {
        method: 'post',
        body: xhr.form({ pgn }),
      })
      .then(
        (data: AnalyseData) => {
          this.reloadData(data, false);
          this.userJump(this.mainlinePathToPly(this.tree.lastPly()));
          this.redraw();
        },
        error => {
          console.log(error);
          this.redirecting = false;
          this.redraw();
        },
      );
  }

  changeFen(fen: Fen): void {
    this.redirecting = true;
    window.location.href =
      '/analysis/' +
      this.data.game.variant.key +
      '/' +
      encodeURIComponent(fen).replace(/%20/g, '_').replace(/%2F/g, '/');
  }

  userNewPiece = (piece: cg.Piece, pos: Key): void => {
    if (crazyValid(this.chessground, this.data, this.node.drops, this.node.dropsByRole, piece, pos)) {
      this.justPlayed = ['go9x9', 'go13x13', 'go19x19'].includes(this.data.game.variant.key)
        ? roleToChar(piece.role) + '@' + pos
        : roleToChar(piece.role).toUpperCase() + '@' + pos;
      this.justDropped = piece.role;
      this.justCaptured = undefined;
      this.sound.move();
      const drop: AnaDrop = {
        role: piece.role,
        pos,
        variant: this.data.game.variant.key,
        lib: this.data.game.variant.lib,
        fen: this.node.fen,
        path: this.path,
      };
      this.socket.sendAnaDrop(drop);
      this.preparePremoving();
      this.redraw();
    } else this.jump(this.path);
    if (!this.data.onlyDropsVariant || this.data.game.variant.key === 'amazons') {
      cancelDropMode(this.chessground.state);
      this.redraw();
    }
  };

  userMove = (orig: Key, dest: Key, capture?: JustCaptured): void => {
    this.justPlayed = orig;
    this.justDropped = undefined;
    const piece = this.chessground.state.pieces.get(dest);
    const isCapture =
      capture ||
      (this.data.game.gameFamily !== 'breakthroughtroyka' && piece && piece.role == 'p-piece' && orig[0] != dest[0]);
    this.sound[isCapture ? 'capture' : 'move']();
    if (!promotion.start(this, orig, dest, capture, this.sendMove)) this.sendMove(orig, dest, capture);
    if (!this.data.onlyDropsVariant) cancelDropMode(this.chessground.state);
  };

  sendMove = (orig: Key, dest: Key, capture?: JustCaptured, prom?: cg.Role): void => {
    const move: AnaMove = {
      orig,
      dest,
      variant: this.data.game.variant.key,
      lib: this.data.game.variant.lib,
      fen: this.node.fen,
      path: this.path,
    };
    if (capture) this.justCaptured = capture;
    if (prom) move.promotion = prom;
    if (this.practice) this.practice.onUserMove();
    this.socket.sendAnaMove(move);
    this.preparePremoving();
    this.redraw();
  };

  sendPass = (): void => {
    const pass: AnaPass = {
      variant: this.data.game.variant.key,
      lib: this.data.game.variant.lib,
      fen: this.node.fen,
      path: this.path,
    };
    if (this.practice) this.practice.onUserMove();
    this.socket.sendAnaPass(pass);
    this.preparePremoving();
    this.redraw();
  };

  cancelMove = (): void => {
    this.reset();
  };

  private preparePremoving(): void {
    this.chessground.set({
      turnPlayerIndex: this.chessground.state.movable.playerIndex as cg.PlayerIndex,
      movable: {
        playerIndex: opposite(this.chessground.state.movable.playerIndex as cg.PlayerIndex),
      },
      premovable: {
        enabled: true,
      },
    });
  }

  onPremoveSet = () => {
    if (this.study) this.study.onPremoveSet();
  };

  addNode(node: Tree.Node, path: Tree.Path) {
    const newPath = this.tree.addNode(node, path);
    if (!newPath) {
      return this.redraw();
    }
    this.jump(newPath);
    this.redraw();
    this.chessground.playPremove();
    const parsedDests = stratUtils.readDests(node.dests);
    if (parsedDests) this.maybeForceMove(parsedDests);
  }

  addDests(dests: string, path: Tree.Path): void {
    this.tree.addDests(dests, path);
    if (path === this.path) {
      this.showGround();
      if (this.outcome()) this.ceval.stop();
    }
    this.withCg(cg => cg.playPremove());
  }

  private maybeForceMove(possibleMoves: cg.Dests) {
    if (
      (this.data.game.variant.key === 'flipello' || this.data.game.variant.key === 'flipello10') &&
      possibleMoves.size == 1
    ) {
      const passOrig = possibleMoves.keys().next().value;
      if (passOrig != undefined) {
        const passDests = possibleMoves.get(passOrig);
        if (passDests && passDests.length == 1) {
          const passDest = passDests[0];
          this.sendMove(passOrig, passDest, undefined, undefined);
        }
      }
    }
  }

  deleteNode(path: Tree.Path): void {
    const node = this.tree.nodeAtPath(path);
    if (!node) return;
    const count = treeOps.countChildrenAndComments(node);
    if (
      (count.nodes >= 10 || count.comments > 0) &&
      !confirm(
        'Delete ' +
          util.plural('move', count.nodes) +
          (count.comments ? ' and ' + util.plural('comment', count.comments) : '') +
          '?',
      )
    )
      return;
    this.tree.deleteNodeAt(path);
    if (treePath.contains(this.path, path)) this.userJump(treePath.init(path));
    else this.jump(this.path);
    if (this.study) this.study.deleteNode(path);
  }

  promote(path: Tree.Path, toMainline: boolean): void {
    this.tree.promoteAt(path, toMainline);
    this.jump(path);
    if (this.study) this.study.promote(path, toMainline);
  }

  forceVariation(path: Tree.Path, force: boolean): void {
    this.tree.forceVariationAt(path, force);
    this.jump(path);
    if (this.study) this.study.forceVariation(path, force);
  }

  reset(): void {
    this.showGround();
    this.redraw();
  }

  encodeNodeFen(): Fen {
    return this.node.fen.replace(/\s/g, '_');
  }

  currentEvals() {
    return {
      server: this.node.eval,
      client: this.node.ceval,
    };
  }

  nextNodeBest() {
    return treeOps.withMainlineChild(this.node, (n: Tree.Node) => (n.eval ? n.eval.best : undefined));
  }

  setAutoShapes = (): void => {
    this.withCg(cg => cg.setAutoShapes(computeAutoShapes(this)));
  };

  private onNewCeval = (ev: Tree.ClientEval, path: Tree.Path, isThreat?: boolean): void => {
    this.tree.updateAt(path, (node: Tree.Node) => {
      if (node.fen !== ev.fen && !isThreat) return;
      if (isThreat) {
        if (!node.threat || isEvalBetter(ev, node.threat) || node.threat.maxDepth < ev.maxDepth) node.threat = ev;
      } else if (isEvalBetter(ev, node.ceval)) {
        node.ceval = ev;
      } else if (node.ceval && ev.maxDepth > node.ceval.maxDepth) node.ceval.maxDepth = ev.maxDepth;

      if (path === this.path) {
        this.setAutoShapes();
        if (!isThreat) {
          if (this.retro) this.retro.onCeval();
          if (this.practice) this.practice.onCeval();
          if (this.studyPractice) this.studyPractice.onCeval();
          this.evalCache.onCeval();
          if (ev.cloud && ev.depth >= this.ceval.effectiveMaxDepth()) this.ceval.stop();
        }
        this.redraw();
      }
    });
  };

  private instanciateCeval(): void {
    if (this.ceval) this.ceval.destroy();
    const cfg: CevalOpts = {
      variant: this.data.game.variant,
      standardMaterial:
        !this.data.game.initialFen ||
        parseFen(variantKeyToRules(this.data.game.variant.key))(this.data.game.initialFen).unwrap(
          setup =>
            PLAYERINDEXES.every(playerIndex => {
              const board = setup.board;
              const pieces = board[playerIndex];
              const promotedPieces =
                Math.max(board['q-piece'].intersect(pieces).size() - 1, 0) +
                Math.max(board['r-piece'].intersect(pieces).size() - 2, 0) +
                Math.max(board['n-piece'].intersect(pieces).size() - 2, 0) +
                Math.max(board['b-piece'].intersect(pieces).intersect(SquareSet.lightSquares64()).size() - 1, 0) +
                Math.max(board['b-piece'].intersect(pieces).intersect(SquareSet.darkSquares64()).size() - 1, 0);
              return board['p-piece'].intersect(pieces).size() + promotedPieces <= 8;
            }),
          _ => false,
        ),
      possible: !this.embed && (this.synthetic || !game.playable(this.data)),
      emit: (ev: Tree.ClientEval, work: CevalWork) => {
        this.onNewCeval(ev, work.path, work.threatMode);
      },
      setAutoShapes: this.setAutoShapes,
      redraw: this.redraw,
    };
    if (this.opts.study && this.opts.practice) {
      cfg.storageKeyPrefix = 'practice';
      cfg.multiPvDefault = 1;
    }
    this.ceval = cevalCtrl(cfg);
  }

  getCeval() {
    return this.ceval;
  }

  outcome(node?: Tree.Node): Outcome | undefined {
    return this.position(node || this.node).unwrap(
      pos => pos.outcome(),
      _ => undefined,
    );
  }

  position(node: Tree.Node): Result<Position, PositionError> {
    const setup = parseFen(variantKeyToRules(this.data.game.variant.key))(node.fen).unwrap();
    return variantClassFromKey(this.data.game.variant.key).fromSetup(setup);
  }

  canUseCeval(): boolean {
    return !this.node.threefold && !this.outcome();
  }

  startCeval = throttle(800, () => {
    if (this.ceval.enabled()) {
      if (this.canUseCeval()) {
        this.ceval.start(this.path, this.nodeList, this.threatMode(), false);
        this.evalCache.fetch(this.path, parseInt(this.ceval.multiPv()));
      } else this.ceval.stop();
    }
  });

  toggleCeval = () => {
    if (!this.showComputer()) return;
    this.ceval.toggle();
    this.setAutoShapes();
    this.startCeval();
    if (!this.ceval.enabled()) {
      this.threatMode(false);
      if (this.practice) this.togglePractice();
    }
    this.redraw();
  };

  toggleThreatMode = () => {
    if (this.node.check) return;
    if (!this.ceval.enabled()) this.ceval.toggle();
    if (!this.ceval.enabled()) return;
    this.threatMode(!this.threatMode());
    if (this.threatMode() && this.practice) this.togglePractice();
    this.setAutoShapes();
    this.startCeval();
    this.redraw();
  };

  disableThreatMode = (): boolean => {
    return !!this.practice;
  };

  mandatoryCeval = (): boolean => {
    return !!this.studyPractice;
  };

  private cevalReset(): void {
    this.ceval.stop();
    if (!this.ceval.enabled()) this.ceval.toggle();
    this.startCeval();
    this.redraw();
  }

  cevalSetMultiPv = (v: number): void => {
    this.ceval.multiPv(v);
    this.tree.removeCeval();
    this.cevalReset();
  };

  cevalSetThreads = (v: number): void => {
    if (!this.ceval.threads) return;
    this.ceval.threads(v);
    this.cevalReset();
  };

  cevalSetHashSize = (v: number): void => {
    if (!this.ceval.hashSize) return;
    this.ceval.hashSize(v);
    this.cevalReset();
  };

  cevalSetInfinite = (v: boolean): void => {
    this.ceval.infinite(v);
    this.cevalReset();
  };

  showEvalGauge(): boolean {
    return (
      this.serverEvalVariant() &&
      this.hasAnyComputerAnalysis() &&
      this.showGauge() &&
      !this.outcome() &&
      this.showComputer()
    );
  }

  serverEvalVariant(): boolean {
    return util.allowServerEvalForVariant(this.data.game.variant.key);
  }

  hasAnyComputerAnalysis(): boolean {
    return this.data.analysis ? true : this.ceval.enabled();
  }

  hasFullComputerAnalysis = (): boolean => {
    return Object.keys(this.mainline[0].eval || {}).length > 0;
  };

  private resetAutoShapes() {
    if (this.showAutoShapes() || this.showMoveAnnotation()) this.setAutoShapes();
    else this.chessground && this.chessground.setAutoShapes([]);
  }

  toggleAutoShapes = (v: boolean): void => {
    this.showAutoShapes(v);
    this.resetAutoShapes();
  };

  toggleGauge = () => {
    this.showGauge(!this.showGauge());
  };

  toggleMoveAnnotation = (v: boolean): void => {
    this.showMoveAnnotation(v);
    this.resetAutoShapes();
  };

  private onToggleComputer() {
    if (!this.showComputer()) {
      this.tree.removeComputerVariations();
      if (this.ceval.enabled()) this.toggleCeval();
      this.chessground && this.chessground.setAutoShapes([]);
    } else this.resetAutoShapes();
  }

  toggleComputer = () => {
    if (this.ceval.enabled()) this.toggleCeval();
    const value = !this.showComputer();
    this.showComputer(value);
    if (!value && this.practice) this.togglePractice();
    this.onToggleComputer();
    playstrategy.pubsub.emit('analysis.comp.toggle', value);
  };

  mergeAnalysisData(data: ServerEvalData): void {
    if (this.study && this.study.data.chapter.id !== data.ch) return;
    this.tree.merge(data.tree);
    if (!this.showComputer()) this.tree.removeComputerVariations();
    this.data.analysis = data.analysis;
    if (data.analysis) data.analysis.partial = !!treeOps.findInMainline(data.tree, n => !n.eval && !!n.children.length);
    if (data.division) this.data.game.division = data.division;
    if (this.retro) this.retro.onMergeAnalysisData();
    if (this.study) this.study.serverEval.onMergeAnalysisData();
    playstrategy.pubsub.emit('analysis.server.progress', this.data);
    this.redraw();
  }

  playUci(uci: Uci): void {
    const move = parseUci(variantKeyToRules(this.data.game.variant.key))(uci)!;
    const to = makeSquare(variantKeyToRules(this.data.game.variant.key))(move.to);
    if (isNormal(move)) {
      const piece = this.chessground.state.pieces.get(
        makeSquare(variantKeyToRules(this.data.game.variant.key))(move.from),
      );
      const capture = this.chessground.state.pieces.get(to);
      this.sendMove(
        makeSquare(variantKeyToRules(this.data.game.variant.key))(move.from),
        to,
        capture && piece && capture.playerIndex !== piece.playerIndex ? capture : undefined,
        move.promotion,
      );
    } else
      this.chessground.newPiece(
        {
          playerIndex: this.chessground.state.movable.playerIndex as PlayerIndex,
          role: move.role,
        },
        to,
      );
  }

  explorerMove(uci: Uci) {
    this.playUci(uci);
    this.explorer.loading(true);
  }

  playBestMove() {
    const uci = this.nextNodeBest() || (this.node.ceval && this.node.ceval.pvs[0].moves[0]);
    if (uci) this.playUci(uci);
  }

  canEvalGet(): boolean {
    if (this.node.ply >= 15 && !this.opts.study) return false;

    // cloud eval does not support threefold repetition
    const fens = new Set();
    for (let i = this.nodeList.length - 1; i >= 0; i--) {
      const node = this.nodeList[i];
      const fen = node.fen.split(' ').slice(0, 4).join(' ');
      if (fens.has(fen)) return false;
      if (node.san && sanIrreversible(this.data.game.variant.key, node.san)) return true;
      fens.add(fen);
    }
    return true;
  }

  instanciateEvalCache() {
    this.evalCache = makeEvalCache({
      variant: this.data.game.variant.key,
      canGet: () => this.canEvalGet(),
      canPut: () =>
        !!(
          this.data.evalPut &&
          this.canEvalGet() &&
          // if not in study, only put decent opening moves
          (this.opts.study || (!this.node.ceval!.mate && Math.abs(this.node.ceval!.cp!) < 99))
        ),
      getNode: () => this.node,
      send: this.opts.socketSend,
      receive: this.onNewCeval,
    });
  }

  toggleRetro = (): void => {
    if (this.retro) this.retro = undefined;
    else {
      this.retro = makeRetro(this, this.bottomPlayerIndex());
      if (this.practice) this.togglePractice();
      if (this.explorer.enabled()) this.toggleExplorer();
    }
    this.setAutoShapes();
  };

  toggleExplorer = (): void => {
    if (this.practice) this.togglePractice();
    if (this.explorer.enabled() || this.explorer.allowed()) this.explorer.toggle();
  };

  togglePractice = () => {
    if (this.practice || !this.ceval.possible) this.practice = undefined;
    else {
      if (this.retro) this.toggleRetro();
      if (this.explorer.enabled()) this.toggleExplorer();
      this.practice = makePractice(this, () => {
        // push to 20 to store AI moves in the cloud
        // lower to 18 after task completion (or failure)
        return this.studyPractice && this.studyPractice.success() === null ? 20 : 18;
      });
    }
    this.setAutoShapes();
  };

  restartPractice() {
    this.practice = undefined;
    this.togglePractice();
  }

  gamebookPlay = (): GamebookPlayCtrl | undefined => {
    return this.study && this.study.gamebookPlay();
  };

  isGamebook = (): boolean => !!(this.study && this.study.data.chapter.gamebook);

  withCg<A>(f: (cg: ChessgroundApi) => A): A | undefined {
    if (this.chessground && this.cgVersion.js === this.cgVersion.dom) return f(this.chessground);
    return undefined;
  }

  // FROM LISHOGI TO HELP TO BYOYOMI CLOCKS
  // Ideally we would just use node.clock
  // but we store remaining times for lishogi games as node.clock
  // for imports we store movetime as node.clock, because
  // that's what's provided next to each move
  getMovetime = (node: Tree.Node): number | undefined => {
    const offset = this.mainline[0].ply;
    if (defined(node.clock) && !this.study) {
      if (defined(this.data.game.plyCentis)) return this.data.game.plyCentis[node.ply - 1 - offset];
      //if (this.imported) return node.clock;
    }
    return;
  };
}
