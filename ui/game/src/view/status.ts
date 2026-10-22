import { Ctrl } from '../interfaces';

type Side = 'winner' | 'loser';

type Description = string | { key: I18nKey; side: Side };

function sidePlayerIndex(ctrl: Ctrl, side: Side): PlayerIndex | undefined {
  const winner = ctrl.data.game.winner;
  if (side === 'winner') return winner;
  return winner ? (winner === 'p1' ? 'p2' : 'p1') : undefined;
}

function sidePlayer(ctrl: Ctrl, side: Side) {
  const playerIndex = sidePlayerIndex(ctrl, side);
  return [ctrl.data.player, ctrl.data.opponent].find(p => p.playerIndex === playerIndex);
}

function sideName(ctrl: Ctrl, side: Side): string {
  const playerName = side === 'winner' ? ctrl.data.game.winnerPlayer : ctrl.data.game.loserPlayer;
  if (!playerName) return '';
  const player = sidePlayer(ctrl, side),
    name = player?.user
      ? player.user.username
      : player?.ai
        ? ctrl.trans('aiNameLevelAiLevel', 'Stockfish', player.ai)
        : player?.name;
  return name || playerName;
}

function sideNode<T>(ctrl: Ctrl, side: Side, userLink: (username: string) => T): string | T {
  const name = sideName(ctrl, side),
    user = name ? sidePlayer(ctrl, side)?.user : undefined;
  return user ? userLink(user.username) : name;
}

export function winnerName(ctrl: Ctrl): string {
  return sideName(ctrl, 'winner');
}

export function loserName(ctrl: Ctrl): string {
  return sideName(ctrl, 'loser');
}

export default function status(ctrl: Ctrl): string {
  const description = describe(ctrl);
  return typeof description === 'string'
    ? description
    : ctrl.trans(description.key, sideName(ctrl, description.side));
}

export function statusNodes<T>(ctrl: Ctrl, userLink: (username: string) => T): Array<string | T> {
  const description = describe(ctrl);
  return typeof description === 'string'
    ? [description]
    : ctrl.trans.vdom(description.key, sideNode(ctrl, description.side, userLink));
}

export function victoriousNodes<T>(ctrl: Ctrl, userLink: (username: string) => T): Array<string | T> | undefined {
  return winnerName(ctrl)
    ? ctrl.trans.vdom('playerIndexIsVictorious', sideNode(ctrl, 'winner', userLink))
    : undefined;
}

function describe(ctrl: Ctrl): Description {
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
      return { key: 'playerIndexResigned', side: 'loser' };
    case 'resignGammon':
      return { key: 'playerIndexResignedGammon', side: 'loser' };
    case 'resignBackgammon':
      return { key: 'playerIndexResignedBackgammon', side: 'loser' };
    case 'resignMatch':
      return { key: 'playerIndexResignedMatch', side: 'loser' };
    case 'cubeDropped':
      return { key: 'playerIndexDroppedTheCube', side: 'loser' };
    case 'stalemate':
      return noarg('stalemate');
    case 'timeout':
      return d.game.loserPlayer ? { key: 'playerIndexLeftTheGame', side: 'loser' } : noarg('draw');
    case 'draw':
      return noarg('draw');
    case 'outoftime':
      //return `${d.game.turns % 2 === 0 ? noarg('whiteTimeOut') : noarg('blackTimeOut')}${
      //  d.game.winner ? '' : ` • ${noarg('draw')}`
      //}`;
      return d.game.loserPlayer
        ? { key: 'playerIndexTimeOut', side: 'loser' }
        : `${ctrl.trans('playerIndexTimeOut', '')} • ${noarg('draw')}`;
    case 'outoftimeGammon':
      return { key: 'playerIndexLosesByGammonTimeOut', side: 'loser' };
    case 'outoftimeBackgammon':
      return { key: 'playerIndexLosesByBackgammonTimeOut', side: 'loser' };
    case 'ruleOfGin':
      return { key: 'playerIndexWinsByRuleOfGin', side: 'winner' };
    case 'ginGammon':
      return { key: 'playerIndexWinsByGinGammon', side: 'winner' };
    case 'ginBackgammon':
      return { key: 'playerIndexWinsByGinBackgammon', side: 'winner' };
    case 'noStart':
      return { key: 'playerIndexDidntMove', side: 'loser' };
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
    case 'lineOfFive':
      return { key: 'playerIndexMadeFiveInARow', side: 'winner' };
    case 'tenCaptures':
      return { key: 'playerIndexCapturedTenStones', side: 'winner' };
    case 'forbiddenMove':
      return { key: 'playerIndexMadeAForbiddenMove', side: 'loser' };
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
