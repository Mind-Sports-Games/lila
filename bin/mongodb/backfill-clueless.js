// $ mongosh <db> backfill-clueless.js
//
// One-off backfill: `clueless` was added as a field on `ranking` docs after the
// collection was already populated in production (RankingApi.save(), modules/user/src/main/RankingApi.scala),
// so pre-existing docs are missing it. Leaderboard queries filter on
// `clueless != true`, so docs missing the field must be backfilled or they'll be
// silently excluded from every leaderboard.
//
// `clueless` mirrors Perf.clueless in the app: the player's *live* (time-decayed)
// Glicko-2 deviation is >= 230. "Live" means decayed for elapsed time since their
// last game in that perf, not the raw deviation stored in user4.perfs.<key>.gl.d
// (see Glicko.liveDeviation / Perf.perfBSONHandler.reads in
// modules/rating/src/main/Glicko.scala + Perf.scala) - a player who hasn't played
// in a long time has a materially higher effective deviation than what's stored.
//
// The decay formula below is ported verbatim from
// org.goochjs.glicko2.RatingCalculator (calculateNewRD / previewDeviation),
// disassembled from the compiled classes to confirm exactness (javap -c), and
// matches modules/rating/src/main/Glicko.scala's ratingPeriodsPerDay /
// minDeviation / maxDeviation constants. Do not hand-derive a different formula
// here without re-checking against that class - the sign/scale is easy to get
// subtly wrong.
//
// Run with the site down / no games being saved concurrently, so nothing races
// this script's read-then-write per document.

const CLUELESS_DEVIATION = 230;
const MIN_DEVIATION = 45;
const MAX_DEVIATION = 500;
const RATING_PERIODS_PER_DAY = 0.21436;
const GLICKO_SCALE = 173.7178;
const MS_PER_DAY = 86400000;

// ranking.perf (numeric PerfType id) -> user4.perfs.<key>
// Dumped from lila.rating.PerfType.all in the running app (`sbt "rating/console"`,
// `PerfType.all.map(pt => pt.id -> pt.key)`), then the puzzle_* entries were
// removed - puzzle perfs don't appear on rating leaderboards, so they're left
// out on purpose. Don't hand-edit the rest; re-dump if perf types change.
const PERF_KEYS = {
  0: 'ultraBullet',
  1: 'bullet',
  2: 'blitz',
  6: 'rapid',
  3: 'classical',
  4: 'correspondence',
  5: 'standard',
  18: 'crazyhouse',
  11: 'chess960',
  12: 'kingOfTheHill',
  15: 'threeCheck',
  19: 'fiveCheck',
  13: 'antichess',
  14: 'atomic',
  16: 'horde',
  17: 'racingKings',
  20: 'noCastling',
  23: 'monster',
  21: 'linesOfAction',
  22: 'scrambledEggs',
  105: 'international',
  111: 'frisian',
  116: 'frysk',
  113: 'antidraughts',
  117: 'breakthrough',
  122: 'russian',
  123: 'brazilian',
  124: 'pool',
  125: 'portuguese',
  126: 'english',
  800: 'dameo',
  200: 'shogi',
  202: 'minishogi',
  201: 'xiangqi',
  203: 'minixiangqi',
  204: 'flipello',
  205: 'flipello10',
  210: 'antiflipello',
  211: 'octagonflipello',
  206: 'amazons',
  208: 'breakthroughtroyka',
  209: 'minibreakthroughtroyka',
  300: 'oware',
  400: 'togyzkumalak',
  401: 'bestemshe',
  502: 'go19x19',
  501: 'go13x13',
  500: 'go9x9',
  600: 'backgammon',
  601: 'nackgammon',
  602: 'hyper',
  700: 'abalone',
  701: 'grandabalone',
};

// Glicko.liveDeviation(reverse = false), ported from RatingCalculator.previewDeviation
// + calculateNewRD: newPhi = sqrt(phi^2 + t*sigma^2) on the Glicko-2 internal scale
// (phi = d / 173.7178), converted back to the original scale and clamped.
function liveDeviation(d, v, lastPlayedDate) {
  const days = (Date.now() - lastPlayedDate.getTime()) / MS_PER_DAY;
  const t = days * RATING_PERIODS_PER_DAY;
  const phi = d / GLICKO_SCALE;
  const newD = Math.sqrt(phi * phi + t * v * v) * GLICKO_SCALE;
  return Math.min(Math.max(newD, MIN_DEVIATION), MAX_DEVIATION);
}

let seen = 0;
let updated = 0;
let defaulted = 0; // no matching user/perf found - set clueless:false so the doc isn't stuck unmatched forever

db.ranking.find({ clueless: { $exists: false } }).forEach(function (r) {
  seen++;

  const userId = r._id.split(':')[0];
  const key = PERF_KEYS[String(r.perf)];
  const user = key && db.user4.findOne({ _id: userId }, { [`perfs.${key}`]: 1 });
  const perf = user && user.perfs && user.perfs[key];

  let clueless = false;
  if (perf && perf.gl && perf.la) {
    clueless = liveDeviation(perf.gl.d, perf.gl.v, perf.la) >= CLUELESS_DEVIATION;
  } else {
    defaulted++;
  }

  db.ranking.updateOne({ _id: r._id }, { $set: { clueless: clueless } });
  updated++;
});

print(`seen=${seen} updated=${updated} defaulted(no matching user/perf)=${defaulted}`);
