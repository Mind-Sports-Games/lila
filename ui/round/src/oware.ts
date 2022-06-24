import * as util from 'chessground/util';
import * as cg from 'chessground/types';
import RoundController from './ctrl';

export function updateBoardFromFen(ctrl: RoundController, newFen: string) {
  const diff: cg.PiecesDiff = new Map();
  const boardFen = newFen.split(' ')[0];

  let top = true;
  let colNum = 0;
  const emptySquares: cg.Key[] = [];
  for (let i = 0; i < boardFen.length; i++) {
    if (boardFen.charAt(i) == '/') {
      top = false;
      colNum = 0;
      continue;
    }
    if (['1', '2', '3', '4', '5', '6'].includes(boardFen.charAt(i))) {
      for (let j = 0; j < Number(boardFen.charAt(i)); j++) {
        emptySquares.push(util.pos2key([colNum + j + 1, top ? 2 : 1]));
      }
      colNum += Number(boardFen.charAt(i));
      continue;
    }
    const playerIndex: 'p1' | 'p2' = top ? 'p2' : 'p1';
    const piece: cg.Piece = { role: `${boardFen.charAt(i)}-piece` as cg.Role, playerIndex: playerIndex };
    const pos: cg.Pos = [colNum + 1, top ? 2 : 1] as cg.Pos;
    const k: cg.Key = util.pos2key(pos);
    diff.set(k, piece);
    colNum++;
  }
  //update empty squares as there could have been captures, which are not currently calculated in chessground
  emptySquares.forEach(x => diff.set(x, undefined));

  ctrl.chessground.setPiecesNoAnim(diff);
}

export function updateBoardFromMove(ctrl: RoundController, orig: cg.Key, dest: cg.Key) {
  const boardWidth = ctrl.data.game.variant.boardSize.width;
  const currentFen = ctrl.data.steps[ctrl.data.steps.length - 1].fen;
  const boardFen = currentFen.split(' ')[0];
  const boardArray = createBoardArrayFromBoardFen(boardFen, boardWidth);
  const origBoardIndex = boardIndexFromUci(orig, boardWidth);
  const destBoardIndex = boardIndexFromUci(dest, boardWidth);
  const stones = boardArray[origBoardIndex];
  const extraStoneArray = Array<number>(boardWidth * 2).fill(0);

  //calculate where the stones from moving piece will land
  for (let i = 1; i < stones + 1; i++) {
    const missingOwnHouse = Math.floor((i - 1) / (boardWidth * 2 - 1));
    const indexToAdd = (origBoardIndex + i + missingOwnHouse) % (boardWidth * 2);
    extraStoneArray[indexToAdd] += 1;
  }

  //remove piece that is moving
  const BoardArrayRemovedMovingPiece = boardArray.slice(0, boardArray.length);
  BoardArrayRemovedMovingPiece[origBoardIndex] = 0;

  //add moving stones to board
  const finalBoardArray = BoardArrayRemovedMovingPiece.map((v, i) => v + extraStoneArray[i]);

  //remove any captured pieces (must check for grandslam!)
  if (dest[1] !== orig[1] && !isGrandSlam(finalBoardArray, destBoardIndex, boardWidth)) {
    for (let i = 0; i < (destBoardIndex % boardWidth) + 1; i++) {
      const captureIndex = destBoardIndex - i;
      if (finalBoardArray[captureIndex] == 2 || finalBoardArray[captureIndex] == 3) {
        finalBoardArray[captureIndex] = 0;
      } else {
        break;
      }
    }
  }

  //calculate the new pieces of the board to update chessground with
  const pieces: cg.PiecesDiff = new Map();
  for (let i = 0; i < finalBoardArray.length; i++) {
    const playerIndex: 'p1' | 'p2' = i < boardWidth ? 'p1' : 'p2';
    const numStones = finalBoardArray[i];
    const pos: cg.Pos = [i < boardWidth ? i + 1 : boardWidth * 2 - i, i < boardWidth ? 1 : 2] as cg.Pos;
    const k: cg.Key = util.pos2key(pos);
    if (numStones == 0) {
      pieces.set(k, undefined);
    } else {
      const piece: cg.Piece = {
        role: `${stoneNumberToPieceLetter(finalBoardArray[i])}-piece` as cg.Role,
        playerIndex: playerIndex,
      };
      pieces.set(k, piece);
    }
  }

  ctrl.chessground.setPiecesNoAnim(pieces);
}

function createBoardArrayFromBoardFen(boardFen: string, boardWidth: number): number[] {
  const boardArrayFenOrder: number[] = [];

  for (let i = 0; i < boardFen.length; i++) {
    if (boardFen.charAt(i) == '/') {
      continue;
    }
    if (['1', '2', '3', '4', '5', '6'].includes(boardFen.charAt(i))) {
      for (let j = 0; j < Number(boardFen.charAt(i)); j++) {
        boardArrayFenOrder.push(0);
      }
      continue;
    }
    boardArrayFenOrder.push(owarePieceToNumberOfStones(`${boardFen.charAt(i)}`));
  }

  const boardArray: number[] = boardArrayFenOrder
    .splice(boardWidth, boardWidth)
    .concat(boardArrayFenOrder.splice(0, boardWidth).reverse());
  return boardArray;
}

function owarePieceToNumberOfStones(letter: string): number {
  const num = letter.charCodeAt(0);
  if (num < 91) {
    return num - 64;
  } else {
    return num - 70;
  }
}

function stoneNumberToPieceLetter(num: number): string {
  if (num < 27) {
    return String.fromCharCode(num + 64);
  } else {
    return String.fromCharCode(num + 70);
  }
}

function boardIndexFromUci(uci: cg.Key, boardWidth: number): number {
  return uci[1] === '1' ? uci[0].charCodeAt(0) - 97 : 96 - uci.charCodeAt(0) + 2 * boardWidth;
}

//assumption - move has been played but stones not captured, dest is on opponents side
function isGrandSlam(finalBoardArray: number[], destBoardIndex: number, boardWidth: number): boolean {
  //check spaces not possible to capture are 0
  for (let i = 1; i < boardWidth - (destBoardIndex % boardWidth); i++) {
    if (finalBoardArray[destBoardIndex + i] != 0) return false;
  }
  //check captures to edge and then non-zeros
  let checkCapture = true;
  for (let i = 0; i < (destBoardIndex % boardWidth) + 1; i++) {
    const captureIndex = destBoardIndex - i;
    if (checkCapture && (finalBoardArray[captureIndex] == 2 || finalBoardArray[captureIndex] == 3)) {
      continue;
    } else {
      checkCapture = false;
      if (finalBoardArray[captureIndex] != 0) {
        return false;
      }
    }
  }

  return true;
}
