
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
db.tournament2.find({ 'schedule.freq': 'shield', lib: 0, variant: { $in: [11, 14] } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '2_' + t.variant.toString() } });
});
db.tournament2.find({ 'schedule.freq': 'shield', lib: 2, variant: { $in: [1, 5] } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '3_' + t.variant.toString() } });
});
db.tournament2.find({ 'schedule.freq': 'shield', lib: 2, variant: { $in: [2, 4] } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '4_' + t.variant.toString() } });
});
db.tournament2.find({ 'schedule.freq': 'shield', lib: 2, variant: { $in: [6, 7] } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '5_' + t.variant.toString() } });
});
db.tournament2.find({ 'schedule.freq': 'shield', lib: 2, variant: { $in: [8] } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '8_' + t.variant.toString() } });
});
db.tournament2.find({ 'schedule.freq': 'shield', lib: 3, variant: { $in: [1] } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '6_' + t.variant.toString() } });
});
db.tournament2.find({ 'schedule.freq': 'shield', lib: 4, variant: { $in: [1] } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '7_' + t.variant.toString() } });
});
db.tournament2.find({ 'schedule.freq': 'shield', variant: { $exists: false } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '0_1' } });
});

db.tournament2.find({ 'schedule.freq': { $in: ['shield', 'medleyshield'] } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id, r: 1 }, { $set: { mp: 5 } });
  db.tournament_leaderboard.updateMany({ t: t._id, r: 2 }, { $set: { mp: 3 } });
  db.tournament_leaderboard.updateMany({ t: t._id, r: 3 }, { $set: { mp: 2 } });
  db.tournament_leaderboard.updateMany({ t: t._id, r: { $gt: 3 } }, { $set: { mp: 1 } });
});
