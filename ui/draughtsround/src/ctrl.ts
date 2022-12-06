/// <reference types="../types/ab" />

import * as ab from 'ab';
import * as round from './round';
import * as game from 'game';
import * as status from 'game/status';
import * as ground from './ground';
import notify from 'common/notification';
import { make as makeSocket, RoundSocket } from './socket';
import * as title from './title';
import * as blur from './blur';
import * as speech from './speech';
import * as cg from 'draughtsground/types';
import { Config as CgConfig } from 'draughtsground/config';
import { Api as DgApi } from 'draughtsground/api';
import { countGhosts } from 'draughtsground/fen';
import { ClockController } from './clock/clockCtrl';
import { CorresClockController, ctrl as makeCorresClock } from './corresClock/corresClockCtrl';
import MoveOn from './moveOn';
import TransientMove from './transientMove';
import * as sound from './sound';
import * as util from './util';
import * as xhr from './xhr';
import { ctrl as makeKeyboardMove, KeyboardMove } from './keyboardMove';
import * as renderUser from './view/user';
import * as cevalSub from './cevalSub';
import * as keyboard from './keyboard';

import {
  RoundOpts,
  RoundData,
  ApiMove,
  ApiEnd,
  Redraw,
  SocketMove,
  SocketDrop,
  SocketOpts,
  MoveMetadata,
  Position,
  NvuiPlugin,
} from './interfaces';

interface GoneBerserk {
  p1?: boolean;
  p2?: boolean;
}

type Timeout = number;

export default class RoundController {
  data: RoundData;
  socket: RoundSocket;
  draughtsground: DgApi;
  clock?: ClockController;
  corresClock?: CorresClockController;
  trans: Trans;
  noarg: TransNoArg;
  keyboardMove?: KeyboardMove;
  moveOn: MoveOn;

  /**
   * We make a strict disctiontion between this.data.game.turns as the game state, determining turn playerIndex etc, and this.ply, determining the game view only
   * Rewrite what variable is used and/or updated where necessary, so that we can safely add "virtual plies" to this.ply
   */
  ply: number;
  firstSeconds = true;
  flip = false;
  loading = false;
  loadingTimeout: number;
  redirecting = false;
  transientMove: TransientMove;
  moveToSubmit?: SocketMove;
  dropToSubmit?: SocketDrop;
  goneBerserk: GoneBerserk = {};
  resignConfirm?: Timeout = undefined;
  drawConfirm?: Timeout = undefined;
  // will be replaced by view layer
  autoScroll: () => void = () => {};
  challengeRematched = false;
  justDropped?: cg.Role;
  justCaptured?: cg.Piece;
  shouldSendMoveTime = false;
  lastDrawOfferAtPly?: Ply;
  nvui?: NvuiPlugin;
  sign: string = Math.random().toString(36);
  private music?: any;

