// $ mongosh lichess set-ratings-bots.js

const perfs = [
  'standard',
  'rapid',
  'blitz',
  'correspondence',
  'bullet',
  'ultraBullet',
  'classical',
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
  'shogi',
  'minishogi',
  'xiangqi',
  'minixiangqi',
  'flipello',
  'flipello10',
  'breakthroughtroyka',
  'minibreakthroughtroyka',
];

const ratings = {
  'stockfish-level1': 800,
  'stockfish-level2': 1100,
  'stockfish-level3': 1400,
  'stockfish-level4': 1700,
  'stockfish-level5': 2000,
  'stockfish-level6': 2300,
  'stockfish-level7': 2700,
  'stockfish-level8': 3000,
};

for (k of Object.keys(ratings)) {
  const rating = ratings[k];
  const id = k.toLowerCase();
  const user = db.user4.findOne({ _id: id });
  perfs.forEach(perf => {
    if (user.perfs[perf] && user.perfs[perf].nb) {
      const set = { [`perfs.${perf}.gl.r`]: rating };
      const push = {
        [`perfs.${perf}.re`]: {
          $each: [NumberInt(rating)],
          $position: 0,
        },
      };
      db.user4.updateOne({ _id: id }, { $set: set, $push: push });
    } else {
      db.user4.updateOne(
        { _id: id },
        {
          $set: {
            [`perfs.${perf}`]: {
              gl: {
                r: rating,
                d: 150,
                v: 0.06,
              },
              nb: NumberInt(0),
              re: [],
            },
          },
        },
      );
    }
  });
}
