import { h } from 'snabbdom';
import { MaybeVNode, Position } from '../interfaces';
import RoundController from '../ctrl';
import { isPlayerTurn, playable } from 'game';

let rang = false;

export default function (ctrl: RoundController, position: Position): MaybeVNode {
  const moveIndicater = true;
  const d = playable(ctrl.data) && ctrl.data.expiration;
  let timeLeft = 8000;
  if ((!d && !moveIndicater) || !playable(ctrl.data)) return;
  if (d) {
    timeLeft = Math.max(0, d.movedAt - Date.now() + d.millisToMove);
  }
  const secondsLeft = Math.floor(timeLeft / 1000),
    myTurn = isPlayerTurn(ctrl.data);
  let emerg = myTurn && timeLeft < 8000;
  if (!rang && emerg) {
    playstrategy.sound.play('lowTime');
    rang = true;
  }
  const side = myTurn != ctrl.flip ? 'bottom' : 'top';
  let moveIndicaterText = ctrl.trans.vdomPlural(
    'nbSecondsToPlayTheFirstMove',
    secondsLeft,
    h('strong', '' + secondsLeft)
  );

  if (moveIndicater && ctrl.data.steps.length > 2) {
    emerg = false;
    if (myTurn) {
      moveIndicaterText = [ctrl.trans('yourTurn')];
    } else {
      moveIndicaterText = [ctrl.trans('waitingForOpponent')];
    }
  }

  if (position == side) {
    return h(
      'div.expiration.expiration-' + side,
      {
        class: {
          emerg,
          'bar-glider': myTurn,
        },
      },
      moveIndicaterText
    );
  } else {
    return h(
      'div.expiration.expiration-' + position,
      { attrs: { style: 'visibility: hidden' } },
      'div not shown... but for layout'
    );
  }
}
