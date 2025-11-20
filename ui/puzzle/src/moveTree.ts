import { INITIAL_FEN, makeFen, parseFen } from 'stratops/fen';
import { makeSan, parseSan } from 'stratops/san';
import { makeSquare, makeUci, parseUci } from 'stratops/util';
import { Position } from 'stratops/chess';
import { variantClassFromKey, variantKeyToRules } from 'stratops/variants/util';

import { scalachessCharPair } from 'stratops/compat';
import { TreeWrapper } from 'tree';
import { Move, Rules } from 'stratops/types';

export function actionStrsToTree(variantKey: VariantKey, actionStrs: San[]): Tree.Node {
  const pos: Position = variantClassFromKey(variantKey).default();
  const root: Tree.Node = {
    ply: 0,
    turnCount: 0,
    playedPlayerIndex: 'p2',
    playerIndex: 'p1',
    id: '',
    fen: INITIAL_FEN,
    children: [],
  } as Tree.Node;
  let current = root;
  // actionsStrs is san for chess/loa but uci for other games
  actionStrs.forEach((san, i) => {
    const move = parseSan(variantKeyToRules(variantKey))(pos, san)!;
    pos.play(move);
    const nextNode = makeNode(variantKeyToRules(variantKey), pos, move, i + 1, san);
    current.children.push(nextNode);
    current = nextNode;
  });
  return root;
}

export function mergeSolution(
  variantKey: VariantKey,
  root: TreeWrapper,
  initialPath: Tree.Path,
  solution: Uci[],
  pov: PlayerIndex,
): void {
  const initialNode = root.nodeAtPath(initialPath);
  const rules = variantKeyToRules(variantKey);
  const pos = variantClassFromKey(variantKey).fromSetup(parseFen(rules)(initialNode.fen).unwrap()).unwrap();
  const fromPly = initialNode.ply;
  const nodes = solution.map((uci, i) => {
    const move = parseUci(rules)(uci)!;
    const san = makeSan(rules)(pos, move);
    pos.play(move);
    const node = makeNode(rules, pos, move, fromPly + i + 1, san);
    if ((pov == 'p1') == (node.playedPlayerIndex === 'p1')) node.puzzle = 'good';
    return node;
  });
  root.addNodes(nodes, initialPath);
}

const makeNode = (rules: Rules, pos: Position, move: Move, ply: number, san: San): Tree.Node => ({
  ply,
  turnCount: ply, //TODO currently same for single action games, fix for multiaction
  playedPlayerIndex: pos.turn === 'p1' ? 'p2' : 'p1', //todo fix for multiaction
  playerIndex: ply % 2 == 1 ? 'p1' : 'p2', // fix for multiaciton (expand stratops to include playedplayer on pos?)
  san,
  fen: makeFen(rules)(pos.toSetup()),
  id: scalachessCharPair(rules)(move),
  uci: makeUci(rules)(move),
  check: pos.isCheck() ? makeSquare(rules)(pos.toSetup().board.kingOf(pos.turn)!) : undefined,
  children: [],
});
