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
    },
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

//Oware change of names ( and new togy to account for, and existing othello and oware coudl be unset)
db.pref.find().forEach(c => {
  const chessPS = c.pieceSet[0] ? c.pieceSet[0].name : 'staunty';
  const draughtsPS = c.pieceSet[1] ? c.pieceSet[1].name : 'wide_crown';
  const loaPS = c.pieceSet[2] ? c.pieceSet[2].name : 'check_yb_loa';
  const shogiPS = c.pieceSet[3] ? c.pieceSet[3].name : '2kanji';
  const xiangqiPS = c.pieceSet[4] ? c.pieceSet[4].name : '2dhanzi';
  const othelloPS = c.pieceSet[5] ? c.pieceSet[5].name : 'fabirovsky_flipello';
  const owarePS = c.pieceSet[6] ? c.pieceSet[6].name : 'unset';
  print(c._id + ' oware pieceSet change: ' + owarePS + ' -> ' + oware_rename(owarePS));
  db.pref.update(
    { _id: c._id },
    {
      $set: {
        pieceSet: [
          { name: chessPS, gameFamily: 0 },
          { name: draughtsPS, gameFamily: 1 },
          { name: loaPS, gameFamily: 2 },
          { name: shogiPS, gameFamily: 3 },
          { name: xiangqiPS, gameFamily: 4 },
          { name: othelloPS, gameFamily: 5 },
          { name: oware_rename(owarePS), gameFamily: 6 },
          { name: 'black_gloss', gameFamily: 7 },
        ],
      },
    },
  );
});
