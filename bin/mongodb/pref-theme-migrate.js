db.pref.find().forEach(c => {
  db.pref.update(
    { _id: c._id },
    {
      $set: {
        theme: [
          { name: c.theme, gameFamily: 0 },
          { name: 'brown', gameFamily: 1 },
          { name: 'brown', gameFamily: 2 },
          { name: 'wood', gameFamily: 3 },
          { name: 'grey', gameFamily: 4 },
        ],
      },
    }
  );
});
