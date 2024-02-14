import * as cg from 'chessground/types';
import { h, Hooks, VNodeData } from 'snabbdom';
import { opposite, calculatePieceGroup, key2pos } from 'chessground/util';
import { Redraw, EncodedDests, Dests, MaterialDiff, Step, CheckCount } from './interfaces';

function pieceScores(variant: VariantKey, piece: cg.Role, isPromoted: boolean | undefined): number {
  switch (variant) {
    case 'xiangqi':
      switch (piece) {
        case 'p-piece':
          return isPromoted ? 2 : 1;
        case 'a-piece':
          return 2;
        case 'b-piece':
          return 2;
        case 'n-piece':
          return 4;
        case 'c-piece':
          return 4.5;
        case 'r-piece':
          return 9;
        case 'k-piece':
          return 0;
        default:
          return 0;
      }
    default:
      switch (piece) {
        case 'p-piece':
          return 1;
        case 'n-piece':
          return 3;
        case 'b-piece':
          return 3;
        case 'r-piece':
          return 5;
        case 'q-piece':
          return 9;
        case 'k-piece':
          return 0;
        case 'l-piece':
          return 0;
        default:
          return 0;
      }
  }
}

export const justIcon = (icon: string): VNodeData => ({
  attrs: { 'data-icon': icon },
});

// TODO: this is duplicated in ui/analyse/src/util.ts
export const uci2move = (uci: string): cg.Key[] | undefined => {
  if (!uci || uci == 'pass' || uci == 'roll' || uci == 'endturn' || uci.includes('/') || uci.substring(0, 3) == 'ss:')
    return undefined;
  const pos = uci.match(/[a-z][1-9][0-9]?/g) as cg.Key[];
  if (uci[1] === '@') return [pos[0], pos[0]] as cg.Key[];
  return [pos[0], pos[1]] as cg.Key[];
};

export const onInsert = (f: (el: HTMLElement) => void): Hooks => ({
  insert(vnode) {
    f(vnode.elm as HTMLElement);
  },
});

export const bind = (eventName: string, f: (e: Event) => void, redraw?: Redraw, passive = true): Hooks =>
  onInsert(el => {
    el.addEventListener(
      eventName,
      e => {
        f(e);
        redraw && redraw();
      },
      { passive }
    );
  });

export function parsePossibleMoves(dests?: EncodedDests, activeDiceValue?: number): Dests {
  const dec = new Map();
  if (!dests) return dec;
  if (typeof dests == 'string')
    for (const ds of dests.split(' ')) {
      const pos = ds.match(/[a-z][1-9][0-9]?/g) as cg.Key[];
      dec.set(pos[0], pos.slice(1));
    }
  else for (const k in dests) dec.set(k, dests[k].match(/[a-z][1-9][0-9]?/g) as cg.Key[]);

  //filter backgammon moves based on active dice value
  if (activeDiceValue) {
    const filtered = new Map();
    dec.forEach((value: cg.Key[], key: cg.Key) => {
      for (const v of value) {
        if (backgammonPosDiff(key, v) === activeDiceValue) {
          filtered.set(key, [v]);
        }
      }
    });
    return filtered;
  }

  return dec;
}

export function parsePossibleLifts(line?: string | null, activeDiceValue?: number): cg.Key[] {
  if (typeof line === 'undefined' || line === null) return [];
  const pos = (line.match(/[a-z][1-9][0-9]?/g) as cg.Key[]) || [];

  //filter backgammon lifts based on active dice value
  if (activeDiceValue) {
    const comparisonSquare = line.includes('1') ? 'm1' : 'm2';
    const posDiff = pos.map(p => backgammonPosDiff(p, comparisonSquare));
    return pos.filter(
      p =>
        backgammonPosDiff(p, comparisonSquare) === activeDiceValue ||
        (pos.length === 1 && activeDiceValue > backgammonPosDiff(p, comparisonSquare)) ||
        (pos.length === 2 &&
          activeDiceValue > backgammonPosDiff(p, comparisonSquare) &&
          backgammonPosDiff(p, comparisonSquare) === Math.max(...posDiff))
    );
  }
  return pos;
}

function backgammonPosDiff(orig: cg.Key, dest: cg.Key): number {
  const origFile = key2pos(orig)[0];
  const origRank = key2pos(orig)[1];
  const destFile = key2pos(dest)[0];
  const destRank = key2pos(dest)[1];
  if (origRank === destRank) {
    return Math.abs(origFile - destFile);
  } else {
    return origFile + destFile - 1;
  }
}

