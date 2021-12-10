db.pref.find().forEach(c => {
  db.pref.update(
    { _id: c._id },
    {
      $set: {
        pieceSet: [
          { name: 'cburnett', gameFamily: 0 },
          { name: 'wide_crown', gameFamily: 1 },
          { name: 'wide', gameFamily: 2 },
          { name: '2kamji', gameFamily: 3 },
          { name: '2dhanzi', gameFamily: 4 },
        ],
      },
    }
  );
});
