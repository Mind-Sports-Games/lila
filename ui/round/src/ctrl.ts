/// <reference types="../types/ab" />

import * as ab from 'ab';
import * as round from './round';
import * as game from 'game';
import * as status from 'game/status';
import * as ground from './ground';
import notify from 'common/notification';
import { make as makeSocket, RoundSocket } from './socket';
import * as title from './title';
import * as promotion from './promotion';
import * as blur from './blur';
import * as speech from './speech';
import * as cg from 'chessground/types';
import { Config as CgConfig } from 'chessground/config';
import { Api as CgApi } from 'chessground/api';
import { setDropMode, cancelDropMode } from 'chessground/drop';
import { State } from 'chessground/state';
import { opposite } from 'chessground/util';
import * as Prefs from 'common/prefs';
import { ClockController, isByoyomi } from './clock/clockCtrl';
import { CorresClockController, ctrl as makeCorresClock } from './corresClock/corresClockCtrl';
import MoveOn from './moveOn';
import TransientMove from './transientMove';
import * as atomic from './atomic';
import * as sound from './sound';
import * as util from './util';
import * as xhr from './xhr';
import { valid as crazyValid, init as crazyInit, onEnd as crazyEndHook } from './crazy/crazyCtrl';
import { ctrl as makeKeyboardMove, KeyboardMove } from './keyboardMove';
import * as renderUser from './view/user';
import * as cevalSub from './cevalSub';
import * as keyboard from './keyboard';
import * as stratUtils from 'stratutils';

import {
  RoundOpts,
  RoundData,
  ApiAction,
  ApiEnd,
  Redraw,
  SocketMove,
  SocketDrop,
  SocketPass,
  SocketDoRoll,
  SocketLift,
  SocketEndTurn,
  SocketUndo,
  SocketCubeAction,
  SocketOpts,
  MoveMetadata,
  Position,
  NvuiPlugin,
} from './interfaces';
import { defined } from 'common';

interface GoneBerserk {
  p1?: boolean;
  p2?: boolean;
}

type Timeout = number;

export default class RoundController {
  data: RoundData;
  socket: RoundSocket;
  chessground: CgApi;
  clock?: ClockController;
  corresClock?: CorresClockController;
  trans: Trans;
  noarg: TransNoArg;
  keyboardMove?: KeyboardMove;
  moveOn: MoveOn;

  ply: number;
  turnCount: number;
  firstSeconds = true;
  flip = false;
  loading = false;
  loadingTimeout: number;
  redirecting = false;
  areDiceDescending = true;
  transientMove: TransientMove;
  moveToSubmit?: SocketMove;
  dropToSubmit?: SocketDrop;
  liftToSubmit?: SocketLift;
  goneBerserk: GoneBerserk = {};
  resignConfirm?: Timeout = undefined;
  drawConfirm?: Timeout = undefined;
  passConfirm?: Timeout = undefined;
  selectSquaresConfirm?: Timeout = undefined;
  // will be replaced by view layer
  autoScroll: () => void = () => {};
  challengeRematched = false;
  justDropped?: cg.Role;
  justCaptured?: cg.Piece;
  shouldSendMoveTime = false;
  preDrop?: cg.Role;
  lastDrawOfferAtPly?: Ply;
  nvui?: NvuiPlugin;
  sign: string = Math.random().toString(36);
  private music?: any;

  constructor(
    readonly opts: RoundOpts,
    readonly redraw: Redraw,
  ) {
    round.massage(opts.data);

    const d = (this.data = opts.data);

    this.ply = round.lastPly(d);
    this.turnCount = round.lastTurn(d);
    this.goneBerserk[d.player.playerIndex] = d.player.berserk;
    this.goneBerserk[d.opponent.playerIndex] = d.opponent.berserk;

    setTimeout(() => {
      this.firstSeconds = false;
      this.redraw();
    }, 3000);

    this.socket = makeSocket(opts.socketSend, this);

    if (playstrategy.RoundNVUI) this.nvui = playstrategy.RoundNVUI(redraw) as NvuiPlugin;

    if (d.clock)
      this.clock = new ClockController(d, {
        onFlag: this.socket.outoftime,
        redraw: this.redraw,
        soundPlayerIndex: d.simul || d.player.spectator || !d.pref.clockSound ? undefined : d.player.playerIndex,
        nvui: !!this.nvui,
      });
    else {
      this.makeCorrespondenceClock();
      setInterval(this.corresClockTick, 1000);
    }

    this.setQuietMode();

    this.moveOn = new MoveOn(this, 'move-on');
    this.transientMove = new TransientMove(this.socket);

    this.trans = playstrategy.trans(opts.i18n);
    this.noarg = this.trans.noarg;

    setTimeout(this.delayedInit, 200);

    setTimeout(this.showExpiration, 350);

    if (!document.referrer?.includes('/serviceWorker.')) setTimeout(this.showYourTurnNotification, 500);

    // at the end:
    playstrategy.pubsub.on('jump', ply => {
      this.jump(parseInt(ply));
      this.redraw();
    });

    playstrategy.pubsub.on('sound_set', set => {
      if (!this.music && set === 'music')
        playstrategy.loadScriptCJS('javascripts/music/play.js').then(() => {
          this.music = playstrategy.playMusic();
        });
      if (this.music && set !== 'music') this.music = undefined;
    });

    playstrategy.pubsub.on('zen', () => {
      if (this.isPlaying()) {
        const zen = !$('body').hasClass('zen');
        $('body').toggleClass('zen', zen);
        window.dispatchEvent(new Event('resize'));
        xhr.setZen(zen);
      }
    });

    if (this.isPlaying()) ab.init(this);
  }

  private showExpiration = () => {
    if (!this.data.expirationAtStart && !this.data.expirationOnPaused) return;
    if (this.data.expirationOnPaused) {
      const eop = this.data.expirationOnPaused;
      if (Math.max(0, eop.updatedAt - Date.now() + eop.millisToMove) == 0 && this.data.selectMode) {
        this.data.expirationOnPaused = undefined;
        if (this.data.deadStoneOfferState == 'ChooseFirstOffer') {
          this.doOfferSelectSquares();
        } else {
          this.socket.sendLoading('select-squares-accept');
        }
      }
    }
    this.redraw();
    setTimeout(this.showExpiration, 250);
  };

