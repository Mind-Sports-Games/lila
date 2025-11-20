export function canUseBoardEditor(variantKey: VariantKey): boolean {
  return [
    'standard',
    'chess960',
    'fromPosition',
    'antichess',
    'kingOfTheHill',
    'threeCheck',
    'fiveCheck',
    'atomic',
    'horde',
    'racingKings',
    'crazyhouse',
    'noCastling',
    'monster',
    'linesOfAction',
    'scrambledEggs',
    'breakthroughtroyka',
    'minibreakthroughtroyka',
    'flipello',
    'flipello10',
    'antiflipello',
    'xiangqi',
    'minixiangqi',
    'amazons',
  ].includes(variantKey);
}

export function replacePocketsInFen(fen: string): string {
  return fen.replace('[', '/').replace(']', '');
}
