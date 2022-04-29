import * as util from 'chessground/util';
import * as cg from 'chessground/types';
import RoundController from './ctrl';

export function updateBoardFromMove(ctrl: RoundController, newFen: string) {
  const diff: cg.PiecesDiff = new Map();
  const boardFen = newFen.split(' ')[0];

  var top = true;
  var colNum = 0;
  var emptySquares: cg.Key[] = [];
  for (var i = 0; i < boardFen.length; i++) {
    if (boardFen.charAt(i) == '/') {
      top = false;
      colNum = 0;
      continue;
    }
    if (['1', '2', '3', '4', '5', '6'].includes(boardFen.charAt(i))) {
      for (var j = 0; j < Number(boardFen.charAt(i)); j++) {
        emptySquares.push(util.pos2key([colNum + j + 1, top ? 2 : 1]));
      }
      colNum += Number(boardFen.charAt(i));
      continue;
    }
    var playerIndex: 'p1' | 'p2' = top ? 'p2' : 'p1';
    var piece: cg.Piece = { role: `${boardFen.charAt(i)}-piece` as cg.Role, playerIndex: playerIndex };
    var pos: cg.Pos = [colNum + 1, top ? 2 : 1] as cg.Pos;
    var k: cg.Key = util.pos2key(pos);
    diff.set(k, piece);
    colNum++;
  }
  //update empty squares as there could have been captures, which are not currently calculated in chessground
  emptySquares.forEach(x => diff.set(x, undefined));

  ctrl.chessground.setPieces(diff);
}