  constructor(readonly opts: RoundOpts, readonly redraw: Redraw) {
    opts.data.steps = round.mergeSteps(opts.data.steps, this.coordSystem(opts.data));
    round.massage(opts.data);

    const d = (this.data = opts.data);

    this.ply = round.lastPly(d);
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

    if (!document.referrer?.includes('/serviceWorker.')) setTimeout(this.showYourMoveNotification, 500);

    // at the end:
    playstrategy.pubsub.on('jump', ply => {
      this.jump(parseInt(ply));
      this.redraw();
    });

    playstrategy.pubsub.on('sound_set', set => {
      if (!this.music && set === 'music')
        playstrategy.loadScript('javascripts/music/play.js').then(() => {
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
    if (!this.data.expiration) return;
    this.redraw();
    setTimeout(this.showExpiration, 250);
  };

  private onUserMove = (orig: cg.Key, dest: cg.Key, meta: cg.MoveMetadata) => {
    if (!this.keyboardMove || !this.keyboardMove.usedSan) ab.move(this, meta);
    this.sendMove(orig, dest, undefined, meta);
  };

  private onMove = (_orig: cg.Key, _dest: cg.Key, captured?: cg.Piece) => {
    if (captured) sound.capture();
    else sound.move();
  };

  private isSimulHost = () => {
    return this.data.simul && this.data.simul.hostId === this.opts.userId;
  };

  lastPly = () => round.lastPly(this.data);

  makeCgHooks = () => ({
    onUserMove: this.onUserMove,
    onMove: this.onMove,
    onNewPiece: sound.move,
    // onPremove: this.onPremove,
    // onCancelPremove: this.onCancelPremove,
  });

  replaying = (): boolean => this.ply !== this.lastPly();

  userJump = (ply: Ply): void => {
    this.cancelMove();
    this.draughtsground.selectSquare(null);
    if (ply != this.ply && this.jump(ply)) speech.userJump(this, this.ply);
    else this.redraw();
  };

  isPlaying = () => game.isPlayerPlaying(this.data);

  jump = (ply: Ply): boolean => {
    ply = Math.max(round.firstPly(this.data), Math.min(this.lastPly(), ply));
    const plyDiff = Math.abs(ply - this.ply);
    this.ply = ply;
    this.justDropped = undefined;
    const s = this.stepAt(ply),
      ghosts = countGhosts(s.fen),
      config: CgConfig = {
        fen: s.fen,
        lastMove: util.uci2move(s.lidraughtsUci),
        turnPlayerIndex: (this.ply - (ghosts == 0 ? 0 : 1)) % 2 === 0 ? 'p1' : 'p2',
      };
    if (this.replaying()) this.draughtsground.stop();
    else {
      config.movable = {
        playerIndex: this.isPlaying() ? this.data.player.playerIndex : undefined,
        dests: util.parsePossibleMoves(this.data.possibleMoves),
      };
      config.captureLength = this.data.captureLength;
    }
    this.draughtsground.set(config, plyDiff > 1);
    if (s.san && plyDiff !== 0) {
      if (s.san.includes('x')) sound.capture();
      else sound.move();
    }
    this.autoScroll();
    if (this.keyboardMove) this.keyboardMove.update(s);
    playstrategy.pubsub.emit('ply', ply);
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

  isAlgebraic = (d: RoundData): boolean => {
    return d.pref.coordSystem === 1 && d.game.variant.board.key === '64';
  };

  coordSystem = (d: RoundData): number => {
    return this.isAlgebraic(d) ? 1 : d.pref.coordSystem;
  };

  isLate = () => this.replaying() && status.playing(this.data);

  playerAt = (position: Position) =>
    (this.flip as any) ^ ((position === 'top') as any) ? this.data.opponent : this.data.player;

  flipNow = () => {
    this.flip = !this.nvui && !this.flip;
    this.draughtsground.set({
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
          socketOpts.millis = moveMillis;
        }
      }
    }
    this.socket.send(tpe, data, socketOpts);

    this.justDropped = meta.justDropped;
    this.justCaptured = meta.justCaptured;
    this.transientMove.register();
    this.redraw();
  };

  sendMove = (orig: cg.Key, dest: cg.Key, prom: cg.Role | undefined, meta: cg.MoveMetadata) => {
    const move: SocketMove = {
      u: orig + dest,
    };
    //if (prom) move.u += prom === 'knight' ? 'n' : prom[0];
    if (prom) move.u += '';
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

  showYourMoveNotification = () => {
    const d = this.data;
    if (game.isPlayerTurn(d))
      notify(() => {
        let txt = this.noarg('yourTurn');
        const opponent = renderUser.userTxt(this, d.opponent);
        if (this.ply < 1) txt = `${opponent}\njoined the game.\n${txt}`;
        else {
          let move = d.steps[d.steps.length - 1].san;
          const turn = Math.floor((this.ply - 1) / 2) + 1;
          move = `${turn}${this.ply % 2 === 1 ? '.' : '...'} ${move}`;
          txt = `${opponent}\nplayed ${move}.\n${txt}`;
        }
        return txt;
      });
    else if (this.isPlaying() && this.ply < 1)
      notify(() => renderUser.userTxt(this, d.opponent) + '\njoined the game.');
  };

  playerByPlayerIndex = (c: PlayerIndex) => this.data[c === this.data.player.playerIndex ? 'player' : 'opponent'];

  apiMove = (o: ApiMove): true => {
    const d = this.data,
      playing = this.isPlaying(),
      ghosts = countGhosts(o.fen);
    d.game.turns = o.ply;
    d.game.player = o.ply % 2 === 0 ? 'p1' : 'p2';
    const playedPlayerIndex = o.ply % 2 === 0 ? 'p2' : 'p1',
      activePlayerIndex = d.player.playerIndex === d.game.player;
    if (o.status) d.game.status = o.status;
    if (o.winner) d.game.winner = o.winner;
    this.playerByPlayerIndex('p1').offeringDraw = o.wDraw;
    this.playerByPlayerIndex('p2').offeringDraw = o.bDraw;
    d.possibleMoves = activePlayerIndex ? o.dests : undefined;
    d.captureLength = o.captLen;

    this.setTitle();
    if (!this.replaying()) {
      //Show next ply if we're following the head of the line (not replaying)
      this.ply = d.game.turns + (ghosts > 0 ? 1 : 0);
      if (o.role)
        this.draughtsground.newPiece(
          {
            role: o.role,
            playerIndex: playedPlayerIndex,
          },
          o.uci.substr(o.uci.length - 2, 2) as cg.Key
        );
      else {
        const keys = util.uci2move(o.uci);
        this.draughtsground.move(keys![0], keys![1], ghosts === 0);
      }
      this.draughtsground.set({
        turnPlayerIndex: d.game.player,
        movable: {
          dests: playing ? util.parsePossibleMoves(d.possibleMoves) : new Map(),
        },
        captureLength: d.captureLength,
      });
      if (o.check) sound.check();
      blur.onMove();
      playstrategy.pubsub.emit('ply', this.ply);
    }
    d.game.threefold = !!o.threefold;

    const step = round.addStep(
      d.steps,
      {
        ply: d.game.turns,
        fen: o.fen,
        san: o.san,
        uci: o.uci,
        lidraughtsUci: o.uci,
      },
      this.coordSystem(d)
    );

    this.justDropped = undefined;
    this.justCaptured = undefined;
    game.setOnGame(d, playedPlayerIndex, true);
    this.data.forecastCount = undefined;
    if (o.clock) {
      this.shouldSendMoveTime = true;
      const oc = o.clock,
        delay = playing && activePlayerIndex ? 0 : oc.lag || 1;
      if (this.clock) this.clock.setClock(d, oc.p1, oc.p2, delay);
      else if (this.corresClock) this.corresClock.update(oc.p1, oc.p2);
    }
    if (this.data.expiration) {
      if (this.data.steps.length > 2) this.data.expiration = undefined;
      else this.data.expiration.movedAt = Date.now();
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
      const premoveDelay = 1;
      setTimeout(() => {
        if (!this.draughtsground.playPremove()) {
          this.showYourMoveNotification();
        }
      }, premoveDelay);
    }
    this.autoScroll();
    this.onChange();
    if (this.keyboardMove) this.keyboardMove.update(step, playedPlayerIndex != d.player.playerIndex);
    if (this.music) this.music.jump(o);
    speech.step(o, this.isAlgebraic(this.data));
    return true; // prevents default socket pubsub
  };

  private clearJust() {
    this.justDropped = undefined;
    this.justCaptured = undefined;
  }

  reload = (d: RoundData): void => {
    d.steps = round.mergeSteps(d.steps, this.coordSystem(d));
    if (d.steps.length !== this.data.steps.length) this.ply = d.steps[d.steps.length - 1].ply;
    round.massage(d);
    this.data = d;
    this.clearJust();
    this.shouldSendMoveTime = false;
    if (this.clock) this.clock.setClock(d, d.clock!.p1, d.clock!.p2);
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
  };

  endWithData = (o: ApiEnd): void => {
    const d = this.data;
    d.game.winner = o.winner;
    d.game.winnerPlayer = o.winnerPlayer;
    d.game.loserPlayer = o.loserPlayer;
    d.game.status = o.status;
    d.game.boosted = o.boosted;
    this.userJump(this.lastPly());
    this.draughtsground.stop();
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
    this.clearJust();
    this.setTitle();
    this.moveOn.next();
    this.setQuietMode();
    this.setLoading(false);
    if (this.clock && o.clock) this.clock.setClock(d, o.clock.wc * 0.01, o.clock.bc * 0.01);
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
      }
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
    this.draughtsground.cancelPremove();
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
      this.transientMove.register();
    }, 2500);
    this.redraw();
  };

  submitMove = (v: boolean): void => {
    const toSubmit = this.moveToSubmit || this.dropToSubmit;
    if (v && toSubmit) {
      if (this.moveToSubmit) this.actualSendMove('move', this.moveToSubmit);
      else this.actualSendMove('drop', this.dropToSubmit);
      playstrategy.sound.play('confirmation');
    } else this.jump(this.ply);
    this.cancelMove();
    if (toSubmit) this.setLoading(true, 300);
  };

  cancelMove = (): void => {
    this.moveToSubmit = undefined;
    this.dropToSubmit = undefined;
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
    return d.opponent.gone !== false && !game.isPlayerTurn(d) && game.resignable(d) && d.opponent.gone;
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

  setDraughtsground = (dg: DgApi) => {
    this.draughtsground = dg;
    if (this.data.pref.keyboardMove) {
      this.keyboardMove = makeKeyboardMove(this, this.stepAt(this.ply), this.redraw);
      requestAnimationFrame(() => this.redraw());
    }
  };

  stepAt = (ply: Ply) => round.plyStep(this.data, ply);

  private delayedInit = () => {
    const d = this.data;
    if (this.isPlaying() && game.nbMoves(d, d.player.playerIndex) === 0 && !this.isSimulHost()) {
      playstrategy.sound.play('genericNotify');
    }
    playstrategy.requestIdleCallback(() => {
      const d = this.data;
      if (this.isPlaying()) {
        if (!d.simul) blur.init(d.steps.length > 2);

        title.init();
        this.setTitle();

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
            this.draughtsground.cancelMove();
          }).bind('return', () => this.submitMove(true));
        }
        cevalSub.subscribe(this);
      }

      if (!this.nvui) keyboard.init(this);

      speech.setup(this);

      this.onChange();
    }, 800);
  };
}
