// $ mongosh lichess set-ratings-gnubg-bots.js

// FIBS ratings derived from each bot's measured PR over 1pt games: 2100 - 32 * PR.
// Trailing numbers are those PRs; levels 2, 3 and 6 are interpolated. Sanity check:
// ps-greedy-two-move's PR of 24.4 predicts 1319 and it has earned 1299.
// Glicko reads these gaps ~5x more decisively than a 1pt game warrants, so keep games
// against these bots unrated, and re-run this script to re-peg them if they drift.
const standardRatings = {
  'gnubg-level1': 1075, // 32.03
  'gnubg-level2': 1236, // ~27.0
  'gnubg-level3': 1380, // ~22.5
  'gnubg-level4': 1511, // 18.40
  'gnubg-level5': 1777, // 10.08
  'gnubg-level6': 1879, // ~6.9
  'gnubg-level7': 1948, //  4.76
  'gnubg-level8': 2064, //  1.13
};

// Same shape at ~35% of the range - three checkers over ~15 moves leaves far less room
// for skill - and sized to the band our hyper players actually occupy. Judgement rather
// than measured: no hyper PRs yet, and they wouldn't be comparable to the above anyway.
// greedy-two-move's earned 1482 lands between levels 2 and 3, as it does at standard.
const hyperRatings = {
  'gnubg-level1': 1400,
  'gnubg-level2': 1457,
  'gnubg-level3': 1508,
  'gnubg-level4': 1554,
  'gnubg-level5': 1648,
  'gnubg-level6': 1685,
  'gnubg-level7': 1709,
  'gnubg-level8': 1750,
};

const ratingsByPerf = {
  backgammon: standardRatings,
  nackgammon: standardRatings,
  hyper: hyperRatings,
};

for (const [perf, ratings] of Object.entries(ratingsByPerf)) {
  Object.keys(ratings).forEach(k => {
    const rating = ratings[k];
    const id = k.toLowerCase();
    const user = db.user4.findOne({ _id: id });
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
