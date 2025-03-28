import { attributesModule, classModule, init } from 'snabbdom';
import boot from './boot';
import PlayStrategyChat from 'chat';
import menuHover from 'common/menuHover';
import MoveOn from './moveOn';
import RoundController from './ctrl';
import Draughtsground from 'draughtsground';
import { Chessground } from 'chessground';
import { main as view } from './view/main';
import { RoundData, RoundOpts, Redraw, Step } from './interfaces';

export interface RoundApi {
  socketReceive(typ: string, data: any): boolean;
  moveOn: MoveOn;
  trans: Trans;
  redraw: Redraw;
  draughtsResult: boolean;
}

export interface RoundMain {
  app: (opts: RoundOpts) => RoundApi;
}

export const firstTurn = (d: RoundData): number => d.steps[0].turnCount;
export const lastTurn = (d: RoundData): number => lastStep(d).turnCount;
export const turnsTaken = (d: RoundData): number => lastTurn(d) - firstTurn(d);
export const lastStep = (d: RoundData): Step => d.steps[d.steps.length - 1];

const patch = init([classModule, attributesModule]);

export function app(opts: RoundOpts): RoundApi {
  const ctrl = new RoundController(opts, redraw);

  const blueprint = view(ctrl);
  opts.element.innerHTML = '';
  let vnode = patch(opts.element, blueprint);

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  window.addEventListener('resize', redraw); // col1 / col2+ transition

  if (ctrl.isPlaying()) menuHover();

  return {
    socketReceive: ctrl.socket.receive,
    moveOn: ctrl.moveOn,
    trans: ctrl.trans,
    redraw: ctrl.redraw,
    draughtsResult: ctrl.data.pref.draughtsResult,
  };
}

export { boot };

window.PlayStrategyChat = PlayStrategyChat;
// that's for the rest of playstrategy to access chessground
// without having to include it a second time
window.Draughtsground = Draughtsground;
window.Chessground = Chessground; // We need both for the "ongoing games" underneath the curren one.
