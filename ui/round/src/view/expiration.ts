import { h } from 'snabbdom';
import { MaybeVNode, Position } from '../interfaces';
import RoundController from '../ctrl';
import { isPlayerTurn, playable } from 'game';

let rang = false;

export default function (ctrl: RoundController, position: Position): MaybeVNode {
  const moveIndicator = ctrl.data.pref.playerTurnIndicator;
  const d = playable(ctrl.data) && ctrl.data.expiration;
  let timeLeft = 8000;
  if ((!d && !moveIndicator) || !playable(ctrl.data)) return;
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
  let moveIndicatorText = ctrl.trans.vdomPlural(
    'nbSecondsToPlayTheFirstMove',
    secondsLeft,
    h('strong', '' + secondsLeft)
  );

  //make it even clearer who it is to move when the countdown is on screen for first moves
  if (moveIndicator)
    moveIndicatorText.unshift(myTurn ? ctrl.trans('yourTurn') + ':' : ctrl.trans('waitingForOpponent') + ':');

  if (moveIndicator && (ctrl.data.steps.length > 2 || !ctrl.data.expiration)) {
    emerg = false;
    if (myTurn) {
      moveIndicatorText = [ctrl.trans('yourTurn')];
    } else {
      moveIndicatorText = [ctrl.trans('waitingForOpponent')];
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
      moveIndicatorText
    );
  } else {
    return h(
      'div.expiration.expiration-' + position,
      { attrs: { style: 'visibility: hidden' } },
      'div not shown... but for layout'
    );
  }
}
