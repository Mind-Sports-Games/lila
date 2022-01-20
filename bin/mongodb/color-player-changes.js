db.challenge.find()
  .forEach(s => {
    //print(`${s._id} choice: ${s.colorChoice} final: ${s.finalColor}`);
    db.challenge.update(
      { _id: s._id },
      {
        $set: {
          'playerIndexChoice': s.colorChoice,
          'finalPlayerIndex': s.finalColor,
        },
        $unset: {
          playerIndexChoice: "",
          finalPlayerIndex: ""
        },
      }
    );
  });

db.pref.find()
  .forEach(s => {
    //print(`${s._id} coord: ${s.coordPlayerIndex}`);
    db.pref.update(
      { _id: s._id },
      {
        $set: {
          'coordPlayerIndex': s.coordPlayerIndex
        },
        $unset: {
          coordPlayerIndex: ""
        },
      }
    );
  });

//TODO:
//
//Update the following keys changing color->playerIndex, white->p1, black->p2. Some of these are nested keys.
//
//pref: coordColor
//seek: color
//simul: color, hostColor
//user: colorIt [look at what this one actually is?]
//cache: whiteWins, blackWins
//coordinate_score: white, black
//player_assessment: white, black
//
//Also need to look at updating the values of some of these fields as they store white/black (which we want to be changed to p1/p2
//player_assessment: _id
//seek: color
//simul: color, hostColor
//study_chapter_flat: tags (in the text)
