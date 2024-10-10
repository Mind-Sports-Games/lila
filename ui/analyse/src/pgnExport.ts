import AnalyseCtrl from './ctrl';
import { h } from 'snabbdom';
import { initialFen, fixCrazySan } from 'stratutils';
import { MaybeVNodes } from './interfaces';

interface PgnNode {
  ply: Ply;
  turnCount: number;
  playerIndex: PlayerIndex;
  playedPlayerIndex: PlayerIndex;
  san?: San;
}

//requries input of parent node to calculcate current node turn count
function nodeToTurn(node: PgnNode): number {
  return Math.floor((node.turnCount ?? 0) / 2) + 1;
}

function renderNodesTxt(nodes: PgnNode[]): string {
  if (!nodes[0]) return '';
  if (!nodes[0].san) nodes = nodes.slice(1);
  if (!nodes[0]) return '';
  let s = nodes[0].turnCount % 2 === 1 || nodes[0].turnCount === 0 ? '' : nodeToTurn(nodes[0]) + '... ';
  nodes.forEach(function (node, i) {
    if (node.ply === 0) return;
    if (!nodes[i - 1]) s += Math.floor(node.turnCount / 2) + 1 + '. ';
    if (nodes[i - 1] && node.playedPlayerIndex === 'p1' && nodes[i - 1].playedPlayerIndex === 'p2')
      s += nodeToTurn(nodes[i - 1]) + '. ';
    else s += '';
    s += fixCrazySan(node.san!) + ((i + 9) % 8 === 0 ? '\n' : ' ');
  });
  return s.trim();
}

export function renderFullTxt(ctrl: AnalyseCtrl): string {
  const g = ctrl.data.game;
  let txt = renderNodesTxt(ctrl.tree.getNodeList(ctrl.path));
  const tags: Array<[string, string]> = [];
  if (g.variant.key !== 'standard') tags.push(['Variant', g.variant.name]);
  if (g.initialFen && g.initialFen !== initialFen) tags.push(['FEN', g.initialFen]);
  if (tags.length)
    txt =
      tags
        .map(function (t) {
          return '[' + t[0] + ' "' + t[1] + '"]';
        })
        .join('\n') +
      '\n\n' +
      txt;
  return txt;
}

export function renderNodesHtml(nodes: PgnNode[]): MaybeVNodes {
  if (!nodes[0]) return [];
  if (!nodes[0].san) nodes = nodes.slice(1);
  if (!nodes[0]) return [];
  const tags: MaybeVNodes = [];
  if (!(nodes[0].turnCount % 2 === 1 || nodes[0].turnCount === 0)) tags.push(h('index', nodeToTurn(nodes[0]) + '...'));
  nodes.forEach((node, i) => {
    if (node.ply === 0) return;
    if (nodes[i - 1] && node.playedPlayerIndex === 'p1' && nodes[i - 1].playedPlayerIndex === 'p2')
      tags.push(h('index', nodeToTurn(nodes[i - 1]) + '. '));
    tags.push(h('san', fixCrazySan(node.san!)));
  });
  return tags;
}
