//Update all medleys to set the number of intervals (dont update duration as it could not match for older styles)
db.tournament2
  .find({ mVariants: { $exists: true }, mMinutes: { $exists: true }, mIntervals: { $exists: false } })
  .forEach(t => {
    print('updating medley tournament ' + t._id + '; ' + t.name);
    var intervals = Math.ceil(t.minutes / t.mMinutes);
    var remainder = intervals * t.mMinutes * 60 - t.minutes * 60;
    var speeds = Array.from({ length: intervals }, (_, i) =>
      i !== intervals - 1 ? NumberInt(t.mMinutes * 60) : NumberInt(t.mMinutes * 60 - remainder)
    );
    db.tournament2.update(
      { _id: t._id },
      {
        $set: {
          mIntervals: speeds,
        },
      }
    );
  });

//find out how many will change + details
db.tournament2.count({ mVariants: { $exists: true }, mMinutes: { $exists: true } });
db.tournament2.find({ mVariants: { $exists: true }, mMinutes: { $exists: true } }, { _id: 1, name: 1, createdBy: 1 });
db.tournament2.find(
  { mVariants: { $exists: true }, mMinutes: { $exists: true }, createdBy: { $exists: true } },
  { _id: 1, name: 1, createdBy: 1, mMinutes: 1, minutes: 1 }
);

//undo setting of mIntervals
db.tournament2.find({ mIntervals: { $exists: true } }).forEach(t => {
  print('unset mIntervals in ' + t._id + '; ' + t.name);

  db.tournament2.update(
    { _id: t._id },
    {
      $unset: {
        mIntervals: true,
      },
    }
  );
});
