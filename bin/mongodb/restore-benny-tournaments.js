//https://playstrategy.org/tournament/lUpYNPy6 Xiangqi Shield
//https://playstrategy.org/tournament/uy195hcv Weekly Xiangqi
//https://playstrategy.org/tournament/HlkWSl3V Weekly Mini Xiangqi
//https://playstrategy.org/tournament/rqVVaYiv Mini Xiangqi Shield

db.tournament_player.insert({
  _id: '1benny19',
  tid: 'lUpYNPy6',
  uid: 'benny1911988',
  r: 2105,
  f: true,
  m: 710000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '1benny19',
  u: 'benny1911988',
  t: 'lUpYNPy6',
  g: 15,
  s: 71,
  r: 1,
  w: 11111,
  mp: 5,
  k: '4_2',
  f: 51,
  p: 85,
  v: 201,
  d: ISODate('2023-03-23T18:00:42Z'),
});
db.tournament2.update({ _id: 'lUpYNPy6' }, { $set: { winner: 'benny1911988' } });

db.tournament_player.insert({
  _id: '2benny19',
  tid: 'uy195hcv',
  uid: 'benny1911988',
  r: 2106,
  f: true,
  m: 161800,
  e: 1800,
});
db.tournament_leaderboard.insert({
  _id: '2benny19',
  u: 'benny1911988',
  t: 'uy195hcv',
  g: 4,
  s: 16,
  r: 1,
  w: 11111,
  f: 40,
  p: 85,
  v: 201,
  d: ISODate('2023-04-03T15:00:43Z'),
});
db.tournament2.update({ _id: 'uy195hcv' }, { $set: { winner: 'benny1911988' } });

db.tournament_player.insert({
  _id: '3benny19',
  tid: 'HlkWSl3V',
  uid: 'benny1911988',
  r: 1904,
  f: true,
  m: 270000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '3benny19',
  u: 'benny1911988',
  t: 'HlkWSl3V',
  g: 14,
  s: 27,
  r: 1,
  w: 25000,
  f: 40,
  p: 70,
  v: 203,
  d: ISODate('2023-04-06T14:00:13Z'),
});
db.tournament2.update({ _id: 'HlkWSl3V' }, { $set: { winner: 'benny1911988' } });

db.tournament_player.insert({
  _id: '4benny19',
  tid: 'rqVVaYiv',
  uid: 'benny1911988',
  r: 1866,
  f: true,
  m: 350000,
  e: 0,
});
db.tournament_leaderboard.insert({
  _id: '4benny19',
  u: 'benny1911988',
  t: 'rqVVaYiv',
  g: 13,
  s: 35,
  r: 1,
  w: 25000,
  mp: 5,
  k: '4_4',
  f: 51,
  p: 70,
  v: 203,
  d: ISODate('2023-04-07T18:00:47Z'),
});
db.tournament2.update({ _id: 'rqVVaYiv' }, { $set: { winner: 'benny1911988' } });
