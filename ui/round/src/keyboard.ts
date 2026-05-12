import RoundController from './ctrl';
import { firstPly as roundFirstPly, lastPly as roundLastPly} from './round';

export const prev = (ctrl: RoundController) => ctrl.userJump(ctrl.ply - 1);

export const next = (ctrl: RoundController) => ctrl.userJump(ctrl.ply + 1);

export const init = (ctrl: RoundController) =>
  window.Mousetrap.bind(['left', 'h'], () => {
    prev(ctrl);
    ctrl.redraw();
  })
    .bind(['right', 'l'], () => {
      next(ctrl);
      ctrl.redraw();
    })
    .bind(['up', 'k'], () => {
      ctrl.userJump(roundFirstPly(ctrl.data));
      ctrl.redraw();
    })
    .bind(['down', 'j'], () => {
      ctrl.userJump(roundLastPly(ctrl.data));
      ctrl.redraw();
    })
    .bind('f', ctrl.flipNow)
    .bind('z', () => playstrategy.pubsub.emit('zen'));