  private onUserMove = (orig: cg.Key, dest: cg.Key, meta: cg.MoveMetadata) => {
    if (!this.keyboardMove || !this.keyboardMove.usedSan) ab.move(this, meta);
    if (!promotion.start(this, orig, dest, meta)) {
      this.sendMove(orig, dest, undefined, this.data.game.variant.key, meta);
    }
  };

  private onUserLift = (dest: cg.Key) => {
    this.sendLift(this.data.game.variant.key, dest);
  };

  private onNewPiece = () => {
    if (!['backgammon', 'hyper', 'nackgammon'].includes(this.data.game.variant.key)) sound.move();
  };

  private onUserNewPiece = (role: cg.Role, key: cg.Key, meta: cg.MoveMetadata) => {
    if (!this.replaying() && crazyValid(this.data, role, key)) {
      this.sendNewPiece(role, key, this.data.game.variant.key, !!meta.predrop);
      if (['backgammon', 'hyper', 'nackgammon'].includes(this.data.game.variant.key)) {
        sound.move();
        cancelDropMode(this.chessground.state);
        this.redraw();
      }
    } else this.jump(this.ply);
    if (!this.data.onlyDropsVariant) {
      cancelDropMode(this.chessground.state);
      this.redraw();
    }
  };

  private onMove = (orig: cg.Key, dest: cg.Key, captured?: cg.Piece) => {
    if (captured || this.enpassant(orig, dest)) {
      if (this.data.game.variant.key === 'atomic') {
        sound.explode();
        atomic.capture(this, dest);
      } else sound.capture();
    } else sound.move();
    if (['togyzkumalak', 'bestemshe', 'backgammon', 'hyper', 'nackgammon'].includes(this.data.game.variant.key)) {
      this.chessground.redrawAll(); //redraw board scores or coordinates
    }
    if (!this.data.onlyDropsVariant) cancelDropMode(this.chessground.state);
  };

  private onPremove = (orig: cg.Key, dest: cg.Key, meta: cg.MoveMetadata) => {
    promotion.start(this, orig, dest, meta);
  };

  private onCancelPremove = () => {
    promotion.cancelPrePromotion(this);
  };

  private onPredrop = (role: cg.Role | undefined, _?: Key) => {
    this.preDrop = role;
    this.redraw();
  };

  private onCancelDropMode = () => {
    //redraw pocket - due to possible selection in CG and dropmode cancelled
    if (['crazyhouse', 'shogi', 'minishogi'].includes(this.data.game.variant.key)) {
      this.redraw();
    }
  };

  private onSelect = (_: cg.Key) => {
    this.data.currentSelectedSquares = Array.from(this.chessground.state.selectedPieces.keys());
    this.data.calculatedCGGoScores = this.chessground.state.simpleGoScores;
    this.redraw();
  };

  private onSelectDice = (dice: cg.Dice[]) => {
    if (this.data.dice === undefined || this.data.dice[0].value !== dice[0].value) {
      this.areDiceDescending = !this.areDiceDescending;
    }
    this.data.dice = dice;
    this.data.activeDiceValue = this.activeDiceValue(dice);
    if (this.data.activeDiceValue === undefined && this.isPlaying()) {
      this.endTurnAction();
    }
    ground.reload(this); //update possible actions from new 'active' (be more restrictive?)
    this.chessground.redrawAll(); //redraw dice
  };

  private onCGButtonClick = (button: cg.Button) => {
    switch (button) {
      case 'undo':
        this.undoAction();
        break;
      case 'roll':
        this.forceRollDice(this.data.game.variant.key);
        break;
      case 'double':
        this.cubeAction('cubeo');
        break;
      case 'take':
        this.cubeAction('cubey');
        break;
      case 'drop':
        this.cubeAction('cuben');
        break;
    }
  };

  private isSimulHost = () => {
    return this.data.simul && this.data.simul.hostId === this.opts.userId;
  };

  private enpassant = (orig: cg.Key, dest: cg.Key): boolean => {
    if (
      [
        'xiangqi',
        'shogi',
        'minixiangqi',
        'minishogi',
        'flipello',
        'flipello10',
        'oware',
        'togyzkumalak',
        'bestemshe',
        'amazons',
        'breakthroughtroyka',
        'minibreakthroughtroyka',
        'go9x9',
        'go13x13',
        'go19x19',
        'backgammon',
        'hyper',
        'nackgammon',
        'abalone',
      ].includes(this.data.game.variant.key)
    )
      return false;
    if (orig[0] === dest[0] || this.chessground.state.pieces.get(dest)?.role !== 'p-piece') return false;
    const pos = (dest[0] + orig[1]) as cg.Key;
    this.chessground.setPieces(new Map([[pos, undefined]]));
    return true;
  };

  private setDropOnlyVariantDropMode = (
    activePlayerIndex: boolean,
    currentPlayerIndex: 'p1' | 'p2',
    s: State,
  ): void => {
    if (activePlayerIndex) {
      return setDropMode(s, stratUtils.onlyDropsVariantPiece(s.variant as VariantKey, currentPlayerIndex));
    } else {
      return cancelDropMode(s);
    }
  };

  lastPly = () => round.lastPly(this.data);
  lastTurn = () => round.lastTurn(this.data);

  makeCgHooks = () => ({
    onUserMove: this.onUserMove,
    onUserNewPiece: this.onUserNewPiece,
    onUserLift: this.onUserLift,
    onMove: this.onMove,
    onNewPiece: this.onNewPiece,
    onPremove: this.onPremove,
    onCancelPremove: this.onCancelPremove,
    onPredrop: this.onPredrop,
    onCancelDropMode: this.onCancelDropMode,
    onSelect: this.onSelect,
    onSelectDice: this.onSelectDice,
    onButtonClick: this.onCGButtonClick,
  });

  replaying = (): boolean => this.ply !== this.lastPly();

  userJump = (ply: Ply): void => {
    this.cancelMove();
    this.chessground.selectSquare(null);
    if (ply != this.ply && this.jump(ply)) speech.userJump(this, this.ply);
    else this.redraw();
  };

