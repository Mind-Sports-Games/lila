
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 0] } }, { $set: { ap: 1 } });
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 1] } }, { $set: { ap: 1 } });
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 2] } }, { $set: { ap: 2 } });
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 3] } }, { $set: { ap: 2 } });

db.game5.updateMany({ ap: { $exists: false }, t: { $mod: [2, 0] } }, { $set: { ap: 1 } });
db.game5.updateMany({ ap: { $exists: false }, t: { $mod: [2, 1] } }, { $set: { ap: 2 } });
//db.game5.find({"ap": {$exists: false}}).count();

//db.game5.find({"sp": {$exists: true}}).count(); //double check this is 0 as we are redefining sp
//When sp meant startPlayer
//db.game5.updateMany({ st: { $mod: [2, 0] } }, { $set: { sp: 1 } });
//db.game5.updateMany({ st: { $mod: [2, 1] } }, { $set: { sp: 2 } });

//With sp meaning startedAtPlies
db.game5.find({ st: { $exists: true } }).forEach(g => {
  db.game5.updateOne(
    { _id: g._id },
    {
      $set: {
        sp: g.st
      }
    }
  );
});

db.game5.find({ l: 2, v: 8 }).forEach(g => {
  db.game5.updateOne(
    { _id: g._id },
    {
      $set: {
        p: g.t
      }
    }
  );
});

db.game5.find({ l: 2, v: 8 }).forEach(g => {
  db.game5.updateOne(
    { _id: g._id },
    {
      $set: {
        t: NumberInt(g.t / 2)
      }
    }
  );
});

