db.player_assessment.find({ 'date.$date': { $exists: true } }).forEach(s => {
  db.player_assessment.update(
    { _id: s._id },
    {
      $set: {
        date: ISODate(s.date['$date']),
      },
    },
  );
});
