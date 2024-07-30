//If you are ever considering running this script in its entirity (and I dont know why you would now its been run on production), maybe consider going through each update individually and double checking.

db.challenge.find().forEach(s => {
  //print(`${s._id} choice: ${s.colorChoice} final: ${s.finalColor}`);
  db.challenge.update(
    { _id: s._id },
    {
      $set: {
        playerIndexChoice: s.colorChoice,
        finalPlayerIndex: s.finalColor,
      },
      $unset: {
        playerIndexChoice: '',
        finalPlayerIndex: '',
      },
    },
  );
});

db.pref.find({ coordColor: { $exists: true } }).forEach(s => {
  //print(`${s._id} coord: ${s.coordColor}`);
  db.pref.update(
    { _id: s._id },
    {
      $set: {
        coordPlayerIndex: s.coordColor,
      },
      $unset: {
        coordColor: '',
      },
    },
  );
});

db.user4.find({ colorIt: { $exists: true } }).forEach(s => {
  //print(`${s._id} it: ${s.colorIt}`);
  db.user4.update(
    { _id: s._id },
    {
      $set: {
        playerIndexIt: s.colorIt,
      },
      $unset: {
        colorIt: '',
      },
    },
  );
});

db.cache.find({ 'v.whiteWins': { $exists: true } }).forEach(s => {
  //print(`${s._id} whiteWins: ${s.v.whiteWins}`);
  db.cache.update(
    { _id: s._id },
    {
      $set: {
        'v.p1Wins': s.v.whiteWins,
      },
      $unset: {
        'v.whiteWins': '',
      },
    },
  );
});

db.cache.find({ 'v.blackWins': { $exists: true } }).forEach(s => {
  //print(`${s._id} blackWins: ${s.v.blackWins}`);
  db.cache.update(
    { _id: s._id },
    {
      $set: {
        'v.p2Wins': s.v.blackWins,
      },
      $unset: {
        'v.blackWins': '',
      },
    },
  );
});

db.coordinate_score.find({ white: { $exists: true } }).forEach(s => {
  //print(`${s._id} white: ${s.white}`);
  db.coordinate_score.update(
    { _id: s._id },
    {
      $set: {
        p1: s.white,
      },
      $unset: {
        white: '',
      },
    },
  );
});

db.coordinate_score.find({ black: { $exists: true } }).forEach(s => {
  //print(`${s._id} black: ${s.black}`);
  db.coordinate_score.update(
    { _id: s._id },
    {
      $set: {
        p2: s.black,
      },
      $unset: {
        black: '',
      },
    },
  );
});

db.player_assessment.find({ white: { $exists: true } }).forEach(s => {
  //print(`${s._id} white: ${s.white}`);
  db.player_assessment.update(
    { _id: s._id },
    {
      $set: {
        p1: s.white,
      },
      $unset: {
        white: '',
      },
    },
  );
});

//seems to duplicate not overwrite, probably because _id (pk) is changing?
db.player_assessment.find.forEach(function (e, i) {
  e._id = e._id.replace('white', 'p1');
  e._id = e._id.replace('black', 'p2');
  db.player_assessment.save(e);
});

db.player_assessment.remove({ _id: { $regex: '/white' } });
db.player_assessment.remove({ _id: { $regex: '/black' } });

db.seek_archive.find().forEach(s => {
  //print(`${s._id} color: ${s.color}`);
  db.seek_archive.update(
    { _id: s._id },
    {
      $set: {
        playerIndex: s.color,
      },
      $unset: {
        color: '',
      },
    },
  );
});

db.seek_archive.updateMany({ playerIndex: 'white' }, { $set: { playerIndex: 'p1' } });
db.seek_archive.updateMany({ playerIndex: 'black' }, { $set: { playerIndex: 'p2' } });

db.seek.find().forEach(s => {
  //print(`${s._id} color: ${s.color}`);
  db.seek.update(
    { _id: s._id },
    {
      $set: {
        playerIndex: s.color,
      },
      $unset: {
        color: '',
      },
    },
  );
});

db.seek.updateMany({ playerIndex: 'white' }, { $set: { playerIndex: 'p1' } });
db.seek.updateMany({ playerIndex: 'black' }, { $set: { playerIndex: 'p2' } });

db.simul.find().forEach(s => {
  //print(`${s._id} color: ${s.color}`);
  db.simul.update(
    { _id: s._id },
    {
      $set: {
        playerIndex: s.color,
      },
      $unset: {
        color: '',
      },
    },
  );
});

db.simul.updateMany({ playerIndex: 'white' }, { $set: { playerIndex: 'p1' } });
db.simul.updateMany({ playerIndex: 'black' }, { $set: { playerIndex: 'p2' } });

//TODO:
//
//Update the following keys changing color->playerIndex, white->p1, black->p2. Some of these are nested keys.
//
//simul: color, hostColor
//
//Also need to look at updating the values of some of these fields as they store white/black (which we want to be changed to p1/p2
//simul: color, hostColor
//study_chapter_flat: tags (in the text)
