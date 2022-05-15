import { lastStep } from './round';
import RoundController from './ctrl';
import { ApiMove, RoundData } from './interfaces';

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
    const step = lastStep(ctrl.data);
    if (!found && step.ply > 14 && ctrl.isPlaying() && e.value && truncateFen(step.fen) == truncateFen(e.value)) {
      found = true;
    }
  });
}

export function publish(d: RoundData, move: ApiMove) {
  if (d.opponent.ai) playstrategy.storage.fire('ceval.fen', move.fen);
}
