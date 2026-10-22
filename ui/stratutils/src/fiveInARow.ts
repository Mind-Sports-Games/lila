import type { Piece as CGPiece, PlayerIndex as CGPlayerIndex, Role as CGRole } from 'chessground/types';

// board nextColour blackSeat openingStep fullMoves, e.g. "15/.../15 b 1 o 1"; pente adds each seat's
// captures before the full moves. A stone keeps its colour; whichever seat holds black can change with a
// swap, so the seat to move is read from the colour of the next stone and the black seat, not from the
// turn character.

export const variants: VariantKey[] = ['gomoku', 'pente', 'renju'];

export const isFiveInARow = (variant: VariantKey | string | undefined): boolean =>
  variants.includes(variant as VariantKey);

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

export const stonesOnBoard = (fen: string): number => (field(fen, 0)?.match(/[BW]/g) ?? []).length;

// renju's Taraguchi-10 restricts the first five stones: with n down, the next goes within n points of the
// centre, the (2n + 1) square about it; undefined once the next stone may go anywhere
export function placementZone(fen: string): number | undefined {
  const step = openingStep(fen),
    placed = stonesOnBoard(fen);
  return (step === 'p' || step === 'q') && placed < 5 ? placed : undefined;
}

// renju offers the other seat a swap after each of the first five stones
export const swapFollowsDrop = (fen: string): boolean => placementZone(fen) !== undefined;

// one seat drops both colours while the opening and the swap2 pair are being placed, and in renju white
// picks one of the offered stones before black's fifth is on the board
export function seatToMove(fen: string): CGPlayerIndex {
  const step = openingStep(fen);
  if (step === 'o') return 'p1';
  if (step === 's') return 'p2';
  const seatOfNext = nextStone(fen) === 'b-piece' ? blackSeat(fen) : opposite(blackSeat(fen));
  return step === 'k' ? opposite(seatOfNext) : seatOfNext;
}

export function dropPiece(fen: string, playerIndex: CGPlayerIndex): CGPiece {
  return { role: nextStone(fen), playerIndex };
}

// the actions besides a drop that the opening offers
export type OpeningAction = 'swap' | 'swap2' | 'offer10';

export function offers(action: OpeningAction, fen: string): boolean {
  if (action === 'swap') return canSwap(fen);
  if (action === 'swap2') return canSwap2(fen);
  return canOffer10(fen);
}

export function canSwap(fen: string): boolean {
  const step = openingStep(fen);
  return step === 'c' || step === 'f' || step === 'q';
}

export function canSwap2(fen: string): boolean {
  return openingStep(fen) === 'c';
}

// renju's second choice once four stones are down: offer ten fifth stones instead of dropping one
export function canOffer10(fen: string): boolean {
  const step = openingStep(fen);
  return (step === 'p' || step === 'q') && stonesOnBoard(fen) === 4;
}

export function offering(fen: string): boolean {
  return openingStep(fen) === 'x';
}

// an offered stone is a lowercase b, beside the board until white picks one
export function stonesStillToOffer(fen: string): number {
  return 10 - (field(fen, 0)?.match(/b/g) ?? []).length;
}

export function canPick(fen: string): boolean {
  return openingStep(fen) === 'k';
}

// the colour a seat currently plays: a swap hands black to the other seat
export function seatColor(fen: string, playerIndex: CGPlayerIndex): PlayerColor {
  return blackSeat(fen) === playerIndex ? 'black' : 'white';
}

// the board classes that draw renju's placement zone for the position shown, each zone size its own class
export function zoneClasses(variant: string, fen: string): Record<string, boolean> {
  const zone = variant === 'renju' ? placementZone(fen) : undefined;
  const classes: Record<string, boolean> = {};
  for (let n = 0; n < 5; n++) classes[`renju-zone-${n}`] = zone === n;
  return classes;
}

// redraw the player colour icons a swap has changed, given each seat's icon element
export function setSeatColorIcon(el: Element | null | undefined, fen: string, playerIndex: CGPlayerIndex): void {
  if (!el) return;
  el.classList.remove('black', 'white');
  el.classList.add(seatColor(fen, playerIndex));
}
