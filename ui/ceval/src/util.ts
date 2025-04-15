const noClientEvalVariants = [
  'monster',
  'linesOfAction',
  'scrambledEggs',
  'international',
  'antidraughts',
  'breakthrough', // BKRTHRU
  'russian',
  'brazilian',
  'pool',
  'portuguese',
  'english',
  'fromPositionDraughts',
  'frisian',
  'frysk',
  'amazons',
  'minibreakthroughtroyka', // Note: it looks like fairySF decided the board starts from a5, thus it's computations seem to be wrong
  'oware',
  'togyzkumalak',
  'bestemshe',
  'go9x9',
  'go13x13',
  'go19x19',
  'backgammon',
  'hyper',
  'nackgammon',
  'abalone',
];

const disallowPathVizualizationVariants: VariantKey[] = ['shogi', 'minishogi', 'xiangqi', 'flipello10'];

const noVariantOutcomeVariants: VariantKey[] = [
  'minishogi',
  'shogi',
  'minixiangqi',
  'xiangqi',
  'flipello',
  'flipello10',
];

export function isEvalBetter(a: Tree.ClientEval, b?: Tree.ClientEval): boolean {
  return !b || a.depth > b.depth || (a.depth === b.depth && a.nodes > b.nodes);
}

export function renderEval(e: number): string {
  e = Math.max(Math.min(Math.round(e / 10) / 10, 99), -99);
  return (e > 0 ? '+' : '') + e.toFixed(1);
}

export function sanIrreversible(variant: VariantKey, san: string): boolean {
  if (san.startsWith('O-O')) return true;
  if (variant === 'crazyhouse') return false;
  if (san.includes('x')) return true; // capture
  if (san.toLowerCase() === san) return true; // pawn move
  return (variant === 'threeCheck' || variant === 'fiveCheck') && san.includes('+');
}

export function allowedForVariant(variant: VariantKey) {
  return noClientEvalVariants.indexOf(variant) == -1;
}

export function allowPv(variant: VariantKey) {
  return disallowPathVizualizationVariants.indexOf(variant) == -1;
}

export function noVariantOutcome(variantKey: VariantKey): boolean {
  return noVariantOutcomeVariants.indexOf(variantKey) != -1;
}
