import { piotr } from './piotr';
import * as cg from 'chessground/types';
import { Rules } from 'stratops/types';
import * as status from 'game/status';
import type { BaseGame } from 'game';

// TODO: For some reason we can't import this like:
// import * from 'stratutils/promotion'
// you have to use
// import { promotion } from 'stratutils'
export * as promotion from './promotion';

export const initialFen: Fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

export function fixCrazySan(san: San): San {
  return san[0] === 'P' ? san.slice(1) : san;
}

export type Dests = Map<Key, Key[]>;

export type NotationStyle = 'uci' | 'san' | 'usi' | 'wxf' | 'dpo' | 'dpg' | 'man' | 'bkg' | 'abl';

export function readDests(lines?: string): Dests | null {
  if (typeof lines === 'undefined') return null;
  const dests = new Map();
  if (lines)
    for (const line of lines.split(' ')) {
      dests.set(
        piotr[line[0]],
        line
          .slice(1)
          .split('')
          .map(c => piotr[c]),
      );
    }
  return dests;
}

export function readDrops(line?: string | null): Key[] | null {
  if (typeof line === 'undefined' || line === null) return null;
  return (line.match(/[a-z][1-9][0-9]?/g) as Key[]) || [];
}

export function readDropsByRole(line?: string | null): Map<cg.Role, Key[]> {
  if (typeof line === 'undefined' || line === null) return new Map();
  const roledrops = new Map();
  line
    .split(' ')
    .forEach(d => roledrops.set(d[0].toLowerCase() + '-piece', d.slice(1).match(/[a-z][1-9][0-9]?/g) as Key[]));
  return roledrops;
}

export const altCastles = {
  e1a1: 'e1c1',
  e1h1: 'e1g1',
  e8a8: 'e8c8',
  e8h8: 'e8g8',
};

// 3 check and 5 check dont have consistent fen formats, its calculated from running through game plys.
export function getScore(variant: VariantKey, fen: string, playerIndex: string): number | undefined {
  switch (variant) {
    case 'oware':
    case 'togyzkumalak':
    case 'bestemshe':
    case 'abalone':
      return +fen.split(' ')[playerIndex === 'p1' ? 1 : 2];
    case 'go9x9':
    case 'go13x13':
    case 'go19x19':
      return +fen.split(' ')[playerIndex === 'p1' ? 3 : 4] / 10.0;
    case 'backgammon':
    case 'hyper':
    case 'nackgammon':
      return +fen.split(' ')[playerIndex === 'p1' ? 4 : 5];
    case 'flipello10':
    case 'flipello': {
      const boardPart = fen.split(' ')[0].split('[')[0];
      return boardPart.split(playerIndex === 'p1' ? 'P' : 'p').length - 1;
    }
    case 'threeCheck':
    case 'fiveCheck':
      return +fen.split(' ')[6][playerIndex === 'p1' ? 1 : 3];
    default:
      return undefined;
  }
}

export function displayScore(variant: VariantKey, fen: string, playerIndex: string): string {
  const score: undefined | number = getScore(variant, fen, playerIndex);
  if (score !== undefined) return `(${score})`;
  else return '';
}

export function fenPlayerIndex(variant: VariantKey, fen: string) {
  //different trick for Go due to fen structure
  if (['go9x9', 'go13x13', 'go19x19'].includes(variant)) {
    return fen.split(' ')[1] === 'b' ? 'p1' : 'p2';
  }
  if (['abalone'].includes(variant)) {
    return fen.split(' ')[3] === 'b' ? 'p1' : 'p2';
  }
  const p2String = variant === 'oware' ? ' N' : ' b';
  return fen.indexOf(p2String) > 0 ? 'p2' : 'p1';
}

export function variantUsesUCINotation(key: VariantKey | DraughtsVariantKey) {
  return ['linesOfAction', 'scrambledEggs', 'amazons', 'breakthroughtroyka', 'minibreakthroughtroyka'].includes(key);
}