  userJumpToTurn = (turn: number): void => {
    this.cancelMove();
    this.chessground.selectSquare(null);
    const ply = this.StepAtTurn(turn).ply;
    if (ply != this.ply && this.jump(ply)) speech.userJump(this, this.ply);
    else this.redraw();
  };

  isPlaying = () => game.isPlayerPlaying(this.data);

  jump = (ply: Ply): boolean => {
    ply = Math.max(round.firstPly(this.data), Math.min(this.lastPly(), ply));
    const isForwardStep = ply === this.ply + 1;
    this.ply = ply;
    this.justDropped = undefined;
    this.preDrop = undefined;
    const s = this.stepAt(ply);
    this.turnCount = s.turnCount;
    const turnPlayerIndex = util.turnPlayerIndexFromLastTurn(this.turnCount);
    const dice = stratUtils.readDice(
      s.fen,
      this.data.game.variant.key,
      this.replaying() ? false : this.data.canEndTurn,
    );
    const doublingCube = stratUtils.readDoublingCube(s.fen, this.data.game.variant.key);
    const config: CgConfig = {
      fen: s.fen,
      lastMove: util.lastMove(this.data.onlyDropsVariant, s.uci),
      check: !!s.check,
      turnPlayerIndex: turnPlayerIndex,
      dice: dice,
      doublingCube: doublingCube,
      showUndoButton: false,
      cubeActions: [], //we dont know what these are so dont want to display them
    };
    if (this.replaying()) {
      cancelDropMode(this.chessground.state);
      this.chessground.stop();
    } else {
      config.movable = {
        playerIndex: this.isPlaying() ? this.data.player.playerIndex : undefined,
        dests: util.parsePossibleMoves(this.data.possibleMoves, this.activeDiceValue(dice)),
      };
      config.liftable = {
        liftDests: util.parsePossibleLifts(this.data.possibleLifts),
      };
      config.showUndoButton = this.isPlaying() && this.data.player.playerIndex == turnPlayerIndex && dice.length > 0;
      config.canUndo = this.data.canUndo;
      config.cubeActions = this.data.cubeActions ? this.data.cubeActions.split(',').map(a => a as cg.CubeAction) : [];
      config.gameButtonsActive = true;
    }
    config.dropmode = {
      dropDests: this.isPlaying() ? stratUtils.readDropsByRole(this.data.possibleDropsByRole) : new Map(),
    };
    this.chessground.set(config);

    if (['togyzkumalak', 'bestemshe', 'backgammon', 'hyper', 'nackgammon'].includes(this.data.game.variant.key)) {
      this.chessground.redrawAll(); //redraw board scores or coordinates
    }
    const variantActionToEnforceDrop =
      ['amazons', 'backgammon', 'hyper', 'nackgammon'].includes(this.data.game.variant.key) &&
      this.data.possibleDropsByRole &&
      this.data.possibleDropsByRole.length > 0;
    if (this.data.onlyDropsVariant) {
      if (
        ply == this.lastPly() &&
        (!['amazons', 'backgammon', 'hyper', 'nackgammon'].includes(this.data.game.variant.key) ||
          variantActionToEnforceDrop)
      ) {
        this.setDropOnlyVariantDropMode(
          this.data.player.playerIndex === this.data.game.player,
          this.data.player.playerIndex,
          this.chessground.state,
        );
      } else {
        cancelDropMode(this.chessground.state);
      }
    }
    if (isForwardStep) {
      if (['backgammon', 'hyper', 'nackgammon'].includes(this.data.game.variant.key)) {
        //Too noisy if playing dice roll sounds during scrolling, therefore just use move sound
        sound.move();
      } else if (s.san) {
        if (s.san.includes('x')) sound.capture();
        else sound.move();
        if (/[+#]/.test(s.san)) sound.check();
      }
    }
    this.autoScroll();
    if (this.keyboardMove) this.keyboardMove.update(s);
    playstrategy.pubsub.emit('ply', ply);

    this.doForcedActions();
    return true;
  };

  replayEnabledByPref = (): boolean => {
    const d = this.data;
    return (
      d.pref.replay === Prefs.Replay.Always ||
      (d.pref.replay === Prefs.Replay.OnlySlowGames &&
        (d.game.speed === 'classical' || d.game.speed === 'unlimited' || d.game.speed === 'correspondence'))
    );
  };

  activeDiceValue = (dice: cg.Dice[]): number | undefined => {
    return dice && dice.filter(d => d.isAvailable).length > 0 ? dice.filter(d => d.isAvailable)[0].value : undefined;
  };

  isLate = () => this.replaying() && status.playing(this.data);

  playerAt = (position: Position) =>
    (this.flip as any) ^ ((position === 'top') as any) ? this.data.opponent : this.data.player;

  flipNow = () => {
    this.flip = !this.nvui && !this.flip;
    this.chessground.set({
      orientation: ground.boardOrientation(this.data, this.flip),
    });
    this.redraw();
  };

  setTitle = () => title.set(this);

  actualSendMove = (tpe: string, data: any, meta: MoveMetadata = {}) => {
    const socketOpts: SocketOpts = {
      sign: this.sign,
      ackable: true,
    };
    if (this.clock) {
      socketOpts.withLag = !this.shouldSendMoveTime || !this.clock.isRunning();
      if (meta.premove && this.shouldSendMoveTime) {
        this.clock.hardStopClock();
        socketOpts.millis = 0;
      } else {
        const moveMillis = this.clock.stopClock();
        if (moveMillis !== undefined && this.shouldSendMoveTime) {
          // @ts-expect-error TS2322: Type 'number | void' is not assignable to type 'number'.
          socketOpts.millis = moveMillis;
        }
      }
    }
    this.socket.send(tpe, data, socketOpts);

    this.justDropped = meta.justDropped;
    this.justCaptured = meta.justCaptured;
    this.preDrop = undefined;
    this.redraw();
  };

  sendMove = (orig: cg.Key, dest: cg.Key, prom: cg.Role | undefined, variant: string, meta: cg.MoveMetadata) => {
    const move: SocketMove = {
      u: orig + dest,
      variant: variant,
    };
    if (prom) move.u += prom.split('-')[0].slice(-1);
    if (blur.get()) move.b = 1;
    this.resign(false);
    if (this.data.pref.submitMove && !meta.premove) {
      this.moveToSubmit = move;
      this.redraw();
    } else {
      this.actualSendMove('move', move, {
        justCaptured: meta.captured,
        premove: meta.premove,
      });
    }
  };

  sendNewPiece = (role: cg.Role, key: cg.Key, variant: string, isPredrop: boolean): void => {
    const drop: SocketDrop = {
      role: role,
      pos: key,
      variant: variant,
    };
    if (blur.get()) drop.b = 1;
    this.resign(false);
    if (this.data.pref.submitMove && !isPredrop) {
      this.dropToSubmit = drop;
      this.redraw();
    } else {
      this.actualSendMove('drop', drop, {
        justDropped: role,
        premove: isPredrop,
      });
    }
  };

  sendPass = (variant: string): void => {
    const pass: SocketPass = {
      variant: variant,
    };
    if (blur.get()) pass.b = 1;
    this.resign(false);
    this.actualSendMove('pass', pass);
  };

  lastTurnStr = () => {
    const d = this.data;
    if (d.steps.length > 0) {
      const lastTurnCount = d.steps[d.steps.length - 1].turnCount;
      return d.steps
        .filter(step => step.turnCount >= lastTurnCount - 1)
        .slice(1)
        .filter(step => step.san !== 'endturn')
        .map(step => step.san)
        .join(',');
    } else {
      return '';
    }
  };

  showYourTurnNotification = () => {
    const d = this.data;
    if (game.isPlayerTurn(d))
      notify(() => {
        let txt = this.noarg('yourTurn');
        const opponent = renderUser.userTxt(this, d.opponent);
        if (this.turnCount < 1) txt = `${opponent}\njoined the game.\n${txt}`;
        else {
          const turn = Math.floor((this.turnCount - 1) / 2) + 1;
          const actions = `${turn}${this.turnCount % 2 === 1 ? '.' : '...'} ${this.lastTurnStr()}`;
          let notificationPrefix = `${opponent}\nplayed`;
          if (d.steps[d.steps.length - 1].san.includes('/')) notificationPrefix = 'You rolled';
          txt = `${notificationPrefix} ${actions}.\n${txt}`;
        }
        return txt;
      });
    else if (this.isPlaying() && this.turnCount < 1)
      notify(() => renderUser.userTxt(this, d.opponent) + '\njoined the game.');
  };

  playerByPlayerIndex = (c: PlayerIndex) => this.data[c === this.data.player.playerIndex ? 'player' : 'opponent'];

  apiAction = (o: ApiAction): true => {
    const d = this.data,
      playing = this.isPlaying(),
      hasJustSwitchedTurns = d.game.turns != o.turnCount;
    d.game.turns = o.turnCount;
    d.game.player = util.turnPlayerIndexFromLastTurn(o.turnCount);
    if (['amazons', 'backgammon', 'hyper', 'nackgammon'].includes(d.game.variant.key)) {
      if (d.onlyDropsVariant && !o.drops) {
        cancelDropMode(this.chessground.state);
        this.redraw();
      }
      d.onlyDropsVariant = o.drops ? true : false;
    }
    d.multiActionMetaData = o.multiActionMetaData;

    const playedPlayerIndex = hasJustSwitchedTurns ? opposite(d.game.player) : d.game.player,
      activePlayerIndex = d.player.playerIndex === d.game.player;
    if (o.status) d.game.status = o.status;
    if (o.winner) d.game.winner = o.winner;
    this.playerByPlayerIndex('p1').offeringDraw = o.wDraw;
    this.playerByPlayerIndex('p2').offeringDraw = o.bDraw;
    d.possibleMoves = activePlayerIndex ? o.dests : undefined;
    d.possibleDrops = activePlayerIndex ? o.drops : undefined;
    d.possibleDropsByRole = activePlayerIndex ? o.dropsByRole : undefined;
    d.possibleLifts = activePlayerIndex ? o.lifts : undefined;

    //set the right data from all backgammon actions
    this.areDiceDescending = activePlayerIndex ? this.areDiceDescending : true;
    d.canOnlyRollDice = activePlayerIndex ? o.canOnlyRollDice : false;
    d.dice = stratUtils.readDice(o.fen, this.data.game.variant.key, o.canEndTurn, this.areDiceDescending);
    d.doublingCube = stratUtils.readDoublingCube(o.fen, this.data.game.variant.key);
    d.activeDiceValue = this.activeDiceValue(d.dice);
    (d.cubeActions = o.cubeActions), (d.forcedAction = o.forcedAction);

    d.crazyhouse = o.crazyhouse;
    d.takebackable = d.canTakeBack ? o.takebackable : false;
    d.canUndo = activePlayerIndex ? o.canUndo : false;
    d.canEndTurn = activePlayerIndex ? o.canEndTurn : false;
    //from pass move (or drop)
    if (['go9x9', 'go13x13', 'go19x19'].includes(d.game.variant.key)) {
      d.selectMode = o.canSelectSquares ? activePlayerIndex : false;
      d.deadStoneOfferState = o.canSelectSquares ? 'ChooseFirstOffer' : undefined;
      if (d.clock) {
        d.expirationOnPaused = o.canSelectSquares
          ? {
              idleMillis: 0,
              updatedAt: Date.now(),
              millisToMove: d.pauseSecs ? d.pauseSecs : 60000,
            }
          : undefined;
        setTimeout(this.showExpiration, 350);
      }
    }
    this.setTitle();
    if (!this.replaying()) {
      if (o.uci === 'undo') {
        this.ply--;
      } else {
        this.ply++;
      }
      this.turnCount = o.turnCount;
      const variantCanStillHavePieceAtActionKey = ['togyzkumalak', 'bestemshe', 'backgammon', 'hyper', 'nackgammon'];
      const allowChessgroundAction =
        !(
          variantCanStillHavePieceAtActionKey.includes(d.game.variant.key) && playedPlayerIndex === d.player.playerIndex
        ) || !this.isPlaying();
      //apiAction triggers for both players and the move/drop/lift has already happened for the active player.
      if (o.role && allowChessgroundAction) {
        this.chessground.newPiece(
          {
            role: o.role,
            playerIndex: playedPlayerIndex,
          },
          util.uci2move(o.uci)![1] as cg.Key,
        );
      } else if (o.uci.includes('^') && allowChessgroundAction) {
        this.chessground.liftNoAnim(o.uci.slice(1) as cg.Key);
      } else {
        // This block needs to be idempotent, even for castling moves in
        // Chess960.
        const keys = util.uci2move(o.uci);
        if (keys !== undefined) {
          // ignore a pass action
          const pieces = this.chessground.state.pieces;
          if (
            (!o.castle ||
              (pieces.get(o.castle.king[0])?.role === 'k-piece' && pieces.get(o.castle.rook[0])?.role === 'r-piece')) &&
            allowChessgroundAction
          ) {
            if (
              ['oware', 'togyzkumalak', 'bestemshe', 'backgammon', 'hyper', 'nackgammon'].includes(d.game.variant.key)
            ) {
              this.chessground.moveNoAnim(keys[0], keys[1]);
            } else {
              this.chessground.move(keys[0], keys[1]);
            }
          }
        }
      }

      if (['backgammon', 'hyper', 'nackgammon'].includes(d.game.variant.key)) {
        this.chessground.set({
          dice: this.data.dice ? this.data.dice : [],
          doublingCube: this.data.doublingCube,
          cubeActions: this.data.cubeActions ? this.data.cubeActions.split(',').map(a => a as cg.CubeAction) : [],
          fen: o.fen,
          canUndo: this.data.canUndo,
          showUndoButton:
            this.isPlaying() &&
            this.data.player.playerIndex === this.data.game.player &&
            this.data.dice &&
            this.data.dice.length > 0,
          viewOnly:
            this.isPlaying() &&
            this.data.pref.playForcedAction &&
            this.data.forcedAction !== undefined &&
            this.data.player.playerIndex === this.data.game.player,
        });
      }
      if (d.onlyDropsVariant) {
        this.setDropOnlyVariantDropMode(activePlayerIndex, d.player.playerIndex, this.chessground.state);
      }
      if (o.promotion) ground.promote(this.chessground, o.promotion.key, o.promotion.pieceClass);
      this.chessground.set({
        turnPlayerIndex: d.game.player,
        movable: {
          dests: playing ? util.parsePossibleMoves(d.possibleMoves, d.activeDiceValue) : new Map(),
        },
        liftable: {
          liftDests: playing ? util.parsePossibleLifts(d.possibleLifts) : [],
        },
        dropmode: {
          dropDests: playing ? stratUtils.readDropsByRole(d.possibleDropsByRole) : new Map(),
        },
        check: !!o.check,
        onlyDropsVariant: d.onlyDropsVariant, //need to update every move (amazons, backgammon)
        selectOnly: d.selectMode,
        highlight: {
          lastMove:
            d.pref.highlight && !d.selectMode && !['backgammon', 'hyper', 'nackgammon'].includes(d.game.variant.key),
        },
      });
      if (o.check) sound.check();
      if (o.uci === 'pass') sound.move();
      blur.onMove();
      playstrategy.pubsub.emit('ply', this.ply);

      if (['backgammon', 'hyper', 'nackgammon'].includes(d.game.variant.key)) {
        this.chessground.redrawAll(); //dice, extra button updates etc.
      }
    }
    d.game.threefold = !!o.threefold;
    d.game.perpetualWarning = !!o.perpetualWarning;

    //backgammon need to update initial turnCount if we switch starting player on initial dice roll
    if (
      ['backgammon', 'hyper', 'nackgammon'].includes(d.game.variant.key) &&
      this.lastPly() === 0 &&
      o.fen.includes('b')
    ) {
      d.steps[0].turnCount = 1;
      d.steps[0].ply = 1;
      d.game.startedAtTurn = 1;
    }

    const step = {
      ply: this.lastPly() + 1,
      turnCount: o.turnCount,
      fen: o.fen,
      san: o.san,
      uci: o.uci,
      check: o.check,
      crazy: o.crazyhouse,
    };
    if (step.uci === 'undo') {
      d.steps.pop();
    } else {
      d.steps.push(step);
    }
    this.justDropped = undefined;
    this.justCaptured = undefined;
    game.setOnGame(d, playedPlayerIndex, true);
    d.forecastCount = undefined;
    if (o.clock) {
      this.shouldSendMoveTime = true;
      const oc = o.clock,
        delay = playing && activePlayerIndex ? 0 : oc.lag || 1;
      if (this.clock && this.clock.byoyomiData) {
        this.clock.setClock(d, oc.p1, oc.p2, oc.p1Pending, oc.p2Pending, oc.p1Periods, oc.p2Periods, delay);
      } else if (this.clock)
        this.clock.setClock(d, oc.p1, oc.p2, oc.p1Pending, oc.p2Pending, undefined, undefined, delay);
      else if (this.corresClock) this.corresClock.update(oc.p1, oc.p2);
    }
    if (d.expirationAtStart) {
      if (round.turnsTaken(d) > 1 && !d.pref.playerTurnIndicator) {
        d.expirationAtStart = undefined;
      } else if (hasJustSwitchedTurns) d.expirationAtStart.updatedAt = Date.now();
    }
    this.redraw();
    if (playing && playedPlayerIndex == d.player.playerIndex) {
      this.transientMove.clear();
      this.moveOn.next();
      cevalSub.publish(d, o);
    }
    if (!this.replaying() && playedPlayerIndex != d.player.playerIndex) {
      // atrocious hack to prevent race condition
      // with explosions and premoves
      // https://github.com/ornicar/lila/issues/343
      const premoveDelay = d.game.variant.key === 'atomic' ? 100 : 1;
      setTimeout(() => {
        if (!this.chessground.playPremove() && !this.playPredrop()) {
          promotion.cancel(this);
          this.showYourTurnNotification();
        }
      }, premoveDelay);
    }
    this.autoScroll();
    this.onChange();
    if (this.keyboardMove) this.keyboardMove.update(step, playedPlayerIndex != d.player.playerIndex);
    if (this.music) this.music.jump(o);
    speech.step(step);

    //as we need to update step data at start of game also jump to latest action
    if (['backgammon', 'hyper', 'nackgammon'].includes(d.game.variant.key) && o.ply <= 2 && !this.replaying()) {
      this.jump(o.ply);
    }

    this.doForcedActions();

    return true; // prevents default socket pubsub
  };

  private forcePass(possibleMoves: cg.Dests, variantKey: VariantKey) {
    if (possibleMoves.size == 1 && possibleMoves.keys().next().value) {
      const passOrig = possibleMoves.keys().next().value;
      if (passOrig != undefined) {
        const passDests = possibleMoves.get(passOrig);
        if (passDests && passDests.length == 1) {
          const passDest = passDests[0];
          this.sendMove(passOrig, passDest, undefined, variantKey, { premove: false });
        }
      }
    }
  }

  private forceRollDice(variantKey: VariantKey) {
    this.sendRollDice(variantKey);
  }

  sendRollDice = (variant: VariantKey): void => {
    sound.diceroll();
    const roll: SocketDoRoll = {
      variant: variant,
    };
    if (blur.get()) roll.b = 1;
    this.resign(false);
    this.actualSendMove('diceroll', roll);
  };

  sendLift = (variant: VariantKey, key: cg.Key): void => {
    sound.move();
    const lift: SocketLift = {
      pos: key,
      variant: variant,
    };
    if (blur.get()) lift.b = 1;
    this.resign(false);
    this.actualSendMove('lift', lift);
  };

  sendUndo = (variant: VariantKey): void => {
    const undo: SocketUndo = {
      variant: variant,
    };
    if (blur.get()) undo.b = 1;
    this.resign(false);
    this.actualSendMove('undo', undo);
  };

  sendCubeAction = (variant: VariantKey, interaction: string): void => {
    const cubeAction: SocketCubeAction = {
      interaction: interaction,
      variant: variant,
    };
    if (blur.get()) cubeAction.b = 1;
    this.resign(false);
    this.actualSendMove('cubeaction', cubeAction);
  };

  sendEndTurn = (variant: VariantKey): void => {
    if (['backgammon', 'hyper', 'nackgammon'].includes(variant)) sound.dicepickup();
    const endTurn: SocketEndTurn = {
      variant: variant,
    };
    if (blur.get()) endTurn.b = 1;
    this.resign(false);
    this.actualSendMove('endturn', endTurn);
  };

  private playPredrop = () => {
    return this.chessground.playPredrop(drop => {
      return crazyValid(this.data, drop.role, drop.key);
    });
  };

  private clearJust() {
    this.justDropped = undefined;
    this.justCaptured = undefined;
    this.preDrop = undefined;
  }

  reload = (d: RoundData): void => {
    if (d.steps.length !== this.data.steps.length) {
      this.ply = d.steps[d.steps.length - 1].ply;
      this.turnCount = d.steps[d.steps.length - 1].turnCount;
    }
    round.massage(d);
    this.data = d;
    this.clearJust();
    this.shouldSendMoveTime = false;
    const clock = d.clock;
    if (this.clock && clock && isByoyomi(clock)) {
      this.clock.setClock(d, clock.p1, clock.p2, clock.p1Pending, clock.p2Pending, clock.p1Periods, clock.p2Periods);
    } else if (this.clock) this.clock.setClock(d, d.clock!.p1, d.clock!.p2, d.clock!.p1Pending, d.clock!.p2Pending);
    if (this.corresClock) this.corresClock.update(d.correspondence.p1, d.correspondence.p2);
    if (!this.replaying()) ground.reload(this);
    this.setTitle();
    this.moveOn.next();
    this.setQuietMode();
    this.redraw();
    this.autoScroll();
    this.onChange();
    this.setLoading(false);
    if (this.keyboardMove) this.keyboardMove.update(d.steps[d.steps.length - 1]);
    this.bindSpaceToEndTurn();
    //redraw board scores/dice, items in CG wrap layer
    if (['togyzkumalak', 'bestemshe', 'backgammon', 'hyper', 'nackgammon'].includes(this.data.game.variant.key))
      this.chessground.redrawAll();
  };

  endWithData = (o: ApiEnd): void => {
    const d = this.data;
    d.game.winner = o.winner;
    d.game.winnerPlayer = o.winnerPlayer;
    d.game.loserPlayer = o.loserPlayer;
    d.game.status = o.status;
    d.game.boosted = o.boosted;
    this.userJump(this.lastPly());
    this.chessground.stop();
    if (o.ratingDiff) {
      d.player.ratingDiff = o.ratingDiff[d.player.playerIndex];
      d.opponent.ratingDiff = o.ratingDiff[d.opponent.playerIndex];
    }
    if (!d.player.spectator && d.game.turns > 1) {
      const key = o.winner ? (d.player.playerIndex === o.winner ? 'victory' : 'defeat') : 'draw';
      playstrategy.sound.play(key);
      if (
        key != 'victory' &&
        d.game.turns > 6 &&
        !d.tournament &&
        !d.swiss &&
        playstrategy.storage.get('courtesy') == '1'
      )
        this.opts.chat?.instance?.then(c => c.post('Good game, well played'));
    }
    if (d.crazyhouse) crazyEndHook();
    this.clearJust();
    this.setTitle();
    this.moveOn.next();
    this.setQuietMode();
    this.setLoading(false);
    if (this.clock && o.clock && this.clock.byoyomiData) {
      this.clock.setClock(
        d,
        o.clock.p1 * 0.01,
        o.clock.p2 * 0.01,
        o.clock.p1Pending * 0.01,
        o.clock.p2Pending * 0.01,
        o.clock.p1Periods,
        o.clock.p2Periods,
      );
    }
    if (this.clock && o.clock)
      this.clock.setClock(d, o.clock.p1 * 0.01, o.clock.p2 * 0.01, o.clock.p1Pending * 0.01, o.clock.p2Pending * 0.01);
    this.redraw();
    this.autoScroll();
    this.onChange();
    if (d.tv) setTimeout(playstrategy.reload, 10000);
    speech.status(this);
  };

  challengeRematch = (): void => {
    this.challengeRematched = true;
    xhr.challengeRematch(this.data.game.id).then(
      () => {
        playstrategy.pubsub.emit('challenge-app.open');
        if (playstrategy.once('rematch-challenge'))
          setTimeout(() => {
            playstrategy.hopscotch(function () {
              window.hopscotch
                .configure({
                  i18n: { doneBtn: 'OK, got it' },
                })
                .startTour({
                  id: 'rematch-challenge',
                  showPrevButton: true,
                  steps: [
                    {
                      title: 'Challenged to a rematch',
                      content: 'Your opponent is offline, but they can accept this challenge later!',
                      target: '#challenge-app',
                      placement: 'bottom',
                    },
                  ],
                });
            });
          }, 1000);
      },
      _ => {
        this.challengeRematched = false;
      },
    );
  };

  private makeCorrespondenceClock = (): void => {
    if (this.data.correspondence && !this.corresClock)
      this.corresClock = makeCorresClock(this, this.data.correspondence, this.socket.outoftime);
  };

  private corresClockTick = (): void => {
    if (this.corresClock && game.playable(this.data)) this.corresClock.tick(this.data.game.player);
  };

  private setQuietMode = () => {
    const was = playstrategy.quietMode;
    const is = this.isPlaying();
    if (was !== is) {
      playstrategy.quietMode = is;
      $('body')
        .toggleClass('playing', is)
        .toggleClass('no-select', is && this.clock && this.clock.millisOf(this.data.player.playerIndex) <= 3e5);
    }
  };

  takebackYes = () => {
    this.socket.sendLoading('takeback-yes');
    this.chessground.cancelPremove();
    promotion.cancel(this);
  };

  resign = (v: boolean, immediately?: boolean): void => {
    if (v) {
      if (this.resignConfirm || !this.data.pref.confirmResign || immediately) {
        this.socket.sendLoading('resign');
        clearTimeout(this.resignConfirm);
      } else {
        this.resignConfirm = setTimeout(() => this.resign(false), 3000);
      }
      this.redraw();
    } else if (this.resignConfirm) {
      clearTimeout(this.resignConfirm);
      this.resignConfirm = undefined;
      this.redraw();
    }
  };

  goBerserk = () => {
    this.socket.berserk();
    playstrategy.sound.play('berserk');
  };

  setBerserk = (playerIndex: PlayerIndex): void => {
    if (this.clock) this.clock.setBerserk(playerIndex);
    if (this.goneBerserk[playerIndex]) return;
    this.goneBerserk[playerIndex] = true;
    if (playerIndex !== this.data.player.playerIndex) playstrategy.sound.play('berserk');
    this.redraw();
    $('<i data-icon="`">').appendTo($(`.game__meta .player.${playerIndex} .user-link`));
  };

  setLoading = (v: boolean, duration = 1500) => {
    clearTimeout(this.loadingTimeout);
    if (v) {
      this.loading = true;
      this.loadingTimeout = setTimeout(() => {
        this.loading = false;
        this.redraw();
      }, duration);
      this.redraw();
    } else if (this.loading) {
      this.loading = false;
      this.redraw();
    }
  };

  setRedirecting = () => {
    this.redirecting = true;
    playstrategy.unload.expected = true;
    setTimeout(() => {
      this.redirecting = false;
      this.redraw();
    }, 2500);
    this.redraw();
  };

  submitMove = (v: boolean): void => {
    const toSubmit = this.moveToSubmit || this.dropToSubmit || this.liftToSubmit;
    if (v && toSubmit) {
      if (this.moveToSubmit) this.actualSendMove('move', this.moveToSubmit);
      else if (this.dropToSubmit) this.actualSendMove('drop', this.dropToSubmit);
      else this.actualSendMove('lift', this.liftToSubmit);
      playstrategy.sound.play('confirmation');
    } else this.jump(this.ply);
    this.cancelMove();
    if (toSubmit) this.setLoading(true, 300);
  };

  cancelMove = (): void => {
    this.moveToSubmit = undefined;
    this.dropToSubmit = undefined;
    this.liftToSubmit = undefined;
  };

  private onChange = () => {
    if (this.opts.onChange) setTimeout(() => this.opts.onChange(this.data), 150);
  };

  private goneTick?: number;
  setGone = (gone: number | boolean) => {
    game.setGone(this.data, this.data.opponent.playerIndex, gone);
    clearTimeout(this.goneTick);
    if (Number(gone) > 1)
      this.goneTick = setTimeout(() => {
        const g = Number(this.opponentGone());
        if (g > 1) this.setGone(g - 1);
      }, 1000);
    this.redraw();
  };

  opponentGone = (): number | boolean => {
    const d = this.data;
    return (
      defined(d.opponent.isGone) &&
      d.opponent.isGone !== false &&
      !game.isPlayerTurn(d) &&
      game.resignable(d) &&
      d.opponent.isGone
    );
  };

  canOfferDraw = (): boolean => game.drawable(this.data) && (this.lastDrawOfferAtPly || -99) < this.ply - 20;

  offerDraw = (v: boolean): void => {
    if (this.canOfferDraw()) {
      if (this.drawConfirm) {
        if (v) this.doOfferDraw();
        clearTimeout(this.drawConfirm);
        this.drawConfirm = undefined;
      } else if (v) {
        if (this.data.pref.confirmResign)
          this.drawConfirm = setTimeout(() => {
            this.offerDraw(false);
          }, 3000);
        else this.doOfferDraw();
      }
    }
    this.redraw();
  };

  private doOfferDraw = () => {
    this.lastDrawOfferAtPly = this.ply;
    this.socket.sendLoading('draw-yes', null);
  };

  canOfferSelectSquares = (): boolean => {
    return (
      ['go9x9', 'go13x13', 'go19x19'].includes(this.data.game.variant.key) &&
      this.isPlaying() &&
      !this.replaying() &&
      (this.data.opponent.offeringSelectSquares || this.data.selectMode) &&
      !this.data.player.offeringSelectSquares &&
      !(this.data.deadStoneOfferState && this.data.deadStoneOfferState === 'RejectedOffer')
    );
  };

  offerSelectSquares = (): void => this.doOfferSelectSquares();

  private doOfferSelectSquares = () => {
    const squares = Array.from(this.chessground.state.selectedPieces.keys());
    const data = {
      variant: this.data.game.variant.key,
      lib: this.data.game.variant.lib,
      s: squares.join(','),
    };
    this.socket.sendLoading('select-squares-offer', data);
  };

  canPassTurn = (): boolean =>
    ['go9x9', 'go13x13', 'go19x19'].includes(this.data.game.variant.key) &&
    this.isPlaying() &&
    !this.replaying() &&
    this.data.player.playerIndex === this.data.game.player &&
    !this.data.selectMode &&
    !this.data.opponent.offeringSelectSquares &&
    !this.data.player.offeringSelectSquares;

  passTurn = (v: boolean): void => {
    if (this.canPassTurn()) {
      if (this.passConfirm) {
        if (v) this.doPassTurn();
        clearTimeout(this.passConfirm);
        this.passConfirm = undefined;
      } else if (v) {
        if (this.data.pref.confirmPass)
          this.passConfirm = setTimeout(() => {
            this.passTurn(false);
          }, 3000);
        else this.doPassTurn();
      }
    }
    this.redraw();
  };

  private doPassTurn = () => {
    //this.setLoading(true);
    this.sendPass(this.data.game.variant.key);
  };

  undoAction = (): void => {
    if (this.data.canUndo) {
      this.sendUndo(this.data.game.variant.key);
      this.redraw();
    }
  };

  cubeAction = (interaction: string): void => {
    this.sendCubeAction(this.data.game.variant.key, interaction);
    this.redraw();
  };

  endTurnAction = (): void => {
    if (this.data.canEndTurn) {
      this.sendEndTurn(this.data.game.variant.key);
    }
    this.redraw();
  };

  setChessground = (cg: CgApi) => {
    this.chessground = cg;
    if (this.data.pref.keyboardMove) {
      this.keyboardMove = makeKeyboardMove(this, this.stepAt(this.ply), this.redraw);
      requestAnimationFrame(() => this.redraw());
    }
    ground.reSelectSelectedSquares(this);
  };

  stepAt = (ply: Ply) => round.plyStep(this.data, ply);
  StepAtTurn = (turn: number) => round.turnStep(this.data, turn);

  bindSpaceToEndTurn = (): void => {
    if (
      this.data.game.variant.key === 'backgammon' ||
      this.data.game.variant.key === 'hyper' ||
      this.data.game.variant.key === 'nackgammon'
    ) {
      window.Mousetrap.bind('space', () => {
        if (this.data.canEndTurn && this.isPlaying()) {
          this.endTurnAction();
        }
      });
    }
  };

  private forcedActionDelayMillis = 500;

  playForcedAction = (): void => {
    const d = this.data;
    if (
      ['backgammon', 'hyper', 'nackgammon'].includes(d.game.variant.key) &&
      this.isPlaying() &&
      !this.replaying() &&
      d.player.playerIndex === d.game.player &&
      d.pref.playForcedAction &&
      d.forcedAction !== undefined
    ) {
      if (d.forcedAction === 'endturn') {
        this.chessground.set({ viewOnly: true });
        setTimeout(() => {
          this.sendEndTurn(d.game.variant.key);
        }, this.forcedActionDelayMillis);
      } else if (d.forcedAction.includes('@')) {
        const dropDests = stratUtils.readDropsByRole(d.possibleDropsByRole).get('s-piece');
        if (dropDests) {
          this.chessground.set({ viewOnly: true });
          setTimeout(() => {
            this.chessground.newPiece(
              {
                role: 's-piece',
                playerIndex: d.game.player,
              },
              dropDests[0] as cg.Key,
            );
            this.onUserNewPiece('s-piece', dropDests[0], { premove: false });
          }, this.forcedActionDelayMillis);
        }
      } else if (d.forcedAction.includes('^')) {
        this.chessground.set({ viewOnly: true });
        setTimeout(() => {
          this.chessground.liftNoAnim(d.forcedAction!.slice(1) as cg.Key);
          this.onUserLift(d.forcedAction!.slice(1) as cg.Key);
        }, this.forcedActionDelayMillis);
      } else {
        const uciMove = util.uci2move(d.forcedAction);
        if (uciMove !== undefined) {
          this.chessground.set({ viewOnly: true });
          setTimeout(() => {
            this.chessground.moveNoAnim(uciMove[0], uciMove[1]);
            this.onUserMove(uciMove[0], uciMove[1], { premove: false });
          }, this.forcedActionDelayMillis);
        }
      }
    }
  };

  private doForcedActions = (): void => {
    const d = this.data;
    if (this.isPlaying() && !this.replaying()) {
      //flipello pass
      if ((d.game.variant.key === 'flipello' || d.game.variant.key === 'flipello10') && d.possibleMoves) {
        this.forcePass(util.parsePossibleMoves(d.possibleMoves), d.game.variant.key);
      }
      //backgammon roll dice at start of turn or end turn when no moves
      if (['backgammon', 'hyper', 'nackgammon'].includes(d.game.variant.key)) {
        if (d.canOnlyRollDice) setTimeout(() => this.forceRollDice(d.game.variant.key), this.forcedActionDelayMillis);
        else if (d.pref.playForcedAction) this.playForcedAction();
      }
    }
  };

  private delayedInit = () => {
    const d = this.data;
    this.doForcedActions();

    if (this.isPlaying() && game.nbMoves(d, d.player.playerIndex) === 0 && !this.isSimulHost()) {
      playstrategy.sound.play('genericNotify');
    }
    playstrategy.requestIdleCallback(() => {
      const d = this.data;
      if (this.isPlaying()) {
        if (!d.simul) blur.init(round.turnsTaken(d) > 1);

        title.init();
        this.setTitle();

        if (d.crazyhouse) crazyInit(this);

        window.addEventListener('beforeunload', e => {
          const d = this.data;
          if (
            playstrategy.unload.expected ||
            this.nvui ||
            !game.playable(d) ||
            !d.clock ||
            d.opponent.ai ||
            this.isSimulHost()
          )
            return;
          this.socket.send('bye2');
          const msg = 'There is a game in progress!';
          (e || window.event).returnValue = msg;
          return msg;
        });

        if (!this.nvui && d.pref.submitMove) {
          window.Mousetrap.bind('esc', () => {
            this.submitMove(false);
            this.chessground.cancelMove();
          }).bind('return', () => this.submitMove(true));
        }
        cevalSub.subscribe(this);
      }

      this.bindSpaceToEndTurn();

      if (!this.nvui) keyboard.init(this);

      speech.setup(this);

      this.onChange();
    }, 800);
  };
}