// {p1: {'p-piece': 3 'q-piece': 1}, p2: {'b-piece': 2}}
export function getMaterialDiff(pieces: cg.Pieces): MaterialDiff {
  const diff: MaterialDiff = {
    p1: {
      'a-piece': 0,
      'b-piece': 0,
      'c-piece': 0,
      'k-piece': 0,
      'l-piece': 0,
      'n-piece': 0,
      'p-piece': 0,
      'q-piece': 0,
      'r-piece': 0,
    },
    p2: {
      'a-piece': 0,
      'b-piece': 0,
      'c-piece': 0,
      'k-piece': 0,
      'l-piece': 0,
      'n-piece': 0,
      'p-piece': 0,
      'q-piece': 0,
      'r-piece': 0,
    },
  };
  for (const p of pieces.values()) {
    const them = diff[opposite(p.playerIndex)];
    if (them[p.role] > 0) them[p.role]--;
    else diff[p.playerIndex][p.role]++;
  }
  return diff;
}

export function getScore(variant: VariantKey, pieces: cg.Pieces): number {
  let score = 0;
  for (const p of pieces.values()) {
    score += pieceScores(variant, p.role, p.promoted) * (p.playerIndex === 'p1' ? 1 : -1);
  }
  return score;
}

export function getPlayerScore(variant: VariantKey, pieces: cg.Pieces, playerIndex: string): number {
  let score = 0;
  for (const p of pieces.values()) {
    score += pieceScores(variant, p.role, p.promoted) * (p.playerIndex === playerIndex ? 1 : 0);
  }
  return score;
}

export function getMancalaScore(fen: string, playerIndex: string): number {
  return +fen.split(' ')[playerIndex === 'p1' ? 1 : 2];
}

export function getGoScore(fen: string, playerIndex: string): number {
  return +fen.split(' ')[playerIndex === 'p1' ? 3 : 4] / 10.0;
}

export function getGoCaptures(fen: string, playerIndex: string): number {
  return +fen.split(' ')[playerIndex === 'p1' ? 5 : 6];
}

export function getGoKomi(fen: string): number {
  return +fen.split(' ')[7] / 10.0;
}

export function getBackgammonScoreFromFen(fen: string, playerIndex: string): number {
  return +fen.split(' ')[playerIndex === 'p1' ? 4 : 5];
}

export function getBackgammonScoreFromPieces(pieces: cg.Pieces, pocketPieces: cg.Piece[], playerIndex: string): number {
  let score = 0;
  for (const p of pieces.values()) {
    score += +p.role.split('-')[0].substring(1) * (p.playerIndex === playerIndex ? 1 : 0);
  }
  for (const p of pocketPieces) {
    score += +p.role.split('-')[0].substring(1) * (p.playerIndex === playerIndex ? 1 : 0);
  }
  return score;
}

export function goStonesToSelect(deadstones: cg.Key[], pieces: cg.Pieces, bd: cg.BoardDimensions): cg.Key[] {
  const stonesToSelect: cg.Key[] = [];
  if (deadstones.length > 0) {
    const pieceGroups: cg.Key[][] = deadstones.map(s => calculatePieceGroup(s, pieces, bd).sort());
    for (const group of pieceGroups) {
      if (!stonesToSelect.includes(group[0])) stonesToSelect.push(group[0]);
    }
  }

  return stonesToSelect;
}

export const noChecks: CheckCount = {
  p1: 0,
  p2: 0,
};

export function countChecks(steps: Step[], ply: Ply): CheckCount {
  const checks: CheckCount = { ...noChecks };
  for (const step of steps) {
    if (ply < step.ply) break;
    if (step.check) {
      if (step.turnCount % 2 === 1) checks.p1++;
      else checks.p2++;
    }
  }
  return checks;
}

export const spinner = () =>
  h(
    'div.spinner',
    {
      'aria-label': 'loading',
    },
    [
      h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
        h('circle', {
          attrs: { cx: 20, cy: 20, r: 18, fill: 'none' },
        }),
      ]),
    ]
  );

const noAnalysisBoardVariants: VariantKey[] = [
  'monster',
  'amazons',
  'go9x9',
  'go13x13',
  'go19x19',
  'backgammon',
  'nackgammon',
];

export function allowAnalysisForVariant(variant: VariantKey) {
  return noAnalysisBoardVariants.indexOf(variant) == -1;
}

export function lastMove(onlyDropsVariant: boolean, uci: string): cg.Key[] | undefined {
  if (onlyDropsVariant) {
    if (uci && uci[1] === '@') {
      return uci2move(uci);
    } else {
      return undefined;
    }
  } else {
    return uci2move(uci);
  }
}

export function turnPlayerIndexFromLastTurn(turn: number): PlayerIndex {
  return turn % 2 === 0 ? 'p1' : 'p2';
}
