//total game count by lib and variant
db.game5.aggregate([
  { $match: { l: { $exists: true } } }, // old games dont have library (pre Aug 2021)
  {
    $project: {
      l: 1,
      v: 1,
    },
  },
  {
    $group: {
      _id: {
        lib: '$l',
        variant: '$v',
      },
      count: { $sum: 1 },
    },
  },
  { $sort: { count: -1 } },
]);

//total game count per month by lib and varaint
db.game5.aggregate([
  {
    $project: {
      date: {
        month: { $month: '$ca' },
        year: { $year: '$ca' },
      },
      l: 1,
      v: 1,
    },
  },
  {
    $group: {
      _id: {
        date: '$date',
        lib: '$l',
        variant: '$v',
      },
      count: { $sum: 1 },
    },
  },
  { $sort: { '_id.date.year': -1, '_id.date.month': -1, count: -1 } },
]);

//total game count over time by matched games (lib and varaint)
db.game5.aggregate([
  { $match: { l: 0, v: 5 } },
  {
    $project: {
      date: {
        day: { $dayOfMonth: '$ca' },
        month: { $month: '$ca' },
        year: { $year: '$ca' },
      },
    },
  },
  {
    $group: {
      _id: {
        date: '$date',
      },
      count: { $sum: 1 },
    },
  },
  { $sort: { '_id.date.year': 1, '_id.date.month': 1, '_id.date.day': 1 } },
]);

//total game count per month
db.game5.aggregate([
  {
    $project: {
      date: {
        month: { $month: '$ca' },
        year: { $year: '$ca' },
      },
    },
  },
  {
    $group: {
      _id: {
        date: '$date',
      },
      count: { $sum: 1 },
    },
  },
  { $sort: { '_id.date.year': -1, '_id.date.month': -1 } },
]);

//user games simple stats (ammend find for specific user)
db.user4.find().forEach(function (user) {
  var games = db.game5;
  var uid = user._id;
  var details = {
    name: uid,
    game_count: games.count({ us: uid }),
    chess_games: games.count({ us: uid, l: { $eq: 0 } }),
    draughts_games: games.count({ us: uid, l: { $eq: 1 } }),
    fairy_games: games.count({ us: uid, l: { $eq: 2 } }),
    ai: games.count({ us: uid, $or: [{ 'p0.ai': { $exists: true } }, { 'p1.ai': { $exists: true } }] }),
    rated: games.count({ us: uid, ra: true }),
  };
  printjson(details);
});

//users games per variant
print(db.user4.count() + ' Users');
db.user4.find().forEach(function (user) {
  var games = db.game5;
  var uid = user._id;
  var details = {
    name: uid,
    game_count: games.count({ us: uid }),
    chess_games: games.count({ us: uid, l: { $eq: 0 } }),
    chess_standard_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 1 } }),
    chess_chess960_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 2 } }),
    chess_frompos_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 3 } }),
    chess_kingofthehill_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 4 } }),
    chess_3check_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 5 } }),
    chess_antichess_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 6 } }),
    chess_atomic_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 7 } }),
    chess_horde_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 8 } }),
    chess_racingkings_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 9 } }),
    chess_crazy_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 10 } }),
    chess_loa_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 11 } }),
    chess_5check_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 12 } }),
    chess_nocastling_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 13 } }),
    chess_scrambledEggs_games: games.count({ us: uid, l: { $eq: 0 }, v: { $eq: 14 } }),
    draughts_games: games.count({ us: uid, l: { $eq: 1 } }),
    draughts_standard_games: games.count({ us: uid, l: { $eq: 1 }, v: { $eq: 1 } }),
    draughts_frompos_games: games.count({ us: uid, l: { $eq: 1 }, v: { $eq: 3 } }),
    draughts_antidraughts_games: games.count({ us: uid, l: { $eq: 1 }, v: { $eq: 6 } }),
    draughts_frysk_games: games.count({ us: uid, l: { $eq: 1 }, v: { $eq: 8 } }),
    draughts_breakthrough_games: games.count({ us: uid, l: { $eq: 1 }, v: { $eq: 9 } }),
    draughts_frisian_games: games.count({ us: uid, l: { $eq: 1 }, v: { $eq: 10 } }),
    draughts_russian_games: games.count({ us: uid, l: { $eq: 1 }, v: { $eq: 11 } }),
    draughts_brazilian_games: games.count({ us: uid, l: { $eq: 1 }, v: { $eq: 12 } }),
    draughts_pool_games: games.count({ us: uid, l: { $eq: 1 }, v: { $eq: 13 } }),
    fairy_games: games.count({ us: uid, l: { $eq: 2 } }),
    shogi_games: games.count({ us: uid, l: { $eq: 2 }, v: { $eq: 1 } }),
    xiangqi_games: games.count({ us: uid, l: { $eq: 2 }, v: { $eq: 2 } }),
    mini_xiangqi_games: games.count({ us: uid, l: { $eq: 2 }, v: { $eq: 4 } }),
    mini_shogi_games: games.count({ us: uid, l: { $eq: 2 }, v: { $eq: 5 } }),
    flipello_games: games.count({ us: uid, l: { $eq: 2 }, v: { $eq: 6 } }),
    oware_games: games.count({ us: uid, l: { $eq: 3 }, v: { $eq: 1 } }),
    win: games.count({ wid: uid }),
    loss: games.count({ us: uid, s: { $in: [30, 31, 35, 33] }, wid: { $ne: uid } }),
    draw: games.count({ us: uid, s: { $in: [34, 32] } }),
    ai: games.count({ us: uid, $or: [{ 'p0.ai': { $exists: true } }, { 'p1.ai': { $exists: true } }] }),
    rated: games.count({ us: uid, ra: true }),
  };
  printjson(details);
});

//most games played by user (top 10)
db.user4.find({}, { 'count.game': 1 }).sort({ 'count.game': -1 }).limit(10);

//most time players by user (top 10)
db.user4.find({}, { 'time.total': 1 }).sort({ 'time.total': -1 }).limit(10);

//Users over time ( added each month)
print(db.user4.count() + ' total users');
db.user4.aggregate([
  {
    $match: {
      "enabled": true,
      "count.game" : {$gt : 0}
    }
  },
  {
    $project: {
      date: {
        month: { $month: '$createdAt' },
        year: { $year: '$createdAt' },
      },
    },
  },
  {
    $group: {
      _id: {
        date: '$date',
      },
      count: { $sum: 1 },
    },
  },
  { $sort: { '_id.date.year': -1, '_id.date.month': -1, count: -1 } },
]);

//users time joined vs time playing (scatter plot)
db.user4.find().forEach(function (user) {
  var uid = user._id;
  var details = {
    name: uid,
    playing_time_seconds: user.time.total,
    account_age_days: (Date.now() - user.createdAt) / 1000 / 3600 / 24,
  };
  printjson(details);
});
