import * as util from 'chessground/util';
import * as cg from 'chessground/types';
import RoundController from './ctrl';

export function updateBoardFromFen(ctrl: RoundController, newFen: string) {
  const diff: cg.PiecesDiff = new Map();
  const boardFen = newFen.split(' ')[0];
  const boardFenNoPocket = boardFen.indexOf('[') !== -1 ? boardFen.slice(0, boardFen.indexOf('[')) : boardFen;
  let col = 0;
  let row = ctrl.data.game.variant.boardSize.height;
  let num = 0;
  const emptySquares: cg.Key[] = [];

  //This is very similar to chessground.fen.read but also calcs the empty squares (for captures)
  for (const r of boardFenNoPocket.split('/')) {
    let skipNext = false;
    for (let i = 0; i < r.length; i++) {
      if (skipNext) {
        skipNext = false;
        continue;
      }
      const c = r[i];
      const step = parseInt(c, 10);
      if (step > 0) {
        let stepped = false;
        if (ctrl.data.game.variant.boardSize.width > 9 && i < r.length + 1 && parseInt(r[i + 1]) >= 0) {
          const twoCharStep = parseInt(c + r[i + 1]);
          if (twoCharStep > 0) {
            for (let j = 0; j < twoCharStep; j++) {
              emptySquares.push(util.pos2key([col + j + 1, row]));
            }
            col += twoCharStep;
            stepped = true;
            skipNext = true;
          }
        }
        if (!stepped) {
          num += step;
          for (let j = 0; j < step; j++) {
            emptySquares.push(util.pos2key([col + j + 1, row]));
          }
        }
      } else {
        col += 1 + num;
        num = 0;
        const letter = c.toLowerCase();
        const playerIndex = (c === letter ? 'p2' : 'p1') as cg.PlayerIndex;
        const piece = {
          role: 's-piece',
          playerIndex: playerIndex,
        } as cg.Piece;
        diff.set(util.pos2key([col, row]), piece);
      }
    }
    --row;
    if (row === 0) break;
    col = 0;
    num = 0;
  }

  //update empty squares as there could have been captures, which are not currently calculated in chessground
  emptySquares.forEach(x => diff.set(x, undefined));

  ctrl.chessground.setPiecesNoAnim(diff);
  ctrl.chessground.redrawAll();
}
