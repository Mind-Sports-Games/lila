//total game count by lib and variant (TAB - Games per variant)
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

//total game count per month (TAB - Games)
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

//Users over time (TAB - users)
//print(db.user4.count() + ' total users');
db.user4.aggregate([
  {
    $project: {
      date: {
        month: { $month: '$createdAt' },
        year: { $year: '$createdAt' },
      },
      user_games: {
        $cond: [{ $gt: ['$count.game', 0] }, 1, 0],
      },
      user_enabled: {
        $cond: ['$enabled', 1, 0],
      },
    },
  },
  {
    $group: {
      _id: {
        date: '$date',
      },
      count_total: { $sum: 1 },
      count_enabled: { $sum: '$user_enabled' },
      count_played_games: { $sum: '$user_games' },
    },
  },
  { $sort: { '_id.date.year': -1, '_id.date.month': -1, count_total: -1 } },
]);

//find donations (TAB - users)
db.plan_charge.aggregate([
  {
    $match: {
      userId: { $exists: true },
    },
  },
  {
    $group: {
      _id: {
        year: { $year: '$date' },
        month: { $month: '$date' },
      },
      totalCents: { $sum: '$cents' },
      count: { $sum: 1 },
    },
  },
  {
    $sort: { '_id.year': 1, '_id.month': 1 },
  },
]);

//Shield stats are in shield-stats.js (change dates before running)
//WIG data is output on file in server wigdata/wigdata.log
