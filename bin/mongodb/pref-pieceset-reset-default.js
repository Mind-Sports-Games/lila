db.pref.find().forEach(c => {
  db.pref.update(
    { _id: c._id },
    {
      $set: {
        pieceSet: [
          { name: 'staunty', gameFamily: 0 },
          { name: 'wide_crown', gameFamily: 1 },
          { name: 'check_yb_loa', gameFamily: 2 },
          { name: '2kanji', gameFamily: 3 },
          { name: '2dhanzi', gameFamily: 4 },
        ],
      },
    }
  );
});
