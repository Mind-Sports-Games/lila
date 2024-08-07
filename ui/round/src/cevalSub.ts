import { lastStep } from './round';
import RoundController from './ctrl';
import { ApiAction, RoundData } from './interfaces';
import * as xhr from 'common/xhr';

let found = false;

const truncateFen = (fen: Fen): string => fen.split(' ')[0];

export function subscribe(ctrl: RoundController): void {
  // allow everyone to cheat against the AI
  if (ctrl.data.opponent.ai) return;
  // allow registered players to use assistance in casual games
  if (!ctrl.data.game.rated && ctrl.opts.userId) return;
  // bots can cheat alright
  if (ctrl.data.player.user?.title == 'BOT') return;

  // Notify tabs to disable ceval. Unless this game is loaded directly on a
  // position being analysed, there is plenty of time (7 moves, in most cases)
  // for this to take effect.
  playstrategy.storage.fire('ceval.disable');

  playstrategy.storage.make('ceval.fen').listen(e => {
    const d = ctrl.data,
      step = lastStep(ctrl.data);
    if (!found && step.ply > 14 && ctrl.isPlaying() && e.value && truncateFen(step.fen) == truncateFen(e.value)) {
      xhr.text(`/jslog/${d.game.id}${d.player.id}?n=ceval`, { method: 'post' });
      found = true;
    }
  });
}

export function publish(d: RoundData, action: ApiAction) {
  if (d.opponent.ai) playstrategy.storage.fire('ceval.fen', action.fen);
}
