// One-off seed: move the two standing bot streams, previously hard-coded in
// BotVsBotConfig.streams, into the bot_stream collection that the mod page manages.
//
// Variant lists were taken from BotVsBotConfig.variantGroups on 2026-10-30. A game
// released after that is added from the bot stream page, not here.

const stockfishVariants = [
  'standard',
  'crazyhouse',
  'chess960',
  'kingOfTheHill',
  'threeCheck',
  'fiveCheck',
  'antichess',
  'atomic',
  'horde',
  'racingKings',
  'noCastling',
  'shogi',
  'minishogi',
  'xiangqi',
  'minixiangqi',
  'flipello',
  'flipello10',
  'octagonflipello',
  'breakthroughtroyka',
  'minibreakthroughtroyka',
];

const nonStockfishOrBackgammonVariants = [
  'monster',
  'linesOfAction',
  'scrambledEggs',
  'international',
  'frisian',
  'frysk',
  'antidraughts',
  'breakthrough',
  'russian',
  'brazilian',
  'pool',
  'portuguese',
  'english',
  'dameo',
  'antiflipello',
  'amazons',
  'oware',
  'togyzkumalak',
  'bestemshe',
  'go19x19',
  'go13x13',
  'go9x9',
  'abalone',
  'grandabalone',
  'entropy',
];

const backgammonVariants = ['backgammon', 'nackgammon', 'hyper'];

const now = new Date();

const stream = (id, name, matchups) => ({
  _id: id,
  name,
  matchups,
  clock: { t: 'fischer', limit: 180, increment: 2 },
  priority: false,
  active: true,
  createdBy: 'playstrategy',
  createdAt: now,
  updatedAt: now,
});

db.bot_stream.createIndex({ name: 1 }, { unique: true });

db.bot_stream.insertMany([
  stream('sfeight7', 'Stockfish-8 vs Stockfish-7', [
    { p1: 'stockfish-level8', p2: 'stockfish-level7', variants: stockfishVariants },
  ]),
  stream('greedytw', 'Greedy-Two-Move vs Stockfish-3/One-Move/GNUBG-3', [
    { p1: 'ps-greedy-two-move', p2: 'stockfish-level3', variants: stockfishVariants },
    { p1: 'ps-greedy-two-move', p2: 'ps-greedy-one-move', variants: nonStockfishOrBackgammonVariants },
    { p1: 'ps-greedy-two-move', p2: 'gnubg-level3', variants: backgammonVariants },
  ]),
]);
