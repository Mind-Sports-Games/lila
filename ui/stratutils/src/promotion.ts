import { Api as ChessgroundApi } from 'chessground/api';

export const forcedShogiPromotion = (chessground: ChessgroundApi, orig: Key, dest: Key): boolean | undefined => {
  const piece = chessground.state.pieces.get(dest),
    premovePiece = chessground.state.pieces.get(orig);
  const isP1 = piece && piece.playerIndex == 'p1';
  const isP2 = piece && piece.playerIndex == 'p2';
  return (
    (((piece && (piece.role === 'l-piece' || piece.role === 'p-piece') && !premovePiece) ||
      (premovePiece && (premovePiece.role === 'l-piece' || premovePiece.role === 'p-piece'))) &&
      ((dest[1] === '9' && isP1) || (dest[1] == '1' && isP2))) ||
    (((piece && piece.role === 'n-piece' && !premovePiece) || (premovePiece && premovePiece.role === 'n-piece')) &&
      ((['8', '9'].includes(dest[1]) && isP1) || (['1', '2'].includes(dest[1]) && isP2)))
  );
};

// forced promotion for shogi pawn in last rank
// assumes possible promotion is passed through (therefore no checks for drops etc).
export const forcedMiniShogiPromotion = (chessground: ChessgroundApi, orig: Key, dest: Key): boolean | undefined => {
  const piece = chessground.state.pieces.get(dest),
    premovePiece = chessground.state.pieces.get(orig);
  const isP1 = piece && piece.playerIndex == 'p1';
  const isP2 = piece && piece.playerIndex == 'p2';
  return (
    ((piece && piece.role === 'p-piece' && !premovePiece) || (premovePiece && premovePiece.role === 'p-piece')) &&
    ((dest[1] === '5' && isP1) || (dest[1] == '1' && isP2))
  );
};

export const possiblePromotion = (
  chessground: ChessgroundApi,
  orig: Key,
  dest: Key,
  variant: VariantKey,
): boolean | undefined => {
  const piece = chessground.state.pieces.get(dest),
    premovePiece = chessground.state.pieces.get(orig);
  const isP1 = piece && piece.playerIndex == 'p1';
  const isP2 = piece && piece.playerIndex == 'p2';
  switch (variant) {
    case 'oware':
    case 'togyzkumalak':
    case 'bestemshe':
    case 'minixiangqi':
    case 'xiangqi':
    case 'flipello10':
    case 'flipello':
    case 'amazons':
    case 'breakthroughtroyka':
    case 'minibreakthroughtroyka':
    case 'go9x9':
    case 'go13x13':
    case 'go19x19':
    case 'backgammon':
    case 'hyper':
    case 'nackgammon':
      return false;
    case 'shogi':
      return (
        ((piece && !piece.promoted && piece.role !== 'k-piece' && piece.role !== 'g-piece' && !premovePiece) ||
          (premovePiece &&
            !premovePiece.promoted &&
            premovePiece.role !== 'k-piece' &&
            premovePiece.role !== 'g-piece')) &&
        ((isP1 && (['7', '8', '9'].includes(dest[1]) || ['7', '8', '9'].includes(orig[1]))) ||
          (isP2 && (['1', '2', '3'].includes(dest[1]) || ['1', '2', '3'].includes(orig[1])))) &&
        orig != 'a0' // cant promote from a drop
      );
    case 'minishogi':
      return (
        ((piece && !piece.promoted && piece.role !== 'k-piece' && piece.role !== 'g-piece' && !premovePiece) ||
          (premovePiece &&
            !premovePiece.promoted &&
            premovePiece.role !== 'k-piece' &&
            premovePiece.role !== 'g-piece')) &&
        ((isP1 && (['5'].includes(dest[1]) || ['5'].includes(orig[1]))) ||
          (isP2 && (['1'].includes(dest[1]) || ['1'].includes(orig[1])))) &&
        orig != 'a0' // cant promote from a drop
      );
    default:
      return (
        ((piece && piece.role === 'p-piece' && !premovePiece) || (premovePiece && premovePiece.role === 'p-piece')) &&
        ((dest[1] === '8' && piece && piece.playerIndex === 'p1') ||
          (dest[1] === '1' && piece && piece.playerIndex === 'p2'))
      );
  }
};
