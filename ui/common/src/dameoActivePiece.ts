import { read as dameoFenRead } from 'chessground/variants/dameo/fen';
import type { Key as CGKey, Pieces as CGPieces } from 'chessground/types';

export function dameoActivePiece(fen: string): CGKey | undefined {
    const pieces: CGPieces = dameoFenRead(fen);
    for (const [key, piece] of pieces) {
      if (['a-piece', 'b-piece'].includes(piece.role)) {
        return key;
      }
    }
    return undefined;
}