export function variantUsesUSINotation(key: VariantKey | DraughtsVariantKey) {
  return ['shogi', 'minishogi'].includes(key);
}

export function variantUsesWXFNotation(key: VariantKey | DraughtsVariantKey) {
  return ['xiangqi', 'minixiangqi'].includes(key);
}

export function variantUsesDestPosOthelloNotation(key: VariantKey | DraughtsVariantKey) {
  return ['flipello', 'flipello10'].includes(key);
}

export function variantUsesDestPosGoNotation(key: VariantKey | DraughtsVariantKey) {
  return ['go9x9', 'go13x13', 'go19x19'].includes(key);
}

export function variantUsesMancalaNotation(key: VariantKey | DraughtsVariantKey) {
  return ['oware', 'togyzkumalak', 'bestemshe'].includes(key);
}

export function variantUsesBackgammonNotation(key: VariantKey | DraughtsVariantKey) {
  return ['backgammon', 'hyper', 'nackgammon'].includes(key);
}

export function variantUsesAbaloneNotation(key: VariantKey | DraughtsVariantKey) {
  return ['abalone'].includes(key);
}

export function notationStyle(key: VariantKey | DraughtsVariantKey): NotationStyle {
  return variantUsesUCINotation(key)
    ? 'uci'
    : variantUsesUSINotation(key)
      ? 'usi'
      : variantUsesWXFNotation(key)
        ? 'wxf'
        : variantUsesDestPosOthelloNotation(key)
          ? 'dpo'
          : variantUsesDestPosGoNotation(key)
            ? 'dpg'
            : variantUsesMancalaNotation(key)
              ? 'man'
              : variantUsesBackgammonNotation(key)
                ? 'bkg'
                : variantUsesAbaloneNotation(key)
                  ? 'abl'
                  : 'san';
}

interface Piece {
  role: cg.Role;
  playerIndex: PlayerIndex;
  promoted?: boolean;
}

export function onlyDropsVariantPiece(variant: VariantKey, turnPlayerIndex: 'p1' | 'p2'): Piece | undefined {
  switch (variant) {
    case 'flipello10':
    case 'flipello':
    case 'amazons':
      return { playerIndex: turnPlayerIndex, role: 'p-piece' };
    case 'go9x9':
    case 'go13x13':
    case 'go19x19':
      return { playerIndex: turnPlayerIndex, role: 's-piece' };
    case 'nackgammon':
    case 'hyper':
    case 'backgammon':
      return { playerIndex: turnPlayerIndex, role: 's-piece' }; //needs to match role from readdropsbyrole and SG role
    default:
      return undefined;
  }
}

const noFishnetVariants: VariantKey[] = [
  'linesOfAction',
  'scrambledEggs',
  'oware',
  'togyzkumalak',
  'bestemshe',
  'go9x9',
  'go13x13',
  'go19x19',
  'backgammon',
  'hyper',
  'nackgammon',
  'abalone',
];
export function allowFishnetForVariant(variant: VariantKey) {
  return noFishnetVariants.indexOf(variant) == -1;
}

export function readDoublingCube(fen: string, variant: VariantKey): cg.DoublingCube | undefined {
  if (!['backgammon', 'hyper', 'nackgammon'].includes(variant)) return undefined;
  if (fen.split(' ').length < 8) return undefined;
  const doublingCube = fen.split(' ')[6];
  if (doublingCube === '-') return undefined;
  if (doublingCube === '0') return { value: 0, owner: 'both' };
  if (doublingCube.length == 2 || doublingCube.length == 3) {
    if (doublingCube[1] === 'w' || doublingCube[1] === 'b') return { value: +doublingCube[0] + 1, owner: 'both' };
    else return { value: +doublingCube[0], owner: doublingCube[1] === 'W' ? 'p1' : 'p2' };
  }
  return undefined; //shouldn't get here...
}

