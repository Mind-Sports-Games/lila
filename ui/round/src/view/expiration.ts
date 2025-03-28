import { h } from 'snabbdom';
import isCol1 from 'common/isCol1';
import { MaybeVNode, Position } from '../interfaces';
import RoundController from '../ctrl';
import * as round from '../round';
import * as game from 'game';
import { irrelevantPiecesNamesPerGameFamily } from '../util';

let rang = false;

export default function (ctrl: RoundController, position: Position): MaybeVNode {
  const moveIndicator = ctrl.data.pref.playerTurnIndicator;
  const d = game.playable(ctrl.data) && (ctrl.data.expirationAtStart || ctrl.data.expirationOnPaused);
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
  if (isCol1() && !irrelevantPiecesNamesPerGameFamily.includes(ctrl.data.game.gameFamily))
    moveIndicatorText.push(`. ${ctrl.trans('youPlayPieces', ctrl.data.player.playerName)}`);

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
    if (!irrelevantPiecesNamesPerGameFamily.includes(ctrl.data.game.gameFamily))
      moveIndicatorText.push(` ${ctrl.trans('youPlayPieces', gameData.player.playerName)}`);
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
