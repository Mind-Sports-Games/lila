db.tournament_leaderboard.updateMany({ r: 1 }, { $set: { mp: 3 } });
db.tournament_leaderboard.updateMany({ r: 2 }, { $set: { mp: 1 } });
db.tournament_leaderboard.updateMany({ r: { $gt: 2 } }, { $set: { mp: 0 } });

db.tournament2.find({ trophy1st: 'shieldPlayStrategyMedley' }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: 'spm' } });
});
db.tournament2.find({ trophy1st: 'shieldChessMedley' }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: 'scm' } });
});
db.tournament2.find({ trophy1st: 'shieldDraughtsMedley' }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: 'sdm' } });
});
db.tournament2.find({ 'schedule.freq': 'shield', variant: { $exists: true } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: t.lib.toString() + '_' + t.variant.toString() } });
});
db.tournament2.find({ 'schedule.freq': 'shield', variant: { $exists: false } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '0_1' } });
});
