import piotr from './piotr';
import * as cg from 'draughtsground/types';

export const san2algMap: { [key: string]: string } = {
  '1': 'b8',
  '2': 'd8',
  '3': 'f8',
  '4': 'h8',
  '5': 'a7',
  '6': 'c7',
  '7': 'e7',
  '8': 'g7',
  '9': 'b6',
  '10': 'd6',
  '11': 'f6',
  '12': 'h6',
  '13': 'a5',
  '14': 'c5',
  '15': 'e5',
  '16': 'g5',
  '17': 'b4',
  '18': 'd4',
  '19': 'f4',
  '20': 'h4',
  '21': 'a3',
  '22': 'c3',
  '23': 'e3',
  '24': 'g3',
  '25': 'b2',
  '26': 'd2',
  '27': 'f2',
  '28': 'h2',
  '29': 'a1',
  '30': 'c1',
  '31': 'e1',
  '32': 'g1',
};

export const initialFen: Fen =
  'W:W31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50:B1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20:H0:F1';

export function decomposeUci(uci: Uci): cg.Key[] {
  const ucis: cg.Key[] = [];
  if (uci.length > 1) {
    for (let i = 0; i < uci.length; i += 2) {
      ucis.push(uci.substr(i, 2) as cg.Key);
    }
  }
  return ucis;
}

export function fenFromTag(tag: string) {
  if (!tag || !tag.startsWith('[') || !tag.endsWith(']') || !tag.includes('FEN')) {
    return tag;
  }
  const fenStart = tag.indexOf('"'),
    fenEnd = tag.lastIndexOf('"');
  if (fenStart === -1 || fenEnd === -1 || fenStart === fenEnd) {
    return tag;
  }
  return tag.slice(fenStart + 1, fenEnd);
}

export function san2alg(san?: string): string | undefined {
  if (!san) return undefined;
  const capture = san.indexOf('x'),
    split = capture === -1 ? san.indexOf('-') : capture;
  return san2algMap[san.slice(0, split)] + (capture === -1 ? '-' : ':') + san2algMap[san.slice(split + 1)];
}

function invertCoord(coord: string): string {
  return (33 - +coord).toString();
}

export function invertSan(san: string): string {
  const capture = san.indexOf('x'),
    split = capture === -1 ? san.indexOf('-') : capture;
  return invertCoord(san.slice(0, split)) + (capture === -1 ? '-' : 'x') + invertCoord(san.slice(split + 1));
}

export function renderEval(e: number): string {
  e = Math.max(Math.min(Math.round(e / 10) / 10, 99), -99);
  return (e > 0 ? '+' : '') + e;
}

export interface Dests {
  [square: string]: Key[];
}

export function fenCompare(fen1: string, fen2: string) {
  const fenParts1: string[] = fen1.split(':');
  const fenParts2: string[] = fen2.split(':');
  if (fenParts1.length < 3 || fenParts2.length < 3) return false;
  for (let i = 0; i < 3; i++) {
    if (fenParts1[i] !== fenParts2[i]) return false;
  }
  return true;
}

export function readDests(lines?: string): Dests | null {
  if (typeof lines === 'undefined') return null;
  const dests: Dests = {};
  if (lines)
    lines.split(' ').forEach(line => {
      if (line[0] !== '#')
        dests[piotr[line[0]]] = line
          .slice(1)
          .split('')
          .map(c => piotr[c] as Key);
    });
  return dests;
}

export function readCaptureLength(lines?: string): number {
  if (lines) {
    const lineSplit = lines.split(' ');
    if (lineSplit.length && lineSplit[0][0] === '#') {
      const captLen = parseInt(lineSplit[0].slice(1));
      if (!isNaN(captLen)) return captLen;
    }
  }
  return 0;
}

export function readDrops(line?: string | null): string[] | null {
  if (typeof line === 'undefined' || line === null) return null;
  return line.match(/.{2}/g) || [];
}

export { piotr };
