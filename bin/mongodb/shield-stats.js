//Number of shield tournaments
db.tournament2
  .find({ 'schedule.freq': 'shield', startsAt: { $gte: ISODate('2024-10-01'), $lt: ISODate('2024-11-01') } })
  .count();

//Number of games played
db.tournament2.aggregate([
  {
    $match: {
      $and: [{ 'schedule.freq': 'shield' }, { startsAt: { $gte: ISODate('2024-10-01'), $lt: ISODate('2024-11-01') } }],
    },
  },
  { $project: { fField: { $concat: ['tournament:stats:', '$_id'] } } },
  { $lookup: { from: 'cache', localField: 'fField', foreignField: '_id', as: 'stats' } },
  { $unwind: '$stats' },
  { $group: { _id: null, count: { $sum: '$stats.v.games' } } },
]);

//Bot games played
db.tournament_leaderboard.aggregate([
  { $lookup: { from: 'tournament2', localField: 't', foreignField: '_id', as: 'tour' } },
  { $unwind: '$tour' },
  {
    $match: {
      $and: [
        { 'tour.schedule.freq': 'shield' },
        { 'tour.startsAt': { $gte: ISODate('2024-10-01'), $lt: ISODate('2024-11-01') } },
        { u: 'pst-greedy-tom' },
      ],
    },
  },
  { $group: { _id: null, count: { $sum: '$g' } } },
]);

//Shields with bots
db.tournament_leaderboard.aggregate([
  { $lookup: { from: 'tournament2', localField: 't', foreignField: '_id', as: 'tour' } },
  { $unwind: '$tour' },
  {
    $match: {
      $and: [
        { 'tour.schedule.freq': 'shield' },
        { 'tour.startsAt': { $gte: ISODate('2024-10-01'), $lt: ISODate('2024-11-01') } },
        { u: 'pst-greedy-tom' },
      ],
    },
  },
  { $count: 'shields-pst-greedy-tom-played' },
]);
