const noClientEvalVariants = [
  'monster',
  'linesOfAction',
  'scrambledEggs',
  'international',
  'antidraughts',
  'breakthrough',
  'russian',
  'brazilian',
  'pool',
  'portuguese',
  'english',
  'fromPositionDraughts',
  'frisian',
  'frysk',
  'shogi',
  'xiangqi',
  'minishogi',
  'flipello',
  'flipello10',
  'amazons',
  'minibreakthroughtroyka',
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

export function allowClientEvalForVariant(variant: VariantKey) {
  return noClientEvalVariants.indexOf(variant) == -1;
}
