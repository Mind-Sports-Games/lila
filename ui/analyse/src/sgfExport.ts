import AnalyseCtrl from './ctrl';
import { initialFen, fixCrazySan } from 'stratutils';

interface SgfNode {
  ply: Ply;
  san?: San;
}

//TODO add logic to produce sgf not pgn actionstrings
function renderNodesTxt(nodes: SgfNode[]): string {
  console.log('nodes', nodes);
  if (!nodes[0]) return '';
  if (!nodes[0].san) nodes = nodes.slice(1);
  if (!nodes[0]) return '';
  let s = nodes[0].ply % 2 === 1 ? '' : Math.floor((nodes[0].ply + 1) / 2) + '... ';
  nodes.forEach(function (node, i) {
    if (node.ply === 0) return;
    if (node.ply % 2 === 1) s += (node.ply + 1) / 2 + '. ';
    else s += '';
    s += fixCrazySan(node.san!) + ((i + 9) % 8 === 0 ? '\n' : ' ');
  });
  return s.trim();
}

export function renderFullTxt(ctrl: AnalyseCtrl): string {
  const g = ctrl.data.game;
  let txt = renderNodesTxt(ctrl.tree.getNodeList(ctrl.path));
  const tags: Array<[string, string]> = [];
  tags.push(['FF', '4']);
  tags.push(['CA', 'UTF-8']);
  if (g.initialFen && g.initialFen !== initialFen) tags.push(['IP', g.initialFen]);
  if (['linesOfAction', 'scrambledEggs'].includes(g.variant.key)) tags.push(['GM', '9']);
  if (['shogi', 'minishogi'].includes(g.variant.key)) {
    tags.push(['GM', '8']);
    tags.push(['SZ', g.variant.boardSize.height.toString()]);
  }
  if (['xiangqi', 'minixiangqi'].includes(g.variant.key)) {
    tags.push(['GM', '7']);
    tags.push(['SZ', g.variant.boardSize.height.toString()]);
  }
  if (['flipello', 'flipello10'].includes(g.variant.key)) {
    tags.push(['GM', '2']);
    tags.push(['SZ', g.variant.boardSize.height.toString()]);
  }
  if (['go9x9', 'go13x13', 'go19x19'].includes(g.variant.key)) {
    tags.push(['GM', '1']);
    tags.push(['SZ', g.variant.boardSize.height.toString()]);
    tags.push(['RU', 'Chinese']);
  }
  if (['backgammon', 'nackgammon'].includes(g.variant.key)) tags.push(['GM', '6']);
  if (tags.length)
    txt =
      '(;' +
      tags
        .map(function (t) {
          return t[0] + '["' + t[1] + '"]';
        })
        .join('\n') +
      '\n\n' +
      txt;
  return txt;
}
