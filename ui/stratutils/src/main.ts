import { piotr } from './piotr';
import * as cg from 'chessground/types';
import { Rules } from 'stratops/types';

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

export type NotationStyle = 'uci' | 'san' | 'usi' | 'wxf' | 'dpo' | 'man';

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
          .map(c => piotr[c])
      );
    }
  return dests;
}

export function readDrops(line?: string | null): Key[] | null {
  if (typeof line === 'undefined' || line === null) return null;
  return (line.match(/[a-z][1-9]0?/g) as Key[]) || [];
}

export function readDropsByRole(line?: string | null): Map<cg.Role, Key[]> {
  if (typeof line === 'undefined' || line === null) return new Map();
  const roledrops = new Map();
  line
    .split(' ')
    .forEach(d => roledrops.set(d[0].toLowerCase() + '-piece', d.slice(1).match(/[a-z][1-9]0?/g) as Key[]));
  return roledrops;
}

export const altCastles = {
  e1a1: 'e1c1',
  e1h1: 'e1g1',
  e8a8: 'e8c8',
  e8h8: 'e8g8',
};

export function variantUsesUCINotation(key: VariantKey | DraughtsVariantKey) {
  return ['linesOfAction', 'scrambledEggs', 'amazons'].includes(key);
}

export function variantUsesUSINotation(key: VariantKey | DraughtsVariantKey) {
  return ['shogi', 'minishogi'].includes(key);
}

export function variantUsesWXFNotation(key: VariantKey | DraughtsVariantKey) {
  return ['xiangqi', 'minixiangqi'].includes(key);
}

export function variantUsesDestPosOnlyNotation(key: VariantKey | DraughtsVariantKey) {
  return ['flipello', 'flipello10'].includes(key);
}

export function variantUsesMancalaNotation(key: VariantKey | DraughtsVariantKey) {
  return ['oware', 'togyzkumalak'].includes(key);
}

export function notationStyle(key: VariantKey | DraughtsVariantKey): NotationStyle {
  return variantUsesUCINotation(key)
    ? 'uci'
    : variantUsesUSINotation(key)
      ? 'usi'
      : variantUsesWXFNotation(key)
        ? 'wxf'
        : variantUsesDestPosOnlyNotation(key)
          ? 'dpo'
          : variantUsesMancalaNotation(key)
            ? 'man'
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
    default:
      return undefined;
  }
}

const noFishnetVariants: VariantKey[] = ['linesOfAction', 'scrambledEggs', 'oware', 'togyzkumalak'];
export function allowFishnetForVariant(variant: VariantKey) {
  return noFishnetVariants.indexOf(variant) == -1;
}

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
    case 'oware':
      return 'oware';
    case 'togyzkumalak':
      return 'togyzkumalak';
  }
};
