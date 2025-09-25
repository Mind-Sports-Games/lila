import { piotr } from './piotr';
import * as status from 'game/status';
import type * as cg from 'chessground/types';
import type { BaseGame } from 'game';
import { variantClassFromKey } from 'stratops/variants/util';

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

export const uci2move = (uci: string): cg.Key[] | undefined => {
  if (
    !uci ||
    uci == 'pass' ||
    uci == 'roll' ||
    uci == 'endturn' ||
    uci == 'undo' ||
    uci.includes('/') ||
    uci.substring(0, 3) == 'ss:' ||
    uci.substring(0, 4) == 'cube'
  )
    return undefined;
  const pos = uci.match(/[a-z][1-9][0-9]?/g) as cg.Key[];
  if (uci[1] === '@' || uci[0] === '@') return [pos[0], pos[0]] as cg.Key[];
  return [pos[0], pos[1]] as cg.Key[];
};

export function lastMove(onlyDropsVariant: boolean, uci: string): cg.Key[] | undefined {
  if (onlyDropsVariant) {
    if (uci && (uci[1] === '@' || uci[0] === '@')) {
      return uci2move(uci);
    } else {
      return undefined;
    }
  } else {
    return uci2move(uci);
  }
}

// 3 check and 5 check dont have consistent fen formats, its calculated from running through game plys.
export function getScore(variant: VariantKey, fen: string, playerIndex: string): number | undefined {
  return variantClassFromKey(variant).getScoreFromFen(fen, playerIndex);
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

interface Piece {
  role: cg.Role;
  playerIndex: PlayerIndex;
  promoted?: boolean;
}

export function onlyDropsVariantPiece(variant: VariantKey, turnPlayerIndex: 'p1' | 'p2'): Piece | undefined {
  switch (variant) {
    case 'flipello':
    case 'flipello10':
    case 'antiflipello':
    case 'octagonflipello':
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
  'antiflipello',
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
