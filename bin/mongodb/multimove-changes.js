db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 0] } }, { $set: { ap: 1 } });
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 1] } }, { $set: { ap: 1 } });
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 2] } }, { $set: { ap: 2 } });
db.game5.updateMany({ l: 2, v: 8, t: { $mod: [4, 3] } }, { $set: { ap: 2 } });

//Now not setting ap for all old games, can determine it in the BSONHandler
//db.game5.updateMany({ ap: { $exists: false }, t: { $mod: [2, 0] } }, { $set: { ap: 1 } });
//db.game5.updateMany({ ap: { $exists: false }, t: { $mod: [2, 1] } }, { $set: { ap: 2 } });

//db.game5.find({"sp": {$exists: true}}).count(); //double check this is 0 as we are redefining sp

//When sp meant startPlayer
//db.game5.updateMany({ st: { $mod: [2, 0] } }, { $set: { sp: 1 } });
//db.game5.updateMany({ st: { $mod: [2, 1] } }, { $set: { sp: 2 } });

//With sp meaning startedAtPly
db.game5.find({ st: { $exists: true } }).forEach(g => {
  db.game5.updateOne(
    { _id: g._id },
    {
      $set: {
        sp: g.st,
      },
    }
  );
});

db.game5.find({ l: 2, v: 8 }).forEach(g => {
  db.game5.updateOne(
    { _id: g._id },
    {
      $set: {
        p: g.t,
      },
    }
  );
});

db.game5.find({ l: 2, v: 8 }).forEach(g => {
  db.game5.updateOne(
    { _id: g._id },
    {
      $set: {
        t: NumberInt(g.t / 2),
      },
    }
  );
});

db.tournament_pairing.find({ ppt: 2 }).forEach(p => {
  db.tournament_pairing.updateOne(
    { _id: p._id },
    {
      $set: {
        t: NumberInt(p.t / 2),
      },
    }
  );
});

db.tournament_pairing.updateMany({ ppt: { $exists: true } }, { $unset: { ppt: '' } });

//alternative way of doing above
//db.tournament_pairing.find({"d": {$gte: ISODate("2023-03-01")}}).forEach(p => {
//  var t = db.tournament2.findOne({ "_id": p.tid, "lib": 2, "variant": 8 }, { name: 1 });
//  if (t != null) {
//    p.t = NumberInt(p.t / 2);
//    db.tournament_pairing.save(p);
//  }
//});

db.game5.find({ l: 2, v: 8, do: { $exists: true } }).forEach(g => {
  db.game5.updateOne(
    { _id: g._id },
    {
      $set: {
        do: g.do.map(function (d) {
          return NumberInt(d / 2);
        }),
      },
    }
  );
});
