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

export const uci2move = (uci: string): cg.Key[] | undefined => {
  if (!uci) return undefined;
  if (uci[1] === '@') return [uci.slice(2, 4) as cg.Key];
  return [uci.slice(0, 2), uci.slice(2, 4)] as cg.Key[];
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
      dec.set(ds.slice(0, 2), ds.slice(2).match(/.{2}/g) as cg.Key[]);
    }
  else for (const k in dests) dec.set(k, dests[k].match(/.{2}/g) as cg.Key[]);
  return dec;
}

// {white: {'p-piece': 3 'q-piece': 1}, black: {'b-piece': 2}}
export function getMaterialDiff(pieces: cg.Pieces): MaterialDiff {
  const diff: MaterialDiff = {
    white: {
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
    black: {
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
    const them = diff[opposite(p.color)];
    if (them[p.role] > 0) them[p.role]--;
    else diff[p.color][p.role]++;
  }
  return diff;
}

export function getScore(variant: VariantKey, pieces: cg.Pieces): number {
  let score = 0;
  for (const p of pieces.values()) {
    score += pieceScores(variant, p.role, p.promoted) * (p.color === 'white' ? 1 : -1);
  }
  return score;
}

export const noChecks: CheckCount = {
  white: 0,
  black: 0,
};

export function countChecks(steps: Step[], ply: Ply): CheckCount {
  const checks: CheckCount = { ...noChecks };
  for (const step of steps) {
    if (ply < step.ply) break;
    if (step.check) {
      if (step.ply % 2 === 1) checks.white++;
      else checks.black++;
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

const noAnalysisVariants = ['linesOfAction'];

export function allowAnalysisForVariant(variant: VariantKey) {
  return noAnalysisVariants.indexOf(variant) == -1;
}
