import { Api as ChessgroundApi } from 'chessground/api';

export const forcedShogiPromotion = (chessground: ChessgroundApi, orig: Key, dest: Key): boolean | undefined => {
  const piece = chessground.state.pieces.get(dest),
    premovePiece = chessground.state.pieces.get(orig);
  const isP1 = piece && piece.playerIndex == 'p1'
  const isP2 = piece && piece.playerIndex == 'p2'
  return (
    (((piece && (piece.role === 'l-piece' || piece.role === 'p-piece') && !premovePiece) ||
      (premovePiece && (premovePiece.role === 'l-piece' || premovePiece.role === 'p-piece'))) &&
      ((dest[1] === '9' && isP1) || (dest[1] == '1' && isP2))) ||
    (((piece && piece.role === 'n-piece' && !premovePiece) || (premovePiece && premovePiece.role === 'n-piece')) &&
      ((['8', '9'].includes(dest[1]) && isP1) ||
        (['1', '2'].includes(dest[1]) && isP2)))
  );
}

// forced promotion for shogi pawn in last rank
// assumes possible promotion is passed through (therefore no checks for drops etc).
export const forcedMiniShogiPromotion = (chessground: ChessgroundApi, orig: Key, dest: Key): boolean | undefined => {
  const piece = chessground.state.pieces.get(dest),
    premovePiece = chessground.state.pieces.get(orig);
  const isP1 = piece && piece.playerIndex == 'p1'
  const isP2 = piece && piece.playerIndex == 'p2'
  return (
    ((piece && piece.role === 'p-piece' && !premovePiece) || (premovePiece && premovePiece.role === 'p-piece')) &&
    ((dest[1] === '5' && isP1) || (dest[1] == '1' && isP2))
  );
}
