db.pref.find().forEach(c => {
  db.pref.update(
    { _id: c._id },
    {
      $set: {
        pieceSet: [
          { name: c.pieceSet, gameFamily: 0 },
          { name: 'wide_crown', gameFamily: 1 },
          { name: 'check_yb_loa', gameFamily: 2 },
          { name: '2kanji', gameFamily: 3 },
          { name: '2dhanzi', gameFamily: 4 },
        ],
      },
    }
  );
});

//Oware pieceSet update funciton and command to run
function oware_rename(oldName) {
  switch (oldName) {
    case 'green_mancala':
      return 'green';
    case 'blue_mancala':
      return 'blue';
    case 'red_mancala':
      return 'red';
    case 'grey_mancala':
      return 'grey';
    case 'green_seed_mancala':
      return 'green_seed';
    case 'green_numbers_mancala':
      return 'green_numbers';
    default:
      return 'green';
  }
}

//Oware change of names ( and new togy to account for)
db.pref.find().forEach(c => {
  print(c._id + ' oware pieceSet change: ' + c.pieceSet[6].name + ' -> ' + oware_rename(c.pieceSet[6].name));
  db.pref.update(
    { _id: c._id },
    {
      $set: {
        pieceSet: [
          { name: c.pieceSet[0].name, gameFamily: 0 },
          { name: c.pieceSet[1].name, gameFamily: 1 },
          { name: c.pieceSet[2].name, gameFamily: 2 },
          { name: c.pieceSet[3].name, gameFamily: 3 },
          { name: c.pieceSet[4].name, gameFamily: 4 },
          { name: c.pieceSet[5].name, gameFamily: 5 },
          { name: oware_rename(c.pieceSet[6].name), gameFamily: 6 },
          { name: 'black_gloss', gameFamily: 7 },
        ],
      },
    }
  );
});
