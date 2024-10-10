import AnalyseCtrl from './ctrl';
import { initialFen } from 'stratutils';

interface SgfNode {
  ply: Ply;
  playedPlayerIndex: PlayerIndex;
  san?: San;
}

function renderNodesTxt(nodes: SgfNode[]): string {
  if (!nodes[0]) return '';
  if (!nodes[0].san) nodes = nodes.slice(1);
  if (!nodes[0]) return '';
  let s = '';
  const startingColor = nodes[0].san ? nodes[0].san[1] : '';
  const startingPlayer = nodes[0].playedPlayerIndex;
  const color = startingColor === 'W' ? 'B' : 'W';
  nodes.forEach(function (node, _) {
    if (node.ply === 0) return;
    if (node.playedPlayerIndex !== startingPlayer && node.san) {
      s += ';' + color + node.san.substring(2) + '\n';
    } else {
      s += node.san + '\n';
    }
  });
  return s.trim();
}

export function renderFullTxt(ctrl: AnalyseCtrl): string {
  const g = ctrl.data.game;
  let txt = renderNodesTxt(ctrl.tree.getNodeList(ctrl.path));
  const tags: Array<[string, string]> = [];
  tags.push(['FF', '4']);
  tags.push(['CA', 'UTF-8']);
  if (g.initialFen && g.initialFen !== initialFen && !['go9x9', 'go13x13', 'go19x19'].includes(g.variant.key))
    tags.push(['IP', g.initialFen]);
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
  if (['breakthroughtroyka', 'minibreakthroughtroyka'].includes(g.variant.key)) {
    tags.push(['GM', '41']);
    tags.push(['SZ', g.variant.boardSize.height.toString()]);
  }
  if (['go9x9', 'go13x13', 'go19x19'].includes(g.variant.key)) {
    //from position and handicap in plain analysis does not add initial moves at start
    // as anlaysis needs to correctly support go hanicap starting player
    tags.push(['GM', '1']);
    tags.push(['SZ', g.variant.boardSize.height.toString()]);
    tags.push(['RU', 'Chinese']);
  }
  if (['backgammon', 'nackgammon'].includes(g.variant.key)) tags.push(['GM', '6']);
  if (['amazons'].includes(g.variant.key)) {
    tags.push(['GM', '18']);
    tags.push(['SZ', g.variant.boardSize.height.toString()]);
  }
  if (tags.length)
    txt =
      '(;' +
      tags
        .map(function (t) {
          return t[0] + '["' + t[1] + '"]';
        })
        .join('\n') +
      '\n\n' +
      txt +
      ')';
  return txt;
}
