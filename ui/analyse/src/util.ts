import { h, VNode, Hooks, Attrs } from 'snabbdom';
import { fixCrazySan, getScore } from 'stratutils';
import * as cg from 'chessground/types';

export { autolink, innerHTML, enrichText, richHTML, toYouTubeEmbed, toTwitchEmbed } from 'common/richText';

export const emptyRedButton = 'button.button.button-red.button-empty';

const longPressDuration = 610; // used in bindMobileTapHold

export function clearSelection() {
  window.getSelection()?.removeAllRanges();
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
    redraw,
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

export function fullTurnCount(node: Tree.Node): number {
  return 1 + Math.floor((node.turnCount - (node.playedPlayerIndex === node.playerIndex ? 0 : 1)) / 2);
}

export function nodeFullActionTextUci(node: Tree.Node) {
  if (node.children.length === 1 && node.playedPlayerIndex === node.children[0].playedPlayerIndex)
    return node.uci + ' ' + node.children[0].uci;
  else return node.uci;
}

export function nodeFullActionTextSan(node: Tree.Node) {
  if (node.san)
    if (
      node.children.length === 1 &&
      node.playedPlayerIndex === node.children[0].playedPlayerIndex &&
      node.children[0].san
    )
      return fixCrazySan(node.san) + ' ' + fixCrazySan(node.children[0].san);
    else return fixCrazySan(node.san);
  return 'Initial position';
}

export function nodeFullName(node: Tree.Node) {
  if (node.san)
    if (node.san === 'NOSAN' && node.uci != undefined)
      return fullTurnCount(node) + (node.playedPlayerIndex === 'p1' ? '.' : '...') + ' ' + nodeFullActionTextUci(node);
    else
      return fullTurnCount(node) + (node.playedPlayerIndex === 'p1' ? '.' : '...') + ' ' + nodeFullActionTextSan(node);
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
    name,
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

export function fullTurnNodesFromNode(node: Tree.ParentedNode): Tree.ParentedNode[] {
  function childNodeOfSameTurn(currentNode: Tree.ParentedNode, nodes: Tree.ParentedNode[]): Tree.ParentedNode[] {
    if (!currentNode.children) return nodes;
    const cs = parentedNodes(currentNode.children, currentNode),
      main = cs[0];
    if (!main) return nodes;
    if (!currentNode.parent) return nodes;
    if (currentNode.playedPlayerIndex === currentNode.playerIndex) return childNodeOfSameTurn(main, nodes.concat(main));
    return nodes;
  }
  return childNodeOfSameTurn(node, [node]);
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

export function getScoreFromFen(variant: VariantKey, fen: string, playerIndex: string): number {
  return getScore(variant, fen, playerIndex) ?? 0;
}

const noServerEvalVariants = [
  'monster',
  'linesOfAction',
  'scrambledEggs',
  'amazons',
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

export function allowServerEvalForVariant(variant: VariantKey) {
  return noServerEvalVariants.indexOf(variant) == -1;
}

export const isOnlyDropsPly = (node: Tree.Node, variantKey: VariantKey, defaultValue: boolean) => {
  if (variantKey === 'amazons') return typeof node.dropsByRole === 'string' && node.dropsByRole.length > 0;
  else return defaultValue;
};

export function allowExplorerForVariant(_variant: VariantKey) {
  // @TODO: enable it at least for chess game family when the link gets fixed (points towards something that exists)
  return false;
}
