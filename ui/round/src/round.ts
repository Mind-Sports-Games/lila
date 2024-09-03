import { attributesModule, classModule, init } from 'snabbdom';
import PlayStrategyRound from './boot';
import PlayStrategyChat from 'chat';
import menuHover from 'common/menuHover';
import Draughtsground from 'draughtsground';
import { Chessground } from 'chessground';
import RoundController from './ctrl';
import { RoundData, RoundOpts, Step } from './interfaces';
import MoveOn from './moveOn';
import { main as view } from './view/main';

export interface RoundApi {
  socketReceive(typ: string, data: any): boolean;
  moveOn: MoveOn;
}

const patch = init([classModule, attributesModule]);

// export function boot(opts: RoundOpts) {
//   booti(opts, app);
// }

// export default function PlayStrategyRound(opts: RoundOpts) {

//   // @ts-ignore
//   function boot() {
//     boot(opts, app);
//   }

// }

export interface RoundMain {
  app: (opts: RoundOpts) => RoundApi;
}

export const firstPly = (d: RoundData): number => d.steps[0].ply;

export const firstTurn = (d: RoundData): number => d.steps[0].turnCount;

export const lastPly = (d: RoundData): number => lastStep(d).ply;

export const lastTurn = (d: RoundData): number => lastStep(d).turnCount;

export const turnsTaken = (d: RoundData): number => lastTurn(d) - firstTurn(d);

export const lastStep = (d: RoundData): Step => d.steps[d.steps.length - 1];

export const plyStep = (d: RoundData, ply: number): Step => d.steps[ply - firstPly(d)];

export const turnStep = (d: RoundData, turn: number): Step =>
  turn <= lastTurn(d) && turn >= firstTurn(d) ? d.steps.filter(s => s.turnCount === turn)[0] : d.steps[0];

export const massage = (d: RoundData): void => {
  if (d.clock) {
    d.clock.showTenths = d.pref.clockTenths;
    d.clock.showBar = d.pref.clockBar;
  }

  if (d.correspondence) d.correspondence.showBar = d.pref.clockBar;

  if (['horde', 'monster', 'crazyhouse', 'shogi', 'minishogi', 'amazons'].includes(d.game.variant.key))
    d.pref.showCaptured = false;

  if (d.expirationAtStart) d.expirationAtStart.updatedAt = Date.now() - d.expirationAtStart.idleMillis;
  if (d.expirationOnPaused) d.expirationOnPaused.updatedAt = Date.now() - d.expirationOnPaused.idleMillis;
};

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
  };
}

export { PlayStrategyRound };

window.PlayStrategyChat = PlayStrategyChat;
// that's for the rest of playstrategy to access chessground
// without having to include it a second time
window.Chessground = Chessground;
window.Draughtsground = Draughtsground; // We need both for the "ongoing games" underneath the current one.
