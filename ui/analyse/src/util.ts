import { h, VNode, Hooks, Attrs } from 'snabbdom';
import { fixCrazySan } from 'stratutils';
import * as cg from 'chessground/types';
import { Rules } from 'stratops/types';

export { autolink, innerHTML, enrichText, richHTML, toYouTubeEmbed, toTwitchEmbed } from 'common/richText';

export const emptyRedButton = 'button.button.button-red.button-empty';

const longPressDuration = 610; // used in bindMobileTapHold

export function clearSelection() {
  window.getSelection()?.removeAllRanges();
}

//TODO multiaction remove this function
export function plyPlayerIndex(ply: number, variantKey: VariantKey): PlayerIndex {
  if (variantKey === 'amazons') {
    return Math.floor(ply / 2) % 2 === 0 ? 'p1' : 'p2';
  } else {
    return ply % 2 === 0 ? 'p1' : 'p2';
  }
}

export function bindMobileMousedown(el: HTMLElement, f: (e: Event) => unknown, redraw?: () => void) {
  for (const mousedownEvent of ['touchstart', 'mousedown']) {
    el.addEventListener(mousedownEvent, e => {
      f(e);
      e.preventDefault();
      if (redraw) redraw();
    });
  }
}

export function bindMobileTapHold(el: HTMLElement, f: (e: Event) => unknown, redraw?: () => void) {
  let longPressCountdown: number;

  el.addEventListener('touchstart', e => {
    longPressCountdown = setTimeout(() => {
      f(e);
      if (redraw) redraw();
    }, longPressDuration);
  });

  el.addEventListener('touchmove', () => {
    clearTimeout(longPressCountdown);
  });

  el.addEventListener('touchcancel', () => {
    clearTimeout(longPressCountdown);
  });

  el.addEventListener('touchend', () => {
    clearTimeout(longPressCountdown);
  });
}

function listenTo(el: HTMLElement, eventName: string, f: (e: Event) => unknown, redraw?: () => void) {
  el.addEventListener(eventName, e => {
    const res = f(e);
    if (res === false) e.preventDefault();
    if (redraw) redraw();
    return res;
  });
}

export function bind(eventName: string, f: (e: Event) => unknown, redraw?: () => void): Hooks {
  return onInsert(el => listenTo(el, eventName, f, redraw));
}

export function bindSubmit(f: (e: Event) => unknown, redraw?: () => void): Hooks {
  return bind(
    'submit',
    e => {
      e.preventDefault();
      return f(e);
    },
    redraw
  );
}

export function onInsert<A extends HTMLElement>(f: (element: A) => void): Hooks {
  return {
    insert: vnode => f(vnode.elm as A),
  };
}

export function readOnlyProp<A>(value: A): () => A {
  return function (): A {
    return value;
  };
}

export function dataIcon(icon: string): Attrs {
  return {
    'data-icon': icon,
  };
}

export function iconTag(icon: string) {
  return h('i', { attrs: dataIcon(icon) });
}

export function plyToTurn(ply: number): number {
  return Math.floor((ply - 1) / 2) + 1;
}

export function nodeFullName(node: Tree.Node) {
  if (node.san) return plyToTurn(node.ply) + (node.ply % 2 === 1 ? '.' : '...') + ' ' + fixCrazySan(node.san);
  return 'Initial position';
}

export function plural(noun: string, nb: number): string {
  return nb + ' ' + (nb === 1 ? noun : noun + 's');
}

export function titleNameToId(titleName: string): string {
  const split = titleName.split(' ');
  return (split.length === 1 ? split[0] : split[1]).toLowerCase();
}

export function spinner(): VNode {
  return h('div.spinner', [
    h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
      h('circle', {
        attrs: { cx: 20, cy: 20, r: 18, fill: 'none' },
      }),
    ]),
  ]);
}

export function baseUrl() {
  return `${window.location.protocol}//${window.location.host}`;
}

export function option(value: string, current: string | undefined, name: string) {
  return h(
    'option',
    {
      attrs: {
        value: value,
        selected: value === current,
      },
    },
    name
  );
}

export function scrollTo(el: HTMLElement | undefined, target: HTMLElement | null) {
  if (el && target) el.scrollTop = target.offsetTop - el.offsetHeight / 2 + target.offsetHeight / 2;
}

export function treeReconstruct(parts: Tree.Node[]): Tree.Node {
  const root = parts[0],
    nb = parts.length;
  let node = root;
  root.id = '';
  for (let i = 1; i < nb; i++) {
    const n = parts[i];
    if (node.children) node.children.unshift(n);
    else node.children = [n];
    node = n;
  }
  node.children = node.children || [];
  return root;
}

export interface NodeWithParentIntermediate {
  nodes: Tree.ParentedNode[];
  prev?: Tree.Node;
}

export function parentedNodesFromOrdering(nodes: Tree.Node[]): Tree.ParentedNode[] {
  let withParent: Tree.ParentedNode[] = [];
  if (nodes.length > 0) {
    const initial: NodeWithParentIntermediate = { nodes: [], prev: undefined };
    withParent = nodes.reduce(({ nodes, prev }, node) => {
      return {
        nodes: nodes.concat([{ parent: prev, ...node, tag: 'parented' }]),
        prev: node,
      };
    }, initial).nodes;
  }
  return withParent;
}

export function parentedNode(node: Tree.Node, parent?: Tree.Node): Tree.ParentedNode {
  return { tag: 'parented', parent, ...node };
}

export function parentedNodes(nodes: Tree.Node[], parent?: Tree.Node): Tree.ParentedNode[] {
  return nodes.map(n => parentedNode(n, parent));
}

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

const noCevalVariants = [
  'linesOfAction',
  'scrambledEggs',
  'shogi',
  'xiangqi',
  'minishogi',
  'minixiangqi',
  'flipello',
  'flipello10',
  'amazons',
  'oware',
  'togyzkumalak',
  'go9x9',
  'go13x13',
  'go19x19',
  'backgammon',
];

export function allowCevalForVariant(variant: VariantKey) {
  return noCevalVariants.indexOf(variant) == -1;
}

export type LexicalUci = {
  from: cg.Key;
  to: cg.Key;
  dropRole?: cg.Role;
  promotion?: cg.Role;
};

export const parseLexicalUci = (uci: string): LexicalUci | undefined => {
  if (!uci) return undefined;
  const pos = uci.match(/[a-z][1-9][0-9]?/g) as cg.Key[];

  if (uci[1] === '@') {
    return {
      from: pos[0],
      to: pos[0],
      dropRole: `${uci[0].toLowerCase()}-piece` as cg.Role,
    };
  }

  // e7e8Q
  let promotion: cg.Role | undefined = undefined;

  const uciToFrom = `${pos[0]}${pos[1]}`;
  if (uci.startsWith(uciToFrom) && uci.length == uciToFrom.length + 1) {
    promotion = `${uci[uci.length - 1]}-piece` as cg.Role;
  }

  return {
    from: pos[0],
    to: pos[1],
    promotion,
  };
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
    case 'monster':
      return 'monster';
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
    case 'go9x9':
      return 'go9x9';
    case 'go13x13':
      return 'go13x13';
    case 'go19x19':
      return 'go19x19';
    case 'backgammon':
      return 'backgammon';
  }
};
