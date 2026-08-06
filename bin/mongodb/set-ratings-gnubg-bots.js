// $ mongosh lichess set-ratings-bots.js

const perfs = ['backgammon', 'nackgammon', 'hyper'];

const ratings = {
  'gnubg-level1': 800,
  'gnubg-level2': 1100,
  'gnubg-level3': 1400,
  'gnubg-level4': 1700,
  'gnubg-level5': 2000,
  'gnubg-level6': 2300,
  'gnubg-level7': 2700,
  'gnubg-level8': 3000,
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
