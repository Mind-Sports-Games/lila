use('lichess');

const variants = [
  'standard',
  'chess960',
  'kingOfTheHill',
  'threeCheck',
  'fiveCheck',
  'antichess',
  'atomic',
  'horde',
  'racingKings',
  'crazyhouse',
  'noCastling',
  'monster',
  'linesOfAction',
  'scrambledEggs',
  'frisian',
  'frysk',
  'international',
  'antidraughts',
  'breakthrough',
  'russian',
  'brazilian',
  'pool',
  'portuguese',
  'english',
  'dameo',
  'shogi',
  'xiangqi',
  'minishogi',
  'minixiangqi',
  'flipello',
  'flipello10',
  'antiflipello',
  'octagonflipello',
  'amazons',
  'breakthroughtroyka',
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

const chessSpeedFields = ['bullet', 'blitz', 'rapid', 'classical', 'correspondence'];

const facet = Object.fromEntries(
  variants.map(v => {
    const match =
      v === 'standard'
        ? { $match: { $or: chessSpeedFields.map(s => ({ [`perfs.${s}.nb`]: { $gt: 0 } })) } }
        : { $match: { [`perfs.${v}.nb`]: { $gt: 0 } } };
    return [v, [match, { $count: 'count' }]];
  }),
);

const project = Object.fromEntries(variants.map(v => [v, { $arrayElemAt: [`$${v}.count`, 0] }]));

const result = db.user4.aggregate([{ $facet: facet }, { $project: project }]).toArray()[0];

variants
  .map(v => ({ variant: v, count: result[v] ?? 0 }))
  .sort((a, b) => b.count - a.count)
  .forEach(({ variant, count }) => print(`${variant}\t${count}`));
