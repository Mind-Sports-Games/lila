db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 0] } }, { $set: { ap: 1 } });
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 1] } }, { $set: { ap: 1 } });
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 2] } }, { $set: { ap: 2 } });
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 3] } }, { $set: { ap: 2 } });
db.game5.updateMany({ ap: { $exists: false }, t: { $mod: [2, 0] } }, { $set: { ap: 1 } });
db.game5.updateMany({ ap: { $exists: false }, t: { $mod: [2, 1] } }, { $set: { ap: 2 } });
//db.game5.find({"ap": {$exists: false}}).count();
