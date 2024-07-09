import * as cg from 'draughtsground/types';
import { h, Hooks, VNodeData } from 'snabbdom';
import { opposite } from 'draughtsground/util';
import { Redraw, EncodedDests, DecodedDests } from './interfaces';
import { decomposeUci } from 'draughts';

const pieceScores = {
  man: 1,
  king: 2,
  ghostman: 0,
  ghostking: 0,
};

export const justIcon = (icon: string): VNodeData => ({
  attrs: { 'data-icon': icon },
});

export const uci2move = (uci: string): cg.Key[] | undefined => {
  if (!uci) return undefined;
  return decomposeUci(uci);
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
      { passive },
    );
  });

export function parsePossibleMoves(dests?: EncodedDests): DecodedDests {
  const dec = new Map();
  if (!dests) return dec;
  if (typeof dests == 'string')
    for (const ds of dests.split(' ')) {
      dec.set(ds.slice(0, 2), ds.slice(2).match(/.{2}/g) as cg.Key[]);
    }
  else for (const k in dests) dec.set(k, dests[k].match(/.{2}/g) as cg.Key[]);
  return dec;
}

// {p1: {man: 3}, p2: {king: 1}}
export function getMaterialDiff(pieces: cg.Pieces): cg.MaterialDiff {
  const diff: cg.MaterialDiff = {
    p1: { king: 0, man: 0 },
    p2: { king: 0, man: 0 },
  };
  for (const p of pieces.values()) {
    if (p.role != 'ghostman' && p.role != 'ghostking') {
      const them = diff[opposite(p.playerIndex)];
      if (them[p.role] > 0) them[p.role]--;
      else diff[p.playerIndex][p.role]++;
    }
  }
  return diff;
}

export function getScore(pieces: cg.Pieces): number {
  let score = 0;
  for (const p of pieces.values()) {
    score += pieceScores[p.role] * (p.playerIndex === 'p1' ? 1 : -1);
  }
  return score;
}

export function spinner() {
  return h(
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
    ],
  );
}

const noAnalysisVariants = [
  'international',
  'antidraughts',
  'breakthrough',
  'russian',
  'brazilian',
  'pool',
  'portuguese',
  'english',
  'fromPositionDraughts',
  'frisian',
  'frysk',
];

export function allowAnalysisForVariant(variant: DraughtsVariantKey) {
  return noAnalysisVariants.indexOf(variant) == -1;
}
