// One-off backfill: relabel historical bot-vs-bot stream games from
// Source.Friend (2) to the new Source.BotVsBotStream (14)
// (modules/game/src/main/Source.scala).
//
// IMPORTANT: only run this AFTER the BotVsBotStream source change has been
// deployed to production. Before that, no running app instance knows id 14 -
// Source.apply(14) returns None on old code, so any request that reads these
// games' source in the meantime would see it as undefined. Running this
// pre-deploy risks a window where games silently have an unrecognised source.
//
// Also double check PS_BOT_IDS below against modules/common/src/main/LightUser.scala
// psBotsIDs before running - a stale list will misclassify games either way.
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
];

const botVsBotFriendGames = {
  s: 2, // Source.Friend - the only source these games could have prior to this migration
  us: { $size: 2 }, // guard against open/anonymous challenges with fewer than 2 known players
  'us.0': { $in: PS_BOT_IDS },
  'us.1': { $in: PS_BOT_IDS },
};

// dry run first - check this count looks sane before updating anything
print('games to migrate: ' + db.game5.countDocuments(botVsBotFriendGames));

// inspect a sample before committing, if you want:
// db.game5.find(botVsBotFriendGames).limit(5).forEach(g => print(g._id + ' ' + g.us));

db.game5.updateMany(botVsBotFriendGames, { $set: { s: 14 } });

print('games now on BotVsBotStream source: ' + db.game5.countDocuments({ s: 14 }));
