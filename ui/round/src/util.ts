import * as cg from 'chessground/types';
import { h, Hooks, VNodeData } from 'snabbdom';
import { opposite } from 'chessground/util';
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
  if (!uci) return undefined;
  const pos = uci.match(/[a-z][1-9]0?/g) as cg.Key[];
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

export function parsePossibleMoves(dests?: EncodedDests): Dests {
  const dec = new Map();
  if (!dests) return dec;
  if (typeof dests == 'string')
    for (const ds of dests.split(' ')) {
      const pos = ds.match(/[a-z][1-9]0?/g) as cg.Key[];
      dec.set(pos[0], pos.slice(1));
    }
  else for (const k in dests) dec.set(k, dests[k].match(/[a-z][1-9]0?/g) as cg.Key[]);
  return dec;
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

export const noChecks: CheckCount = {
  p1: 0,
  p2: 0,
};

export function countChecks(steps: Step[], ply: Ply): CheckCount {
  const checks: CheckCount = { ...noChecks };
  for (const step of steps) {
    if (ply < step.ply) break;
    if (step.check) {
      if (step.ply % 2 === 1) checks.p1++;
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

const noAnalysisBoardVariants: VariantKey[] = [];

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

export function turnPlayerIndexFromLastPly(ply: Ply, variantKey: VariantKey): PlayerIndex {
  if (variantKey === 'amazons') {
    return Math.floor(ply / 2) % 2 === 0 ? 'p1' : 'p2';
  } else {
    return ply % 2 === 0 ? 'p1' : 'p2';
  }
}
