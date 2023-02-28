db.simul.find().forEach(t => {
  print('updating simul tournament clock ' + t._id + '; ' + t.name);
  print('current clock ' + t.clock.config.limitSeconds + '; ' + t.clock.config.incrementSeconds);

  db.simul.update(
    { _id: t._id },
    {
      $set: {
        'clock.config.limit': NumberInt(t.clock.config.limitSeconds),
        'clock.config.increment': NumberInt(t.clock.config.incrementSeconds),
        'clock.config.t': 'fischer',
      },
      // $unset: {
      //     'clock.config.limitSeconds': true,
      //     'clock.config.incrementSeconds': true,
      // }
    }
  );
});
