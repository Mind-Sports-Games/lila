// need to support multi action games in analysis
const noAnalysisBoardVariants: string[] = [
  'antidraughts',
  'brazilian',
  'breakthrough',
  'backgammon',
  'nackgammon',
  'english',
  'frisian',
  'frysk',
  'international',
  'pool',
  'portuguese',
  'russian',
];

// Means that the analysis board on playstrategy will work.
export function allowAnalysisForVariant(variant: VariantKey) {
  return noAnalysisBoardVariants.indexOf(variant) == -1;
}

// Means that it's a lichess variant, so everything works
const chessOnly: VariantKey[] = [
  'standard',
  'chess960',
  'antichess',
  'fromPosition',
  'kingOfTheHill',
  'threeCheck',
  'atomic',
  'horde',
  'racingKings',
  'crazyhouse',
];

export function isChess(variant: VariantKey) {
  return chessOnly.indexOf(variant) !== -1;
}

// Means that serverside fishnet analysis works.
const fishnetVariants: VariantKey[] = [
  'standard',
  'chess960',
  'antichess',
  'fromPosition',
  'kingOfTheHill',
  'threeCheck',
  'fiveCheck',
  'atomic',
  'horde',
  'racingKings',
  'crazyhouse',
  'noCastling',
  'shogi',
  'minishogi',
  'xiangqi',
  'minixiangqi',
  'flipello',
  'flipello10',
  'breakthroughtroyka',
  'minibreakthroughtroyka',
];

export function hasFishnet(variant: VariantKey) {
  return fishnetVariants.indexOf(variant) !== -1;
}
