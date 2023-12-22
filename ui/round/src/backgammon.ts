import * as util from 'chessground/util';
import * as cg from 'chessground/types';
import RoundController from './ctrl';

export function updateBoardFromFen(ctrl: RoundController, newFen: string) {
  const diff: cg.PiecesDiff = new Map();
  let boardFen = newFen.split(' ')[0];
  //TODO also managed the pocket?
  if (boardFen.indexOf('[') !== -1) boardFen = boardFen.slice(0, boardFen.indexOf('['));

  let col = 0;
  let row = 2;
  let num = 0;

  //This is very similar to chessground.fen.read
  for (const r of boardFen.split('/')) {
    for (const f of r.split(',')) {
      if (isNaN(+f)) {
        col += 1 + num;
        num = 0;
        const count = f.slice(0, -1);
        const role = f.substring(f.length - 1).toLowerCase();
        const playerIndex = f.substring(f.length - 1) === role ? 'p2' : 'p1';
        const piece = {
          role: `${role}${count}-piece`,
          playerIndex: playerIndex,
        } as cg.Piece;
        diff.set(util.pos2key([col, row]), piece);
      } else {
        num = num + +f;
      }
    }
    --row;
    if (row === 0) break;
    col = 0;
    num = 0;
  }

  ctrl.chessground.setPiecesNoAnim(diff);
  ctrl.chessground.redrawAll();
}
