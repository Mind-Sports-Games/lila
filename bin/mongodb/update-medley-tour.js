//Update all medleys to set the number of intervals (dont update duration as it could not match for older styles)
db.tournament2
  .find({ mVariants: { $exists: true }, mMinutes: { $exists: true }, mIntervals: { $exists: false } })
  .forEach(t => {
    print('updating medley tournament ' + t._id + '; ' + t.name);
    var intervals = Math.ceil(t.minutes / t.mMinutes);
    db.tournament2.update(
      { _id: t._id },
      {
        $set: {
          mIntervals: NumberInt(intervals),
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
