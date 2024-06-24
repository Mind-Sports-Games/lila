import { TablebaseMoveStats } from './interfaces';
import { opposite } from 'stratops/build/util';

export function playerIndexOf(fen: Fen): PlayerIndex {
  return fen.split(' ')[1] === 'w' ? 'p1' : 'p2';
}

export function winnerOf(fen: Fen, move: TablebaseMoveStats): PlayerIndex | undefined {
  const stm = playerIndexOf(fen);
  if (move.checkmate || move.variant_loss || move.wdl! < 0) return stm;
  if (move.variant_win || move.wdl! > 0) return opposite(stm);
  return undefined;
}
