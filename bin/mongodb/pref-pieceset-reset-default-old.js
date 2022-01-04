db.pref.find().forEach(c => {
  db.pref.update({ _id: c._id }, { $set: { pieceSet: 'cburnett' } });
});
