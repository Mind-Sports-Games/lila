import type { PlayerIndex as CGPlayerIndex, Role as CGRole } from 'chessground/types';

// board[pocket] turn p1Score p2Score round fullMoves, e.g. "7/7/7/7/7/7/7[R] w 0 0 1 1"
const ROUNDS = 2;

interface EntropyFen {
  pocket: string;
  turn: CGPlayerIndex;
  round: number;
}

function readFen(fen: string): EntropyFen | undefined {
  const parts = fen.split(' ');
  const pocket = parts[0]?.match(/\[([^\]]*)\]/);
  const round = Number(parts[4]);
  if (!pocket || parts.length < 5 || isNaN(round)) return undefined;
  return { pocket: pocket[1], turn: parts[1] === 'b' ? 'p2' : 'p1', round };
}

export function chaosPlayer(round: number): CGPlayerIndex {
  return round === 1 ? 'p1' : 'p2';
}

export function isChaosTurn(fen: string): boolean {
  const f = readFen(fen);
  return !!f && f.round <= ROUNDS && f.turn === chaosPlayer(f.round);
}

export function isOrderTurn(fen: string): boolean {
  const f = readFen(fen);
  return !!f && f.round <= ROUNDS && f.turn !== chaosPlayer(f.round);
}

// the counter Chaos holds, if it has already drawn this turn
export function counterInPocket(fen: string): CGRole | undefined {
  const f = readFen(fen);
  if (!f || f.pocket.length === 0) return undefined;
  const owned = [...f.pocket].find(c => (c === c.toUpperCase()) === (f.turn === 'p1'));
  return owned ? (`${owned.toLowerCase()}-piece` as CGRole) : undefined;
}

export function mustDraw(fen: string): boolean {
  return isChaosTurn(fen) && counterInPocket(fen) === undefined;
}

export function round(fen: string): number | undefined {
  return readFen(fen)?.round;
}

const COUNTERS_PER_COLOUR = 7;
export const roles: CGRole[] = ['w-piece', 'k-piece', 'y-piece', 'g-piece', 'r-piece', 'b-piece', 'p-piece'];

// what is left to draw, derived as strategygames derives it: seven of each colour, less those on the
// board and those held. A full board before a draw is a finished round still standing, and the
// draw clears it, so the bag is full again.
export function bag(fen: string): Map<CGRole, number> {
  const board = fen.split(/[[ ]/)[0] ?? '';
  const pocket = readFen(fen)?.pocket ?? '';
  const onBoard = board.replace(/[\d/]/g, '');
  const standingFullBoard = onBoard.length === 49 && pocket.length === 0;
  const used = standingFullBoard ? '' : onBoard + pocket;
  return new Map(
    roles.map(role => {
      const letter = role[0];
      const count = [...used].filter(c => c.toLowerCase() === letter).length;
      return [role, Math.max(COUNTERS_PER_COLOUR - count, 0)];
    }),
  );
}
