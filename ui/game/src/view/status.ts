import { Ctrl } from '../interfaces';

export default function status(ctrl: Ctrl): string {
  const noarg = ctrl.trans.noarg,
    d = ctrl.data;
  switch (d.game.status.name) {
    case 'started':
      return noarg('playingRightNow');
    case 'aborted':
      return noarg('gameAborted');
    case 'mate':
      switch (d.game.variant.lib) {
        case 0:
          return noarg('checkmate');
        case 1:
          return '';
        case 2:
          return noarg('checkmate');
      }
      return '';
    case 'resign':
      return ctrl.trans('playerIndexResigned', d.game.loserPlayer ? d.game.loserPlayer : '');
    case 'resignGammon':
      return ctrl.trans('playerIndexResignedGammon', d.game.loserPlayer ? d.game.loserPlayer : '');
    case 'resignBackgammon':
      return ctrl.trans('playerIndexResignedBackgammon', d.game.loserPlayer ? d.game.loserPlayer : '');
    case 'resignMatch':
      return ctrl.trans('playerIndexResignedMatch', d.game.loserPlayer ? d.game.loserPlayer : '');
    case 'cubeDropped':
      return ctrl.trans('playerIndexDroppedTheCube', d.game.loserPlayer ? d.game.loserPlayer : '');
    case 'stalemate':
      return noarg('stalemate');
    case 'timeout':
      return d.game.loserPlayer ? ctrl.trans('playerIndexLeftTheGame', d.game.loserPlayer) : noarg('draw');
    case 'draw':
      return noarg('draw');
    case 'outoftime':
      //return `${d.game.turns % 2 === 0 ? noarg('whiteTimeOut') : noarg('blackTimeOut')}${
      //  d.game.winner ? '' : ` • ${noarg('draw')}`
      //}`;
      return d.game.loserPlayer
        ? ctrl.trans('playerIndexTimeOut', d.game.loserPlayer)
        : `${ctrl.trans('playerIndexTimeOut', '')} • ${noarg('draw')}`;
    case 'outoftimeGammon':
      return ctrl.trans('playerIndexLosesByGammonTimeOut', d.game.loserPlayer ? d.game.loserPlayer : '');
    case 'outoftimeBackgammon':
      return ctrl.trans('playerIndexLosesByBackgammonTimeOut', d.game.loserPlayer ? d.game.loserPlayer : '');
    case 'ruleOfGin':
      return ctrl.trans('playerIndexWinsByRuleOfGin', d.game.winnerPlayer ? d.game.winnerPlayer : '');
    case 'ginGammon':
      return ctrl.trans('playerIndexWinsByGinGammon', d.game.winnerPlayer ? d.game.winnerPlayer : '');
    case 'ginBackgammon':
      return ctrl.trans('playerIndexWinsByGinBackgammon', d.game.winnerPlayer ? d.game.winnerPlayer : '');
    case 'noStart':
      return d.game.loserPlayer + " didn't move";
    case 'cheat':
      return noarg('cheatDetected');
    case 'perpetualCheck':
      return noarg('perpetualCheck');
    case 'singleWin':
      return noarg('backgammonSingleWin');
    case 'gammonWin':
      return noarg('backgammonGammonWin');
    case 'backgammonWin':
      return noarg('backgammonBackgammonWin');
    case 'variantEnd':
      switch (d.game.variant.key) {
        case 'kingOfTheHill':
          return noarg('kingInTheCenter');
        case 'threeCheck':
          return noarg('threeChecks');
        case 'fiveCheck':
          return noarg('fiveChecks');
        case 'linesOfAction':
          return noarg('checkersConnected');
        case 'scrambledEggs':
          return noarg('checkersConnected');
        case 'breakthrough':
          return noarg('promotion');
        case 'flipello':
        case 'flipello10':
        case 'antiflipello':
        case 'octagonflipello':
          return noarg('gameFinished');
        case 'amazons':
          return noarg('gameFinished');
        case 'breakthroughtroyka':
        case 'minibreakthroughtroyka':
          return noarg('raceFinished');
        case 'oware':
          if (d.game.isRepetition) {
            return noarg('gameFinishedRepetition');
          } else {
            return noarg('gameFinished');
          }
        case 'togyzkumalak':
        case 'bestemshe':
          return noarg('gameFinished');
        case 'go9x9':
        case 'go13x13':
        case 'go19x19':
          if (d.game.isRepetition) {
            return noarg('gameFinishedRepetition');
          } else {
            return noarg('gameFinished');
          }
        case 'backgammon':
        case 'hyper':
        case 'nackgammon':
          return noarg('gameFinished');
        case 'abalone':
          return noarg('gameFinished');
      }
      return noarg('variantEnding');
    case 'unknownFinish':
      return 'Finished';
    default:
      return d.game.status.name;
  }
}
