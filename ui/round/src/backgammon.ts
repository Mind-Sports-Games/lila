import * as util from 'chessground/util';
import * as cg from 'chessground/types';
import * as cgFen from 'chessground/fen';
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

export function updateBoardFromMove(ctrl: RoundController, orig: cg.Key, dest: cg.Key) {
  //TODO also manage the pocket and captures (as well as undo action?)

  //assumption this is the fen before the move (check this?)
  const currentFen = ctrl.data.steps[ctrl.data.steps.length - 1].fen;
  const pieces = cgFen.read(currentFen, ctrl.data.game.variant.boardSize, ctrl.data.game.variant.key as cg.Variant);
  const origPiece = pieces.get(orig);
  const destPiece = pieces.get(dest);

  const diff: cg.PiecesDiff = new Map();

  //Update orig and dest squares as they may have changed
  if (origPiece) {
    const oCount = +origPiece.role.split('-')[0].substring(1);
    const oRoleLetter = origPiece.role.charAt(0);
    const oPlayerIndex = origPiece.playerIndex;
    if (oCount > 1) {
      const piece = {
        role: `${oRoleLetter}${oCount - 1}-piece`,
        playerIndex: oPlayerIndex,
      } as cg.Piece;
      diff.set(orig, piece);
    }

    //where did we land?
    if (destPiece) {
      const dCount = +destPiece.role.split('-')[0].substring(1);
      //const dRoleLetter = destPiece.role.charAt(0);
      const dPlayerIndex = destPiece.playerIndex;
      if (dPlayerIndex === oPlayerIndex) {
        const piece = {
          role: `${oRoleLetter}${dCount + 1}-piece`,
          playerIndex: oPlayerIndex,
        } as cg.Piece;
        diff.set(dest, piece);
      } else {
        //capture
        const piece = {
          role: `${oRoleLetter}1-piece`,
          playerIndex: oPlayerIndex,
        } as cg.Piece;
        diff.set(dest, piece);
      }
    } else {
      //empty space
      const piece = {
        role: `${oRoleLetter}1-piece`,
        playerIndex: oPlayerIndex,
      } as cg.Piece;
      diff.set(dest, piece);
    }
  }

  ctrl.chessground.setPiecesNoAnim(diff);
  ctrl.chessground.redrawAll();
}
