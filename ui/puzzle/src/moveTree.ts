import { Chess } from 'stratops/chess';
import { INITIAL_FEN, makeFen, parseFen } from 'stratops/fen';
import { makeSan, parseSan } from 'stratops/san';
import { makeSquare, makeUci, parseUci } from 'stratops/util';
import { scalachessCharPair } from 'stratops/compat';
import { TreeWrapper } from 'tree';
import { Move } from 'stratops/types';

export function pgnToTree(pgn: San[]): Tree.Node {
  const pos = Chess.default();
  const root: Tree.Node = {
    ply: 0,
    playerIndex: 'p1',
    id: '',
    fen: INITIAL_FEN,
    children: [],
  } as Tree.Node;
  let current = root;
  pgn.forEach((san, i) => {
    const move = parseSan('chess')(pos, san)!;
    pos.play(move);
    const nextNode = makeNode(pos, move, i + 1, current.playerIndex === 'p1' ? 'p2' : 'p1', san);
    current.children.push(nextNode);
    current = nextNode;
  });
  return root;
}

export function mergeSolution(root: TreeWrapper, initialPath: Tree.Path, solution: Uci[], pov: PlayerIndex): void {
  const initialNode = root.nodeAtPath(initialPath);
  const pos = Chess.fromSetup(parseFen('chess')(initialNode.fen).unwrap()).unwrap();
  const fromPly = initialNode.ply;
  const nodes = solution.map((uci, i) => {
    const move = parseUci('chess')(uci)!;
    const san = makeSan('chess')(pos, move);
    pos.play(move);
    const node = makeNode(pos, move, fromPly + i + 1, initialNode.playerIndex, san);
    if ((pov == 'p1') == (node.ply % 2 == 1)) node.puzzle = 'good';
    return node;
  });
  root.addNodes(nodes, initialPath);
}

const makeNode = (pos: Chess, move: Move, ply: number, playerIndex: PlayerIndex, san: San): Tree.Node => ({
  ply,
  playerIndex,
  san,
  fen: makeFen('chess')(pos.toSetup()),
  id: scalachessCharPair('chess')(move),
  uci: makeUci('chess')(move),
  check: pos.isCheck() ? makeSquare('chess')(pos.toSetup().board.kingOf(pos.turn)!) : undefined,
  children: [],
});
