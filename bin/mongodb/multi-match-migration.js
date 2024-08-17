//Convert micro match records in mongo to multimatch games and apporpiate swiss/game objects)

function convertMatchResult(r) {
  if (r.w == true) {
    return parseInt('1');
  } else if (r.w == false) {
    return parseInt('2');
  } else {
    return parseInt('0');
  }
}

//count swiss_pairing changes //105 on dev, 1476 on live
db.swiss_pairing.count({ mm: true });
//update swisspairing collection
db.swiss_pairing.find({ mm: true }).forEach(sp => {
  print('updating game 1/2 ' + sp._id);
  var game1mm = `1:${sp._id}`;
  var game2mm = `2:${sp._id}`;
  db.game5.update(
    { _id: sp._id },
    {
      $set: {
        mm: game1mm,
      },
    },
  );
  print('updating game 2/2 ' + sp.mmid);
  db.game5.update(
    { _id: sp.mmid },
    {
      $set: {
        mm: game2mm,
      },
    },
  );
  //update mt in swiss paring
  var res1 = 0;
  db.game5.find({ _id: sp._id }, { w: 1, _id: 0 }).forEach(r => {
    res1 = convertMatchResult(r);
  });
  var res2 = 0;
  db.game5.find({ _id: sp.mmid }, { w: 1, _id: 0 }).forEach(r => {
    res2 = convertMatchResult(r);
  });
  var matchRes = [NumberInt(res1), NumberInt(res2)];

  print(sp.s + ' swiss pairing updating');
  db.swiss_pairing.update(
    { _id: sp._id },
    {
      $set: {
        px: true,
        gpr: 2,
        mmids: [sp.mmid],
        mt: matchRes,
      },
      // $unset: {
      //   mmid: true,
      //   mm: true,
      // },
    },
  );

  var stat_cache = `swiss:stats:${sp.s}`;
  print('delete cache for swiss stats');
  db.cache.deleteOne({ _id: stat_cache });
});

//count swiss changes
db.swiss.count({ 'settings.m': true }); //14 on dev, 54 on live
//update swiss settings collection
db.swiss.find({ 'settings.m': true }).forEach(s => {
  print('updating ' + s._id);
  db.swiss.update(
    { _id: s._id },
    {
      $set: {
        'settings.px': true,
        'settings.gpr': 2,
      },
      // $unset: {
      //   'settings.m': true,
      // },
    },
  );
  print(s._id + ' swiss updated');
});

///////////////////////////////////////////////
//PART 2 UNSETTNG MICRO MATCH FROM DB COLLECTIONS
///////////////////////////////////////////////

//unset micro match field in swiss post migration
db.swiss.find({ 'settings.m': { $exists: true } }).forEach(s => {
  db.swiss.update(
    { _id: s._id },
    {
      $unset: {
        'settings.m': true,
      },
    },
  );
});

//unset micromatch in swiss settings collection
db.swiss_pairing.find({ mm: { $exists: true } }).forEach(sp => {
  db.swiss_pairing.update(
    { _id: sp._id },
    {
      $unset: {
        mmid: true,
        mm: true,
      },
    },
  );
});
