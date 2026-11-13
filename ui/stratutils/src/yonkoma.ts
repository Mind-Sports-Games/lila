// Yonkoma (Mark Steere, 2026): two checkers a side on a 10x10 or 12x12 checkerboard; a player
// with no legal move has the turn skipped, a pass the server plays for them

export const variants: VariantKey[] = ['yonkoma10x10', 'yonkoma12x12'];

export const isYonkoma = (variant: VariantKey | string | undefined): boolean =>
  variants.includes(variant as VariantKey);
