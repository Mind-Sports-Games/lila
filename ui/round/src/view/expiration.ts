import { h } from 'snabbdom';
import { MaybeVNode, Position } from '../interfaces';
import RoundController from '../ctrl';
import * as round from '../round';
import { isPlayerTurn, playable } from 'game';
import isCol1 from 'common/isCol1';

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
    transStr =
      ctrl.data.expirationOnPaused && ctrl.data.deadStoneOfferState == 'ChooseFirstOffer'
        ? myTurn
          ? 'nbSecondsToOfferDeadStones'
          : 'nbSecondsForOpponentToOfferDeadStones'
        : ctrl.data.expirationOnPaused
          ? myTurn
            ? 'nbSecondsToRespondToOffer'
            : 'nbSecondsForOpponentToRespondToOffer'
          : myTurn
            ? 'nbSecondsToPlayTheFirstMove'
            : 'nbSecondsForOpponentToPlayTheFirstMove';

  let emerg = myTurn && timeLeft < 8000;
  if (!rang && emerg) {
    playstrategy.sound.play('lowTime');
    rang = true;
  }
  const side = myTurn != ctrl.flip ? 'bottom' : 'top';
  let moveIndicatorText = ctrl.trans.vdomPlural(transStr, secondsLeft, h('strong', '' + secondsLeft));

  if (
    moveIndicator &&
    (round.turnsTaken(ctrl.data) > 1 || !ctrl.data.expirationAtStart) &&
    !ctrl.data.expirationOnPaused
  ) {
    emerg =
      ctrl.clock !== undefined &&
      ctrl.clock.times.activePlayerIndex !== undefined &&
      ctrl.clock?.millisOf(ctrl.clock.times.activePlayerIndex) < 10000;
    moveIndicatorText = myTurn ? [ctrl.trans('yourTurn')] : [ctrl.trans('waitingForOpponent')];
  }

  if (moveIndicator && round.turnsTaken(ctrl.data) < 2 && isCol1()) {
    moveIndicatorText = myTurn
      ? [
          `${ctrl.trans('youPlayThePlayerIndexPieces', ctrl.data.player.playerName)}.`,
          ` ${ctrl.trans.noarg('itsYourTurn')}`,
        ]
      : [
          `${ctrl.trans('youPlayThePlayerIndexPieces', ctrl.data.player.playerName)}.`,
          ` ${ctrl.trans('waitingForOpponent')}`,
        ];
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
      moveIndicatorText,
    );
  } else {
    return h(
      'div.expiration.expiration-' + position,
      { attrs: { style: 'visibility: hidden' } },
      'div not shown... but for layout',
    );
  }
}
