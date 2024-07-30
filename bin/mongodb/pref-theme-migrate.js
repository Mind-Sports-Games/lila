db.pref.find().forEach(c => {
  db.pref.update(
    { _id: c._id },
    {
      $set: {
        theme: [
          { name: c.theme, gameFamily: 0 },
          { name: 'blue3', gameFamily: 1 },
          { name: 'marble', gameFamily: 2 },
          { name: 'wood', gameFamily: 3 },
          { name: 'green', gameFamily: 4 },
        ],
      },
    },
  );
});
