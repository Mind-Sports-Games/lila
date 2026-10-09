import type { Piece as CGPiece, PlayerIndex as CGPlayerIndex, Role as CGRole } from 'chessground/types';

// board nextColour blackSeat openingStep fullMoves, e.g. "15/.../15 b 1 o 1"
// A stone keeps its colour; whichever seat holds black can change with a swap, so the seat to move
// is read from the colour of the next stone and the black seat, not from the turn character.

export const variants: VariantKey[] = ['gomoku'];

export const isGomoku = (variant: VariantKey | string | undefined): boolean => variants.includes(variant as VariantKey);

const field = (fen: string, index: number): string | undefined => fen.split(' ')[index];

const opposite = (p: CGPlayerIndex): CGPlayerIndex => (p === 'p1' ? 'p2' : 'p1');

export function nextStone(fen: string): CGRole {
  return field(fen, 1) === 'w' ? 'w-piece' : 'b-piece';
}

export function blackSeat(fen: string): CGPlayerIndex {
  return field(fen, 2) === '2' ? 'p2' : 'p1';
}

export function openingStep(fen: string): string {
  return field(fen, 3) ?? '-';
}

// one seat drops both colours while the opening and the swap2 pair are being placed
export function seatToMove(fen: string): CGPlayerIndex {
  const step = openingStep(fen);
  if (step === 'o') return 'p1';
  if (step === 's') return 'p2';
  return nextStone(fen) === 'b-piece' ? blackSeat(fen) : opposite(blackSeat(fen));
}

export function dropPiece(fen: string, playerIndex: CGPlayerIndex): CGPiece {
  return { role: nextStone(fen), playerIndex };
}

export function canSwap(fen: string): boolean {
  const step = openingStep(fen);
  return step === 'c' || step === 'f';
}

export function canSwap2(fen: string): boolean {
  return openingStep(fen) === 'c';
}

// the colour a seat currently plays: a swap hands black to the other seat
export function seatColor(fen: string, playerIndex: CGPlayerIndex): PlayerColor {
  return blackSeat(fen) === playerIndex ? 'black' : 'white';
}

// redraw the player colour icons a swap has changed, given each seat's icon element
export function setSeatColorIcon(el: Element | null | undefined, fen: string, playerIndex: CGPlayerIndex): void {
  if (!el) return;
  el.classList.remove('black', 'white');
  el.classList.add(seatColor(fen, playerIndex));
}
