import * as game from 'game';
import throttle from 'common/throttle';
import modal from 'common/modal';
import notify from 'common/notification';
import * as xhr from './xhr';
import * as sound from './sound';
import RoundController from './ctrl';
import { Untyped } from './interfaces';
import { defined } from 'common';

export interface RoundSocket extends Untyped {
  send: SocketSend;
  handlers: Untyped;
  moreTime(): void;
  outoftime(): void;
  berserk(): void;
  sendLoading(typ: string, data?: any): void;
  receive(typ: string, data: any): boolean;
}

interface Incoming {
  t: string;
  d: any;
}

interface Handlers {
  [key: string]: (data: any) => void;
}

type Callback = (...args: any[]) => void;

function backoff(delay: number, factor: number, callback: Callback): Callback {
  let timer: number | undefined;
  let lastExec = 0;

  return function (this: any, ...args: any[]): void {
    const self: any = this;
    const elapsed = performance.now() - lastExec;

    function exec() {
      timer = undefined;
      lastExec = performance.now();
      delay *= factor;
      callback.apply(self, args);
    }

    if (timer) clearTimeout(timer);

    if (elapsed > delay) exec();
    else timer = setTimeout(exec, delay - elapsed);
  };
}

export function make(send: SocketSend, ctrl: RoundController): RoundSocket {
  playstrategy.socket.sign(ctrl.sign);

  function reload(o: Incoming, isRetry?: boolean) {
    // avoid reload if possible!
    if (o && o.t) {
      ctrl.setLoading(false);
      handlers[o.t](o.d);
    } else
      xhr.reload(ctrl).then(data => {
        if (playstrategy.socket.getVersion() > data.player.version) {
          // race condition! try to reload again
          if (isRetry) playstrategy.reload();
          // give up and reload the page
          else reload(o, true);
        } else ctrl.reload(data);
      }, playstrategy.reload);
  }

  const handlers: Handlers = {
    takebackOffers(o) {
      ctrl.setLoading(false);
      ctrl.data.player.proposingTakeback = o[ctrl.data.player.playerIndex];
      const fromOp = (ctrl.data.opponent.proposingTakeback = o[ctrl.data.opponent.playerIndex]);
      if (fromOp) notify(ctrl.noarg('yourOpponentProposesATakeback'));
      ctrl.redraw();
    },
    move: ctrl.apiMove,
    drop: ctrl.apiMove,
    reload,
    redirect: ctrl.setRedirecting,
    clockInc(o) {
      if (ctrl.clock) {
        ctrl.clock.addTime(o.playerIndex, o.time);
        ctrl.redraw();
      }
    },
    clock(o) {
      if (ctrl.clock) {
        ctrl.clock.setClock(ctrl.data, o.p1, o.p2, o.p1Pending, o.p2Pending, o.p1Periods, o.p2Periods);
        ctrl.redraw();
      }
    },
    cclock(o) {
      if (ctrl.corresClock) {
        ctrl.data.correspondence.p1 = o.p1;
        ctrl.data.correspondence.p2 = o.p2;
        ctrl.corresClock.update(o.p1, o.p2);
        ctrl.redraw();
      }
    },
    crowd(o) {
      ['p1', 'p2'].forEach(c => {
        if (defined(o[c])) game.setOnGame(ctrl.data, c as PlayerIndex, o[c]);
      });
      ctrl.redraw();
    },
    endData: ctrl.endWithData,
    rematchOffer(by: PlayerIndex) {
      ctrl.data.player.offeringRematch = by === ctrl.data.player.playerIndex;
      if ((ctrl.data.opponent.offeringRematch = by === ctrl.data.opponent.playerIndex))
        notify(ctrl.noarg('yourOpponentWantsToPlayANewGameWithYou'));
      ctrl.redraw();
    },
    rematchTaken(nextId: string) {
      ctrl.data.game.rematch = nextId;
      if (!ctrl.data.player.spectator) ctrl.setLoading(true);
      else ctrl.redraw();
    },
    drawOffer(by) {
      if (ctrl.isPlaying()) {
        ctrl.data.player.offeringDraw = by === ctrl.data.player.playerIndex;
        const fromOp = (ctrl.data.opponent.offeringDraw = by === ctrl.data.opponent.playerIndex);
        if (fromOp) notify(ctrl.noarg('yourOpponentOffersADraw'));
      }
      if (by) {
        let turnCount = ctrl.lastTurn();
        if ((by == 'p1') == (turnCount % 2 == 0)) turnCount++;
        ctrl.data.game.drawOffers = (ctrl.data.game.drawOffers || []).concat([turnCount]);
      }
      ctrl.redraw();
    },
    berserk(playerIndex: PlayerIndex) {
      ctrl.setBerserk(playerIndex);
    },
    gone: ctrl.setGone,
    goneIn: ctrl.setGone,
    kingMoves(e) {
      if (ctrl.data.pref.showKingMoves) {
        ctrl.draughtsground.setKingMoves({
          p1: { count: e.p1, key: e.p1King },
          p2: { count: e.p2, key: e.p2King },
        });
        ctrl.redraw();
      }
    },
    simulPlayerMove(gameId: string) {
      if (
        ctrl.opts.userId &&
        ctrl.data.simul &&
        ctrl.opts.userId == ctrl.data.simul.hostId &&
        gameId !== ctrl.data.game.id &&
        ctrl.moveOn.get() &&
        !game.isPlayerTurn(ctrl.data)
      ) {
        ctrl.setRedirecting();
        sound.move();
        location.href = '/' + gameId;
      }
    },
    simulEnd(simul: game.Simul) {
      playstrategy.loadCssPath('modal');
      modal(
        $(
          '<p>Simul complete!</p><br /><br />' +
            `<a class="button" href="/simul/${simul.id}">Back to ${simul.name} simul</a>`
        )
      );
    },
  };

  playstrategy.pubsub.on('ab.rep', n => send('rep', { n }));

  const draughtsSend = (t: string, d?: any, o: any = {}, noRetry = false) => {
    if (typeof d === 'object' && d !== null) d.lib = 1; // Add in the draughts lib to the data.
    return send(t, d, o, noRetry);
  };

  return {
    send: draughtsSend,
    handlers,
    moreTime: throttle(300, () => draughtsSend('moretime')),
    outoftime: backoff(500, 1.1, () => draughtsSend('flag', ctrl.data.game.player)),
    berserk: throttle(200, () => draughtsSend('berserk', null, { ackable: true })),
    sendLoading(typ: string, data?: any) {
      ctrl.setLoading(true);
      draughtsSend(typ, data);
    },
    receive(typ: string, data: any): boolean {
      if (handlers[typ]) {
        handlers[typ](data);
        return true;
      }
      return false;
    },
    reload,
  };
}