export function readDice(fen: string, variant: VariantKey, canEndTurn?: boolean, isDescending?: boolean): cg.Dice[] {
  if (!['backgammon', 'hyper', 'nackgammon'].includes(variant)) return [];
  if (fen.split(' ').length < 2) return [];
  const unusedDice = fen
    .split(' ')[1]
    .replace('-', '')
    .split('/')
    .sort((a, b) => +b - +a);
  const usedDice = fen.split(' ')[2].replace('-', '').split('/');
  const dice = [];
  for (const d of unusedDice) {
    if (+d) dice.push({ value: +d, isAvailable: !(canEndTurn ?? false) });
  }
  for (const d of usedDice) {
    if (+d) dice.push({ value: +d, isAvailable: false });
  }
  if (isDescending !== undefined) {
    if (isDescending) return dice.sort((a, b) => +b.value - +a.value);
    else return dice.sort((a, b) => +a.value - +b.value);
  } else return dice.sort((a, b) => +b.value - +a.value);
}

export const finalMultiPointState = (game: BaseGame, ply: any, lastPly: any) => {
  const pointsToAdd =
    game.pointValue && game.winner && ply == lastPly
      ? game.winner === 'p1'
        ? [game.pointValue, 0]
        : [0, game.pointValue]
      : [0, 0];

  if (status.isOutOfTime(game.status.id) && game.winner && ply == lastPly) {
    if (status.isGin(game.status.id)) {
      if (game.winner === 'p1') {
        if (game.multiPointState.p1 + pointsToAdd[0] < game.multiPointState.target) {
          pointsToAdd[1] += 64;
        }
      } else {
        if (game.multiPointState.p2 + pointsToAdd[1] < game.multiPointState.target) {
          pointsToAdd[0] += 64;
        }
      }
    } else {
      //normal timeout
      if (game.winner === 'p1') {
        pointsToAdd[0] += 64;
      } else {
        pointsToAdd[1] += 64;
      }
    }
  }

  return game.multiPointState
    ? {
        target: game.multiPointState.target,
        p1: Math.min(game.multiPointState.target, game.multiPointState.p1 + pointsToAdd[0]),
        p2: Math.min(game.multiPointState.target, game.multiPointState.p2 + pointsToAdd[1]),
      }
    : undefined;
};

export const variantToRules = (v: VariantKey): Rules => {
  switch (v) {
    case 'standard':
      return 'chess';
    case 'chess960':
      return 'chess';
    case 'antichess':
      return 'antichess';
    case 'fromPosition':
      return 'chess';
    case 'kingOfTheHill':
      return 'kingofthehill';
    case 'threeCheck':
      return '3check';
    case 'fiveCheck':
      return '5check';
    case 'atomic':
      return 'atomic';
    case 'horde':
      return 'horde';
    case 'racingKings':
      return 'racingkings';
    case 'crazyhouse':
      return 'crazyhouse';
    case 'noCastling':
      return 'nocastling';
    case 'linesOfAction':
      return 'linesofaction';
    case 'scrambledEggs':
      return 'scrambledeggs';
    case 'shogi':
      return 'shogi';
    case 'xiangqi':
      return 'xiangqi';
    case 'minishogi':
      return 'minishogi';
    case 'minixiangqi':
      return 'minixiangqi';
    case 'flipello':
      return 'flipello';
    case 'flipello10':
      return 'flipello10';
    case 'amazons':
      return 'amazons';
    case 'breakthroughtroyka':
      return 'breakthrough';
    case 'minibreakthroughtroyka':
      return 'minibreakthrough';
    case 'oware':
      return 'oware';
    case 'togyzkumalak':
      return 'togyzkumalak';
    case 'bestemshe':
      return 'bestemshe';
    case 'go9x9':
      return 'go9x9';
    case 'go13x13':
      return 'go13x13';
    case 'go19x19':
      return 'go19x19';
    case 'backgammon':
      return 'backgammon';
    case 'hyper':
      return 'hyper';
    case 'nackgammon':
      return 'nackgammon';
    case 'abalone':
      return 'abalone';
    default:
      return 'chess';
  }
};
