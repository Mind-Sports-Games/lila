db.trophyKind.update(
  { _id: 'mso21-bronze-medal' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=21' } }
);
db.trophyKind.update(
  { _id: 'mso21-gold-medal' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=21' } }
);
db.trophyKind.update(
  { _id: 'mso21-silver-medal' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=21' } }
);
db.trophyKind.update(
  { _id: 'msogp-2023-bronze-medal' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=23' } }
);
db.trophyKind.update(
  { _id: 'msogp-2023-gold-medal' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=23' } }
);
db.trophyKind.update(
  { _id: 'msogp-2023-silver-medal' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=23' } }
);
db.trophyKind.update(
  { _id: 'msogp-bronze-medal' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=22' } }
);
db.trophyKind.update(
  { _id: 'msogp-bronze-medal-x3' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=22' } }
);
db.trophyKind.update(
  { _id: 'msogp-bronze-medal-x5' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=22' } }
);
db.trophyKind.update(
  { _id: 'msogp-gold-medal' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=22' } }
);
db.trophyKind.update(
  { _id: 'msogp-silver-medal' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=22' } }
);
db.trophyKind.update(
  { _id: 'msogp-silver-medal-x6' },
  { $set: { url: '//msodb.playstrategy.org/Report/YearMedals?year=22' } }
);

db.trophyKind.insert({
  withCustomImage: true,
  url: '//msodb.playstrategy.org/Report/YearMedals?year=21',
  name: 'MSO 2021 Silver Medals',
  order: 21,
  _id: 'mso21-silver-medal-x3',
});
db.trophyKind.insert({
  withCustomImage: true,
  url: '//msodb.playstrategy.org/Report/YearMedals?year=23',
  name: 'MSO Grand Prix 2023 Gold Medals',
  order: 231,
  _id: 'msogp-2023-gold-medal-x5',
});
db.trophyKind.insert({
  withCustomImage: true,
  url: '//msodb.playstrategy.org/Report/YearMedals?year=23',
  name: 'MSO Grand Prix 2023 Silver Medals',
  order: 232,
  _id: 'msogp-2023-silver-medal-x4',
});
db.trophyKind.insert({
  withCustomImage: true,
  url: '//msodb.playstrategy.org/Report/YearMedals?year=24',
  name: 'MSO Grand Prix 2024 Bronze Medal',
  order: 243,
  _id: 'msogp-2024-bronze-medal',
});
db.trophyKind.insert({
  withCustomImage: true,
  url: '//msodb.playstrategy.org/Report/YearMedals?year=24',
  name: 'MSO Grand Prix 2024 Gold Medal',
  order: 241,
  _id: 'msogp-2024-gold-medal',
});
db.trophyKind.insert({
  withCustomImage: true,
  url: '//msodb.playstrategy.org/Report/YearMedals?year=24',
  name: 'MSO Grand Prix 2024 Silver Medal',
  order: 242,
  _id: 'msogp-2024-silver-medal',
});
db.trophyKind.insert({
  withCustomImage: true,
  url: '//msodb.playstrategy.org/Report/YearMedals?year=22',
  name: 'MSO Grand Prix Gold Medals',
  order: 17,
  _id: 'msogp-gold-medal-x3',
});
db.trophyKind.insert({
  withCustomImage: true,
  url: '//msodb.playstrategy.org/Report/YearMedals?year=22',
  name: 'MSO Grand Prix Silver Medals',
  order: 18,
  _id: 'msogp-silver-medal-x2',
});

db.trophy.insert({
  _id: 'mso21-silver-medal-x3-derarzt',
  kind: 'mso21-silver-medal-x3',
  user: 'derarzt',
  date: ISODate('2021-09-06T23:59:00.000Z'),
  url: '//msodb.playstrategy.org/Report/ContestantMedals?contestantId=13042',
  name: '3 MSO 2021 Silver Medals',
});
db.trophy.insert({
  _id: 'msogp-silver-medal-x2-derarzt',
  kind: 'msogp-silver-medal-x2',
  user: 'derarzt',
  date: ISODate('2022-05-29T23:59:00.000Z'),
  url: '//msodb.playstrategy.org/Report/ContestantMedals?contestantId=13042',
  name: '2 MSO GP 2022 Silver Medals',
});
db.trophy.insert({
  _id: 'msogp-2023-silver-medal-x4-derarzt',
  kind: 'msogp-silver-medal-x4',
  user: 'derarzt',
  date: ISODate('2023-03-12T23:59:00.000Z'),
  url: '//msodb.playstrategy.org/Report/ContestantMedals?contestantId=13042',
  name: '4 MSO GP 2023 Silver Medals',
});
db.trophy.insert({
  _id: 'msogp-2023-gold-medal-x5-kspttw',
  kind: 'msogp-2023-gold-medal-x5',
  user: 'kspttw',
  date: ISODate('2023-03-12T23:59:00.000Z'),
  url: '//msodb.playstrategy.org/Report/ContestantMedals?contestantId=11913',
  name: '5 MSO GP 2023 Gold Medals',
});
db.trophy.insert({
  _id: 'msogp-gold-medal-x3-kspttw',
  kind: 'msogp-gold-medal-x3',
  user: 'kspttw',
  date: ISODate('2022-05-29T23:59:00.000Z'),
  url: '//msodb.playstrategy.org/Report/ContestantMedals?contestantId=11913',
  name: '3 MSO GP 2022 Gold Medals',
});

//lichess> db.trophy.find({user: 'derarzt', kind: 'mso21-silver-medal'});
//[
//  {
//    _id: 'mso21-silver-DRIN-derarzt',
//    kind: 'mso21-silver-medal',
//    user: 'derarzt',
//    date: ISODate("2021-08-26T23:59:00.000Z"),
//    url: '//playstrategy.org/swiss/JLeZ3Rp7',
//    name: 'MSO 2021 Silver Medal - International Draughts'
//  },
//  {
//    _id: 'mso21-silver-DRFK-derarzt',
//    kind: 'mso21-silver-medal',
//    user: 'derarzt',
//    date: ISODate("2021-08-30T23:59:00.000Z"),
//    url: '//playstrategy.org/swiss/NzwZ3G2A',
//    name: 'MSO 2021 Silver Medal - Frysk Draughts'
//  },
//  {
//    _id: 'mso21-silver-DRGA-derarzt',
//    kind: 'mso21-silver-medal',
//    user: 'derarzt',
//    date: ISODate("2021-09-01T23:59:00.000Z"),
//    url: '//playstrategy.org/swiss/qwwHGzdi',
//    name: 'MSO 2021 Silver Medal - Antidraughts'
//  }
//]

db.trophy.remove({ user: 'derarzt', kind: 'mso21-silver-medal' });

//lichess> db.trophy.find({user: 'derarzt', kind: 'msogp-silver-medal'});
//[
//  {
//    _id: 'msogp-silver-DISGP-derarzt',
//    kind: 'msogp-silver-medal',
//    user: 'derarzt',
//    date: ISODate("2022-04-20T23:59:00.000Z"),
//    url: '//playstrategy.org/swiss/llcFEhXx',
//    name: 'International Draughts Swiss - MSO GP 2022'
//  },
//  {
//    _id: 'msogp-silver-DFIGP-derarzt',
//    kind: 'msogp-silver-medal',
//    user: 'derarzt',
//    date: ISODate("2022-05-01T23:59:00.000Z"),
//    url: '//playstrategy.org/swiss/N9IufMeB',
//    name: 'Frisian Swiss - MSO GP 2022'
//  }
//]

db.trophy.remove({ user: 'derarzt', kind: 'msogp-silver-medal' });

//lichess> db.trophy.find({user: 'derarzt', kind: 'msogp-2023-silver-medal'});
//[
//  {
//    _id: '9szHtIDo',
//    user: 'derarzt',
//    kind: 'msogp-2023-silver-medal',
//    name: 'International Draughts - MSO Grand Prix 2023',
//    url: 'https://playstrategy.org/swiss/kC37CucN',
//    date: ISODate("2023-01-29T18:50:40.570Z")
//  },
//  {
//    _id: 'u8eMgTMw',
//    user: 'derarzt',
//    kind: 'msogp-2023-silver-medal',
//    name: 'Frisian Draughts - MSO GP 2023 - PREMIER',
//    url: 'https://playstrategy.org/swiss/uxQHId97',
//    date: ISODate("2023-02-01T23:12:28.491Z")
//  },
//  {
//    _id: 'n5VO9Ddv',
//    user: 'derarzt',
//    kind: 'msogp-2023-silver-medal',
//    name: 'Antidraughts - MSO Grand Prix 2023',
//    url: 'https://playstrategy.org/swiss/TFTGt3Tz',
//    date: ISODate("2023-02-04T20:23:54.933Z")
//  },
//  {
//    _id: 'LEajDoLh',
//    user: 'derarzt',
//    kind: 'msogp-2023-silver-medal',
//    name: 'PlayStrategy Medley - MSO Grand Prix 2023',
//    url: 'https://playstrategy.org/swiss/xULemIzD',
//    date: ISODate("2023-03-12T21:47:46.886Z")
//  }
//]

db.trophy.remove({ user: 'derarzt', kind: 'msogp-2023-silver-medal' });

//lichess> db.trophy.find({user: 'kspttw', kind: 'msogp-2023-gold-medal'});
//[
//  {
//    _id: 'KTnVUay8',
//    user: 'kspttw',
//    kind: 'msogp-2023-gold-medal',
//    name: 'Lines of Action - MSO GP 2023 - PREMIER',
//    url: 'https://playstrategy.org/swiss/DpztbhRK',
//    date: ISODate("2023-02-01T23:03:53.641Z")
//  },
//  {
//    _id: '6Bi8ysp7',
//    user: 'kspttw',
//    kind: 'msogp-2023-gold-medal',
//    name: 'Chess Variants Medley - MSO GP 2023 - PREMIER',
//    url: 'https://playstrategy.org/tournament/ox4ZEksa',
//    date: ISODate("2023-02-19T20:40:43.154Z")
//  },
//  {
//    _id: 'bwCxUf5n',
//    user: 'kspttw',
//    kind: 'msogp-2023-gold-medal',
//    name: 'No Castling Chess - MSO Grand Prix 2023',
//    url: 'https://playstrategy.org/swiss/AAmsJ2by',
//    date: ISODate("2023-02-26T22:51:42.726Z")
//  },
//  {
//    _id: 'R1v0vi0T',
//    user: 'kspttw',
//    kind: 'msogp-2023-gold-medal',
//    name: 'Chess 3+2',
//    url: 'https://playstrategy.org/tournament/EqLgUmKH',
//    date: ISODate("2023-03-04T21:00:03.482Z")
//  },
//  {
//    _id: 'lOjuD5WB',
//    user: 'kspttw',
//    kind: 'msogp-2023-gold-medal',
//    name: 'Crazyhouse Chess - MSO Grand Prix 2023',
//    url: 'https://playstrategy.org/swiss/MAzi0sKE',
//    date: ISODate("2023-03-10T21:13:11.028Z")
//  }
//]

db.trophy.remove({ user: 'kspttw', kind: 'msogp-2023-gold-medal' });

//lichess> db.trophy.find({user: 'kspttw', kind: 'msogp-gold-medal'});
//[
//  {
//    _id: 'SEtMBoKE',
//    user: 'kspttw',
//    kind: 'msogp-gold-medal',
//    name: 'Crazyhouse - MSO GP 2022',
//    url: 'https://playstrategy.org/tournament/IgP8VpFH',
//    date: ISODate("2022-04-17T21:30:49.644Z")
//  },
//  {
//    _id: 'msogp-gold-LASGP-kspttw',
//    kind: 'msogp-gold-medal',
//    user: 'kspttw',
//    date: ISODate("2022-05-01T23:59:00.000Z"),
//    url: '//playstrategy.org/swiss/7QJWtJLG',
//    name: 'Lines of Action Swiss - MSO GP 2022'
//  },
//  {
//    _id: '9lL6GgPa',
//    user: 'kspttw',
//    kind: 'msogp-gold-medal',
//    name: 'Chess Blitz - MSO GP 2022',
//    url: 'https://playstrategy.org/tournament/tUCH7OWi',
//    date: ISODate("2022-05-28T21:00:42.033Z")
//  }
//]

db.trophy.remove({ user: 'kspttw', kind: 'msogp-gold-medal' });

db.trophy.update(
  { _id: 'msogp-silver-6-kspttw' },
  { $set: { url: '//msodb.playstrategy.org/Report/ContestantMedals?contestantId=11913' } }
);
db.trophy.update(
  { _id: 'msogp-bronze-5-kspttw' },
  { $set: { url: '//msodb.playstrategy.org/Report/ContestantMedals?contestantId=11913' } }
);
db.trophy.update(
  { _id: 'msogp-bronze-3-jheps' },
  { $set: { url: '//msodb.playstrategy.org/Report/ContestantMedals?contestantId=1006' } }
);
