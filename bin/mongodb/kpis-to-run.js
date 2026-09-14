//total game count by lib and variant, excluding our bot-vs-bot streams (TAB - Games per variant)
db.game5.aggregate([
  {
    $match: {
      l: { $exists: true }, // old games dont have library (pre Aug 2021)
      so: { $ne: 14 }, // exclude lila.game.Source.BotVsBotStream (modules/game/src/main/Source.scala)
    },
  },
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

//total game count per month, excluding our bot-vs-bot streams (TAB - Games)
db.game5.aggregate([
  { $match: { so: { $ne: 14 } } }, // exclude lila.game.Source.BotVsBotStream (modules/game/src/main/Source.scala)
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

//game count per month broken down by human_vs_human / human_vs_bot / bot_vs_bot (TAB - Games)
//(mirrors modules/common/src/main/LightUser.scala psBotsIDs - keep in sync)
const PS_BOT_IDS = [
  'pst-greedy-tom',
  'ps-greedy-one-move',
  'ps-greedy-two-move',
  'ps-greedy-four-move',
  'stockfish-level1',
  'stockfish-level2',
  'stockfish-level3',
  'stockfish-level4',
  'stockfish-level5',
  'stockfish-level6',
  'stockfish-level7',
  'stockfish-level8',
  'ps-random-mover',
  'gnubg-level1',
  'gnubg-level2',
  'gnubg-level3',
  'gnubg-level4',
  'gnubg-level5',
  'gnubg-level6',
  'gnubg-level7',
  'gnubg-level8',
];
db.game5.aggregate([
  {
    $project: {
      date: {
        month: { $month: '$ca' },
        year: { $year: '$ca' },
      },
      isAutoBotVsBot: { $eq: ['$so', 14] },
      p1IsPsBot: { $in: [{ $arrayElemAt: ['$us', 0] }, PS_BOT_IDS] },
      p2IsPsBot: { $in: [{ $arrayElemAt: ['$us', 1] }, PS_BOT_IDS] },
    },
  },
  {
    $group: {
      _id: {
        date: '$date',
        category: {
          $switch: {
            branches: [
              { case: '$isAutoBotVsBot', then: 'bot_vs_bot' },
              { case: { $or: ['$p1IsPsBot', '$p2IsPsBot'] }, then: 'human_vs_bot' },
            ],
            default: 'human_vs_human',
          },
        },
      },
      count: { $sum: 1 },
    },
  },
  { $sort: { '_id.date.year': -1, '_id.date.month': -1, '_id.category': 1 } },
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
