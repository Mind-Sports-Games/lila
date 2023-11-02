//View the data:
db.tournament_leaderboard.aggregate([
  { $lookup: { from: 'tournament2', localField: 't', foreignField: '_id', as: 'tour' } },
  { $unwind: '$tour' },
  { $match: { 'tour.schedule.freq': 'shield' } },
  { $project: { u: 1, 'tour.name': 1, 'tour._id': 1, 'tour.status': 1, r: 1, s: 1, g: 1 } },
]);

//mongoexport --host 172.31.7.79:27017 --db=lichess --collection=tournament2 --query='{ "schedule.freq": "shield", "status" : 30 }' --out=/tmp/shields.json

//Run this to get all shield ids. Copy output into next query.
db.tournament2.distinct('_id', { 'schedule.freq': 'shield', status: 30 });

//mongoexport --host 172.31.7.79:27017 --db=lichess --collection=tournament_leaderboard --query='{ t:{$in:["01jFQDhR", "0H4D6k3d", "0NbJUTMz", "0eqoDEZl", "18HoJaIK", "1GVaH4lR", "1btHoVjN", "1qO8KN7T", "1sXOYJSH", "20F3Vkdv", "2ChVYlug", "2Uh5Rp08", "2VDcleFO", "3255kAWR", "3RBP3gMx", "3i9Z5lP9", "4isEGE0X", "5F07E9gi", "5sbUwfC0", "6HDSWaoC", "6Vq4uOW3", "6j9VN4Bb", "6moeGHPF", "6nG5lFPj", "7v8qBcOT", "8E2DUPAp", "8HsvnhzS", "8Ucpj7gY", "8xsXc96v", "999TUYja", "A2pvHPhg", "AEC6OCLY", "ANuGq4nU", "Awrz9fTj", "AyePxdbw", "B8PipHgo", "BEbMyydo", "BPuGMIPe", "BZBPVuGg", "C3ifype1", "CR1o2tsu", "D8MD16Oq", "DR50VoyC", "DYMxilQu", "DeZQRKj0", "DrQCMSsI", "E6RMWsNf", "EGgzipBX", "EQa7TPae", "Ev06180N", "FEnTx2VF", "FtKSJvN1", "GZS64s56", "GaMtaP8C", "H1cXyML5", "HGxxELjA", "HRne7pcF", "HVPQVC6k", "Hgf63a0U", "IbHSg4B8", "IioEizIu", "IjlhkrBJ", "Iq9i5ZYV", "JMVASlPI", "K9lEwQWB", "KFMcqCRA", "KWsxdHo4", "KpmIThb4", "LdQnlblZ", "MD4NwNo3", "MxUOafok", "NKwqj38z", "NSMHFkX5", "Nmf27LFl", "NpNqrFWX", "O9Uk39A6", "Orz4AD3F", "OzXxdz5l", "P96Vd1Hb", "PNmaXdFC", "Pfzu4O6Y", "PgEC2d1z", "PhunnCYf", "Pt1YOgSW", "PyRpaIyl", "QUoNYJj0", "R969Jt3Z", "RPKwlEi3", "S4qKVr9K", "SEb4Ftsc", "SEphkIMM", "SZ9Se99q", "Sk9TXDLA", "TFrOSW9c", "TchY1xDI", "TtRm8hsq", "TvPh2LLK", "U4fTxT6b", "UQiXVeyU", "UeNAPSne", "W3QMN60R", "WK3gIpbH", "XLMkGTES", "XRGnogb4", "Xm48CbEv", "Y3TXuQWI", "YQ3zwjEK", "a2JZKGRa", "aHO7KYCl", "auuCDXe8", "avYh02wv", "avjgTd1w", "aySB39Aj", "ba44zscV", "cVkkUIeN", "dAoMP2l3", "dGZqSVjC", "dYenObWX", "dn8Of54A", "dv8bBEcj", "e1ngZUJo", "e41tWIY5", "eWmR52d6", "eXyoovwd", "eg7Yk6QQ", "esFFeMlO", "f3nfi0l5", "fA9kaRBa", "fJe9U6J9", "fL0SEvtu", "fPLq9gcG", "gSeLnhEm", "gfyDt9jJ", "gj8KuYvP", "guSmtMIM", "h8RnQmaH", "hYHrHGYx", "hdzvDARM", "ilDGxwYe", "jlgsgaON", "kAOKcfon", "krdcPBDr", "kxSGo2kc", "l31qAzjJ", "lPw6dRyw", "laUJzVXy", "mJkWiw5U", "myOp64pg", "nAPJ2FMJ", "nLnVwO1U", "nP7P1YuJ", "nb7Uaiwg", "oFqjdMb5", "oSL04yaN", "oWqRQBcO", "oZ2HZnXd", "od80Mo4Q", "okxtrfVH", "oo48otd0", "pSanmgmm", "piXoMepI", "piZ2N2jf", "q6c0YA8b", "qImVM453", "qfIecNHK", "qheTHawR", "qiEco59Z", "qsFAfk2L", "rGpKxCat", "rY3L9uTg", "rgJY9Tsj", "rmQAmVaE", "sB1Ez8WS", "sla6DOH0", "tCpElROG", "tOeg33xc", "tzHEsJxx", "uNbvfUVS", "vIatQ8LN", "vcAxSJck", "vjeu0TJW", "vsrm7yiL", "w21wzImi", "wUvbZ6Wy", "wmtTsFwM", "wmuwtBj9", "wv7obI5h", "wyWoxC3d", "x4KCU8jB", "xNSP9coR", "xX18EVlI", "xis9e2O3", "yFOi9ZPT", "yFjBbcGz", "yiXdUCQj", "ytgQk7lx", "zYsHHVap"] }  }' --out=/tmp/leaderboard.json

//Set up some data:
db.tournament_leaderboard.updateMany({ r: 1 }, { $set: { mp: 3 } });
db.tournament_leaderboard.updateMany({ r: 2 }, { $set: { mp: 1 } });
db.tournament_leaderboard.updateMany({ r: { $gt: 2 } }, { $set: { mp: 0 } });

db.tournament2.find({ trophy1st: 'shieldPlayStrategyMedley' }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: 'spm' } });
});
db.tournament2.find({ trophy1st: 'shieldChessMedley' }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: 'scm' } });
});
db.tournament2.find({ trophy1st: 'shieldDraughtsMedley' }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: 'sdm' } });
});
db.tournament2.find({ 'schedule.freq': 'shield', variant: { $exists: true } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: t.lib.toString() + '_' + t.variant.toString() } });
});
db.tournament2.find({ 'schedule.freq': 'shield', variant: { $exists: false } }).forEach(t => {
  db.tournament_leaderboard.updateMany({ t: t._id }, { $set: { k: '0_1' } });
});
