import AnalyseCtrl from '../../ctrl';
import { Goal } from './interfaces';
import { Comment } from '../../practice/practiceCtrl';

// returns null if not deep enough to know
function isDrawish(node: Tree.Node): boolean | null {
  if (!hasSolidEval(node)) return null;
  return !node.ceval!.mate && Math.abs(node.ceval!.cp!) < 150;
}
// returns null if not deep enough to know
function isWinning(node: Tree.Node, goalCp: number, playerIndex: PlayerIndex): boolean | null {
  if (!hasSolidEval(node)) return null;
  const cp = node.ceval!.mate! > 0 ? 99999 : node.ceval!.mate! < 0 ? -99999 : node.ceval!.cp;
  return playerIndex === 'p1' ? cp! >= goalCp : cp! <= goalCp;
}
// returns null if not deep enough to know
function myMateIn(node: Tree.Node, playerIndex: PlayerIndex): number | boolean | null {
  if (!hasSolidEval(node)) return null;
  if (!node.ceval!.mate) return false;
  const mateIn = node.ceval!.mate! * (playerIndex === 'p1' ? 1 : -1);
  return mateIn > 0 ? mateIn : false;
}

function hasSolidEval(node: Tree.Node) {
  return node.ceval && node.ceval.depth >= 16;
}

function hasBlundered(comment: Comment | null) {
  return comment && (comment.verdict === 'mistake' || comment.verdict === 'blunder');
}

// returns null = ongoing, true = win, false = fail
export default function (root: AnalyseCtrl, goal: Goal, nbMoves: number): boolean | null {
  const node = root.node;
  if (!node.uci) return null;
  const outcome = root.outcome();
  if (outcome && outcome.winner && outcome.winner !== root.bottomPlayerIndex()) return false;
  if (outcome && outcome.winner && outcome.winner === root.bottomPlayerIndex()) return true;
  if (hasBlundered(root.practice!.comment())) return false;
  switch (goal.result) {
    case 'drawIn':
    case 'equalIn':
      if (node.threefold) return true;
      if (isDrawish(node) === false) return false;
      if (nbMoves > goal.moves!) return false;
      if (outcome && !outcome.winner) return true;
      if (nbMoves >= goal.moves!) return isDrawish(node);
      break;
    case 'evalIn':
      if (nbMoves >= goal.moves!) return isWinning(node, goal.cp!, root.bottomPlayerIndex());
      break;
    case 'mateIn': {
      if (nbMoves > goal.moves!) return false;
      const mateIn = myMateIn(node, root.bottomPlayerIndex());
      if (mateIn === null) return null;
      if (!mateIn || (mateIn as number) + nbMoves > goal.moves!) return false;
      break;
    }
    case 'promotion':
      if (!node.uci[4]) return null;
      return isWinning(node, goal.cp!, root.bottomPlayerIndex());
    case 'mate':
  }
  return null;
}
