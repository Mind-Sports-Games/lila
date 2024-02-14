import * as cg from 'chessground/types';
import * as cgFen from 'chessground/fen';
import RoundController from './ctrl';

export function updateBoardFromFen(ctrl: RoundController, newFen: string) {
  ctrl.chessground.set({ fen: newFen });
  ctrl.chessground.redrawAll();
}

export function updateBoardFromDrop(ctrl: RoundController, key: cg.Key, playedPlayerIndex: 'p1' | 'p2') {
  const diff: cg.PiecesDiff = new Map();

  //assumption this is the fen before the drop (check this?)
  const currentFen = ctrl.data.steps[ctrl.data.steps.length - 1].fen;
  const pieces = cgFen.read(currentFen, ctrl.data.game.variant.boardSize, ctrl.data.game.variant.key as cg.Variant);
  const destPiece = pieces.get(key);
  const roleLetter = 's';
  let capture = false;

  if (destPiece) {
    const dCount = +destPiece.role.split('-')[0].substring(1);
    const dPlayerIndex = destPiece.playerIndex;
    if (dPlayerIndex === playedPlayerIndex) {
      const piece = {
        role: `${roleLetter}${dCount + 1}-piece`,
        playerIndex: playedPlayerIndex,
      } as cg.Piece;
      diff.set(key, piece);
    } else {
      capture = true;
      const piece = {
        role: `${roleLetter}1-piece`,
        playerIndex: playedPlayerIndex,
      } as cg.Piece;
      diff.set(key, piece);
    }
  } else {
    //empty space
    const piece = {
      role: `${roleLetter}1-piece`,
      playerIndex: playedPlayerIndex,
    } as cg.Piece;
    diff.set(key, piece);
  }

  updatePocketPieces(ctrl, playedPlayerIndex === 'p1' ? 'p2' : 'p1', true, capture);

  ctrl.chessground.set({ turnPlayerIndex: playedPlayerIndex }); //drop in chessground swaps player index
  ctrl.chessground.setPiecesNoAnim(diff);
  ctrl.chessground.redrawAll();
}

export function updateBoardFromMove(ctrl: RoundController, orig: cg.Key, dest: cg.Key) {
  //assumption this is the fen before the move (check this?)
  const currentFen = ctrl.data.steps[ctrl.data.steps.length - 1].fen;
  const pieces = cgFen.read(currentFen, ctrl.data.game.variant.boardSize, ctrl.data.game.variant.key as cg.Variant);
  const origPiece = pieces.get(orig);
  const destPiece = pieces.get(dest);

  const diff: cg.PiecesDiff = new Map();
  let capture = false;
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
        capture = true;
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

    if (capture) {
      updatePocketPieces(ctrl, oPlayerIndex === 'p1' ? 'p2' : 'p1', false, capture);
    }
  }
  ctrl.chessground.set({ turnPlayerIndex: ctrl.data.game.player }); //move in chessground swaps player index
  ctrl.chessground.setPiecesNoAnim(diff);
  ctrl.chessground.redrawAll();
}

function updatePocketPieces(
  ctrl: RoundController,
  capturedPiecePlayerIndex: PlayerIndex,
  isDrop: boolean,
  isCapture: boolean
): void {
  const currentFen = ctrl.data.steps[ctrl.data.steps.length - 1].fen;
  const pocketPieces = cgFen.readPocket(currentFen, ctrl.data.game.variant.key as cg.Variant);

  let playerCount = 0;
  let enemyCount = 0;

  pocketPieces.forEach(p => {
    const pCount = +p.role.split('-')[0].substring(1);
    if (p.playerIndex === capturedPiecePlayerIndex) {
      enemyCount = pCount;
    } else {
      playerCount = pCount;
    }
  });

  const newPocketPieces: cg.Piece[] = [];
  if (enemyCount > 0 || isCapture) {
    const piece = {
      role: `s${enemyCount + (isCapture ? 1 : 0)}-piece`,
      playerIndex: capturedPiecePlayerIndex,
    } as cg.Piece;
    newPocketPieces.push(piece);
  }
  if ((playerCount > 0 && !isDrop) || (isDrop && playerCount > 1)) {
    const piece = {
      role: `s${playerCount - (isDrop ? 1 : 0)}-piece`,
      playerIndex: capturedPiecePlayerIndex === 'p1' ? 'p2' : 'p1',
    } as cg.Piece;
    newPocketPieces.push(piece);
  }

  ctrl.chessground.setPocketPieces(newPocketPieces);
}
