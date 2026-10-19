import { Ctrl } from '../interfaces';

function resultName(ctrl: Ctrl, playerIndex: PlayerIndex | undefined, playerName: PlayerName | undefined): string {
  if (!playerName) return '';
  const d = ctrl.data,
    player = [d.player, d.opponent].find(p => p.playerIndex === playerIndex),
    name = player?.user
      ? player.user.username
      : player?.ai
        ? ctrl.trans('aiNameLevelAiLevel', 'Stockfish', player.ai)
        : player?.name;
  return name || playerName;
}

export function winnerName(ctrl: Ctrl): string {
  return resultName(ctrl, ctrl.data.game.winner, ctrl.data.game.winnerPlayer);
}

export function loserName(ctrl: Ctrl): string {
  const winner = ctrl.data.game.winner;
  return resultName(ctrl, winner ? (winner === 'p1' ? 'p2' : 'p1') : undefined, ctrl.data.game.loserPlayer);
}

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
      return ctrl.trans('playerIndexResigned', loserName(ctrl));
    case 'resignGammon':
      return ctrl.trans('playerIndexResignedGammon', loserName(ctrl));
    case 'resignBackgammon':
      return ctrl.trans('playerIndexResignedBackgammon', loserName(ctrl));
    case 'resignMatch':
      return ctrl.trans('playerIndexResignedMatch', loserName(ctrl));
    case 'cubeDropped':
      return ctrl.trans('playerIndexDroppedTheCube', loserName(ctrl));
    case 'stalemate':
      return noarg('stalemate');
    case 'timeout':
      return d.game.loserPlayer ? ctrl.trans('playerIndexLeftTheGame', loserName(ctrl)) : noarg('draw');
    case 'draw':
      return noarg('draw');
    case 'outoftime':
      //return `${d.game.turns % 2 === 0 ? noarg('whiteTimeOut') : noarg('blackTimeOut')}${
      //  d.game.winner ? '' : ` • ${noarg('draw')}`
      //}`;
      return d.game.loserPlayer
        ? ctrl.trans('playerIndexTimeOut', loserName(ctrl))
        : `${ctrl.trans('playerIndexTimeOut', '')} • ${noarg('draw')}`;
    case 'outoftimeGammon':
      return ctrl.trans('playerIndexLosesByGammonTimeOut', loserName(ctrl));
    case 'outoftimeBackgammon':
      return ctrl.trans('playerIndexLosesByBackgammonTimeOut', loserName(ctrl));
    case 'ruleOfGin':
      return ctrl.trans('playerIndexWinsByRuleOfGin', winnerName(ctrl));
    case 'ginGammon':
      return ctrl.trans('playerIndexWinsByGinGammon', winnerName(ctrl));
    case 'ginBackgammon':
      return ctrl.trans('playerIndexWinsByGinBackgammon', winnerName(ctrl));
    case 'noStart':
      return ctrl.trans('playerIndexDidntMove', loserName(ctrl));
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
        case 'dameo':
          return noarg('gameFinished');
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
        case 'grandabalone':
          return noarg('gameFinished');
        case 'gomoku':
        case 'pente':
        case 'renju':
          return noarg('gameFinished');
        case 'entropy':
          return noarg('gameFinished');
      }
      return noarg('variantEnding');
    case 'unknownFinish':
      return 'Finished';
    default:
      return d.game.status.name;
  }
}
