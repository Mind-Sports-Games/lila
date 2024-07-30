db.pref.count({ playForcedAction: { $exists: true, $eq: 0 } });

db.pref.find({ playForcedAction: { $exists: true, $eq: 0 } }).forEach(c => {
  db.pref.update(
    { _id: c._id },
    {
      $set: {
        playForcedAction: 1,
      },
    },
  );
  print(c._id + ' had pref set to 0 -> changed to 1');
});
