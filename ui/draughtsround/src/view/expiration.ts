import { h } from 'snabbdom';
import isCol1 from 'common/isCol1';
import { MaybeVNode, Position } from '../interfaces';
import RoundController from '../ctrl';
import * as game from 'game';

let rang = false;

export default function (ctrl: RoundController, position: Position): MaybeVNode {
  const moveIndicator = ctrl.data.pref.playerTurnIndicator;
  const d = game.playable(ctrl.data) && ctrl.data.expirationAtStart;
  let timeLeft = 8000;
  if ((!d && !moveIndicator && position === 'bottom') || !game.playable(ctrl.data)) return;
  if (!moveIndicator && !d) {
    return h(
      'div.expiration.expiration-' + position,
      { attrs: { style: 'visibility: hidden' } },
      'div not shown... but for layout',
    );
  }
  if (d) {
    timeLeft = Math.max(0, d.updatedAt - Date.now() + d.millisToMove);
  }
  const secondsLeft = Math.floor(timeLeft / 1000),
    myTurn = game.isPlayerTurn(ctrl.data),
    transStr = myTurn ? 'nbSecondsToPlayTheFirstMove' : 'nbSecondsForOpponentToPlayTheFirstMove',
    emerg = myTurn && timeLeft < 8000;
  if (!rang && emerg) {
    playstrategy.sound.play('lowTime');
    rang = true;
  }
  const side = myTurn != ctrl.flip ? 'bottom' : 'top';
  let moveIndicatorText = ctrl.trans.vdomPlural(transStr, secondsLeft, h('strong', '' + secondsLeft));

  const gameData = ctrl.data;
  if (
    !d &&
    isCol1() &&
    moveIndicator &&
    game.isPlayerPlaying(gameData) &&
    !game.playerHasPlayedTurn(gameData) &&
    !gameData.player.spectator
  ) {
    moveIndicatorText = [];
    if (myTurn) moveIndicatorText.push(`${ctrl.trans.noarg('itsYourTurn')}`);
    else moveIndicatorText.push(`${ctrl.trans('waitingForOpponent')}.`);
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
