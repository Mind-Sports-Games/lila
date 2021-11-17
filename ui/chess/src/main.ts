import { piotr } from './piotr';

export const initialFen: Fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

export function fixCrazySan(san: San): San {
  return san[0] === 'P' ? san.slice(1) : san;
}

export type Dests = Map<Key, Key[]>;

export type NotationStyle = 'uci' | 'san' | 'usi' | 'wxf';

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
  return (line.match(/.{2}/g) as Key[]) || [];
}

export const altCastles = {
  e1a1: 'e1c1',
  e1h1: 'e1g1',
  e8a8: 'e8c8',
  e8h8: 'e8g8',
};

export function variantUsesUCINotation(key: VariantKey | DraughtsVariantKey) {
  return ['linesOfAction'].includes(key);
}

export function variantUsesUSINotation(key: VariantKey | DraughtsVariantKey) {
  return ['shogi'].includes(key);
}

export function variantUsesWXFNotation(key: VariantKey | DraughtsVariantKey) {
  return ['xiangqi'].includes(key);
}

export function notationStyle(key: VariantKey | DraughtsVariantKey): NotationStyle {
  return variantUsesUCINotation(key)
    ? 'uci'
    : variantUsesUSINotation(key)
    ? 'usi'
    : variantUsesWXFNotation(key)
    ? 'wxf'
    : 'san';
}
