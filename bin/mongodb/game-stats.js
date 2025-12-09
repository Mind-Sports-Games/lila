//Games played by month
db.game5.aggregate([
  { $project: { month: { $month: '$ca' }, year: { $year: '$ca' } } },
  { $group: { _id: { year: '$year', month: '$month' }, games: { $sum: 1 } } },
  { $sort: { _id: 1 } },
]);

//Games played by variant:
db.game5.aggregate([
  { $match: { l: { $exists: true } } },
  { $project: { lib: '$l', variant: '$v' } },
  { $group: { _id: { lib: '$lib', variant: '$variant' }, games: { $sum: 1 } } },
  { $sort: { games: -1 } },
]);

//Improvements:
//Include games from before 'l' (lib) was set (all of game family chess, includes some LOA)
//Join on some table list of variant ids:
//
//src/main/scala/chess/variant/Antichess.scala:      id = 6,
//src/main/scala/chess/variant/Atomic.scala:      id = 7,
//src/main/scala/chess/variant/Chess960.scala:      id = 2,
//src/main/scala/chess/variant/Crazyhouse.scala:      id = 10,
//src/main/scala/chess/variant/FiveCheck.scala:      id = 12,
//src/main/scala/chess/variant/FromPosition.scala:      id = 3,
//src/main/scala/chess/variant/Horde.scala:      id = 8,
//src/main/scala/chess/variant/KingOfTheHill.scala:      id = 4,
//src/main/scala/chess/variant/LinesOfAction.scala:      id = 11,
//src/main/scala/chess/variant/Monster.scala:      id = 15,
//src/main/scala/chess/variant/NoCastling.scala:      id = 13,
//src/main/scala/chess/variant/RacingKings.scala:      id = 9,
//src/main/scala/chess/variant/ScrambledEggs.scala:      id = 14,
//src/main/scala/chess/variant/Standard.scala:      id = 1,
//src/main/scala/chess/variant/ThreeCheck.scala:      id = 5,
//src/main/scala/draughts/variant/Antidraughts.scala:      id = 6,
//src/main/scala/draughts/variant/Brazilian.scala:      id = 12,
//src/main/scala/draughts/variant/Breakthrough.scala:      id = 9,
//src/main/scala/draughts/variant/English.scala:      id = 15,
//src/main/scala/draughts/variant/Frisian.scala:      id = 10,
//src/main/scala/draughts/variant/FromPosition.scala:      id = 3,
//src/main/scala/draughts/variant/Frysk.scala:      id = 8,
//src/main/scala/draughts/variant/Pool.scala:      id = 13,
//src/main/scala/draughts/variant/Portuguese.scala:      id = 14,
//src/main/scala/draughts/variant/Russian.scala:      id = 11,
//src/main/scala/draughts/variant/Standard.scala:      id = 1,
//src/main/scala/fairysf/variant/Flipello.scala:      id = 6,
//src/main/scala/fairysf/variant/Flipello10.scala:      id = 7,
//src/main/scala/fairysf/variant/MiniShogi.scala:      id = 5,
//src/main/scala/fairysf/variant/MiniXiangqi.scala:      id = 4,
//src/main/scala/fairysf/variant/Shogi.scala:      id = 1,
//src/main/scala/fairysf/variant/Xiangqi.scala:      id = 2,
//src/main/scala/samurai/variant/Oware.scala:      id = 1,
//src/main/scala/togyzkumalak/variant/Togyzkumalak.scala:      id = 1,
//src/main/scala/backgammon/variant/Backgammon.scala:      id = 1,
//src/main/scala/abalone/variant/Abalone.scala:      id = 1,
//src/main/scala/dameo/variant/Dameo.scala:      id = 1,
