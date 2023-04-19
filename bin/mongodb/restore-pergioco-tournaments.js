//https://playstrategy.org/tournament/FAmAJwDP Draughts Medley Shield 43
//https://playstrategy.org/tournament/q8aJVAkP Lines Of Action Shield
//https://playstrategy.org/tournament/rMcmvqeo Weekly Amazons
//https://playstrategy.org/tournament/5fy7XQx2 Chess960 Shield
//https://playstrategy.org/tournament/gzkSlUq5 Weekly Scrambled Eggs
//https://playstrategy.org/tournament/sUxHX8Zl Weekly Amazons
//https://playstrategy.org/tournament/ciYD9Ofh Othello Shield
//https://playstrategy.org/tournament/9PiPTHjm Frisian Shield
//https://playstrategy.org/tournament/NbgfomgY Draughts Medley Shield 45
//https://playstrategy.org/tournament/770UCGz7 Weekly Horde

db.tournament_player.insert({
  _id: '01pergio',
  tid: 'FAmAJwDP',
  uid: 'pergioco',
  r: 1898,
  m: 200000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '01pergio',
  u: 'pergioco',
  t: 'FAmAJwDP',
  g: 12,
  s: 20,
  r: 1,
  w: 7143,
  mp: 5,
  k: 'sdm',
  f: 52,
  p: 85,
  v: 117,
  d: ISODate('2023-04-01T13:00:33Z'),
});
db.tournament2.update({ _id: 'FAmAJwDP' }, { $set: { winner: 'pergioco' } });

db.tournament_player.insert({
  _id: '02pergio',
  tid: 'q8aJVAkP',
  uid: 'pergioco',
  r: 1569,
  m: 40000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '02pergio',
  u: 'pergioco',
  t: 'q8aJVAkP',
  g: 4,
  s: 4,
  r: 3,
  w: 30000,
  mp: 2,
  k: '2_11',
  f: 51,
  p: 70,
  v: 21,
  d: ISODate('2023-04-01T18:00:27Z'),
});

db.tournament_player.insert({
  _id: '03pergio',
  tid: 'rMcmvqeo',
  uid: 'pergioco',
  r: 2226,
  f: true,
  m: 200000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '03pergio',
  u: 'pergioco',
  t: 'rMcmvqeo',
  g: 8,
  s: 20,
  r: 1,
  w: 9091,
  f: 40,
  p: 70,
  v: 206,
  d: ISODate('2023-04-02T16:00:07Z'),
});
db.tournament2.update({ _id: 'rMcmvqeo' }, { $set: { winner: 'pergioco' } });

db.tournament_player.insert({
  _id: '04pergio',
  tid: '5fy7XQx2',
  uid: 'pergioco',
  r: 1188,
  m: 0,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '04pergio',
  u: 'pergioco',
  t: '5fy7XQx2',
  g: 2,
  s: 0,
  r: 9,
  w: 90000,
  mp: 1,
  k: '0_2',
  f: 51,
  p: 70,
  v: 11,
  d: ISODate('2023-04-02T18:00:25Z'),
});

db.tournament_player.insert({
  _id: '05pergio',
  tid: 'gzkSlUq5',
  uid: 'pergioco',
  r: 1231,
  m: 0,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '05pergio',
  u: 'pergioco',
  t: 'gzkSlUq5',
  g: 4,
  s: 0,
  r: 2,
  w: 66666,
  f: 40,
  p: 70,
  v: 22,
  d: ISODate('2023-04-06T06:00:30Z'),
});

db.tournament_player.insert({
  _id: '06pergio',
  tid: 'sUxHX8Zl',
  uid: 'pergioco',
  r: 1231,
  f: true,
  m: 240000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '06pergio',
  u: 'pergioco',
  t: 'sUxHX8Zl',
  g: 7,
  s: 24,
  r: 1,
  w: 11111,
  f: 40,
  p: 70,
  v: 206,
  d: ISODate('2023-04-09T10:00:45Z'),
});
db.tournament2.update({ _id: 'sUxHX8Zl' }, { $set: { winner: 'pergioco' } });

db.tournament_player.insert({
  _id: '07pergio',
  tid: 'ciYD9Ofh',
  uid: 'pergioco',
  r: 1916,
  f: true,
  m: 350000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '07pergio',
  u: 'pergioco',
  t: 'ciYD9Ofh',
  g: 9,
  s: 35,
  r: 1,
  w: 11111,
  mp: 5,
  k: '5_6',
  f: 51,
  p: 70,
  v: 204,
  d: ISODate('2023-04-10T18:00:20Z'),
});
db.tournament2.update({ _id: 'ciYD9Ofh' }, { $set: { winner: 'pergioco' } });

db.tournament_player.insert({
  _id: '08pergio',
  tid: '9PiPTHjm',
  uid: 'pergioco',
  r: 2461,
  f: true,
  m: 180000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '08pergio',
  u: 'pergioco',
  t: '9PiPTHjm',
  g: 7,
  s: 18,
  r: 1,
  w: 9091,
  mp: 5,
  k: '1_10',
  f: 51,
  p: 70,
  v: 111,
  d: ISODate('2023-04-12T18:00:12Z'),
});
db.tournament2.update({ _id: '9PiPTHjm' }, { $set: { winner: 'pergioco' } });

db.tournament_player.insert({
  _id: '09pergio',
  tid: 'NbgfomgY',
  uid: 'pergioco',
  r: 1394,
  m: 160000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '09pergio',
  u: 'pergioco',
  t: 'NbgfomgY',
  g: 10,
  s: 16,
  r: 1,
  w: 14286,
  mp: 5,
  k: 'sdm',
  f: 52,
  p: 85,
  v: 117,
  d: ISODate('2023-04-15T13:00:32Z'),
});
db.tournament2.update({ _id: 'NbgfomgY' }, { $set: { winner: 'pergioco' } });

db.tournament_player.insert({
  _id: '10pergio',
  tid: '770UCGz7',
  uid: 'pergioco',
  r: 1185,
  m: 30000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '10pergio',
  u: 'pergioco',
  t: '770UCGz7',
  g: 5,
  s: 3,
  r: 3,
  w: 75000,
  f: 40,
  p: 70,
  v: 16,
  d: ISODate('2023-04-16T10:00:49Z'),
});
