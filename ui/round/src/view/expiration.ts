import { h } from 'snabbdom';
import { MaybeVNode, Position } from '../interfaces';
import RoundController from '../ctrl';
import { isPlayerTurn, playable } from 'game';

let rang = false;

export default function (ctrl: RoundController, position: Position): MaybeVNode {
  const moveIndicator = ctrl.data.pref.playerTurnIndicator;
  const d = playable(ctrl.data) && (ctrl.data.expirationAtStart || ctrl.data.expirationOnPaused);
  let timeLeft = 8000;
  if ((!d && !moveIndicator) || !playable(ctrl.data)) return;
  if (d) {
    timeLeft = Math.max(0, d.updatedAt - Date.now() + d.millisToMove);
  }
  const secondsLeft = Math.floor(timeLeft / 1000),
    myTurn = isPlayerTurn(ctrl.data),
    transStr = ctrl.data.expirationAtStart
      ? myTurn
        ? 'nbSecondsToPlayTheFirstMove'
        : 'nbSecondsForOpponentToPlayTheFirstMove'
      : ctrl.data.deadStoneOfferState == 'ChooseFirstOffer'
      ? myTurn
        ? 'nbSecondsToOfferDeadStones'
        : 'nbSecondsForOpponentToOfferDeadStones'
      : myTurn
      ? 'nbSecondsToRespondToOffer'
      : 'nbSecondsForOpponentToRespondToOffer';

  let emerg = myTurn && timeLeft < 8000;
  if (!rang && emerg) {
    playstrategy.sound.play('lowTime');
    rang = true;
  }
  const side = myTurn != ctrl.flip ? 'bottom' : 'top';
  let moveIndicatorText = ctrl.trans.vdomPlural(transStr, secondsLeft, h('strong', '' + secondsLeft));

  if (
    moveIndicator &&
    (ctrl.data.steps.length > (ctrl.data.game.variant.key === 'amazons' ? 4 : 2) || !ctrl.data.expirationAtStart) &&
    !ctrl.data.expirationOnPaused
  ) {
    emerg =
      ctrl.clock !== undefined &&
      ctrl.clock.times.activePlayerIndex !== undefined &&
      ctrl.clock?.millisOf(ctrl.clock.times.activePlayerIndex) < 10000;
    moveIndicatorText = myTurn ? [ctrl.trans('yourTurn')] : [ctrl.trans('waitingForOpponent')];
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
