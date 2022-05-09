db.trophyKind.insert({
  _id: 'msogp-gold-medal',
  name: 'MSO Grand Prix Gold Medal',
  url: '//mso.juliahayward.com/Report/YearMedals?year=2022',
  order: NumberInt(17),
  withCustomImage: true,
});
db.trophyKind.insert({
  _id: 'msogp-silver-medal',
  name: 'MSO Grand Prix Silver Medal',
  url: '//mso.juliahayward.com/Report/YearMedals?year=2022',
  order: NumberInt(18),
  withCustomImage: true,
});
db.trophyKind.insert({
  _id: 'msogp-bronze-medal',
  name: 'MSO Grand Prix Bronze Medal',
  url: '//mso.juliahayward.com/Report/YearMedals?year=2022',
  order: NumberInt(19),
  withCustomImage: true,
});

//GOLD
db.trophy.insert({
  _id: 'msogp-gold-SHSGP-2022-komacchin',
  kind: 'msogp-gold-medal',
  user: 'komacchin',
  date: ISODate('2022-04-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/DxlJ1OhH',
  name: 'Shogi Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-CHEGP-2022-W_Amadeus',
  kind: 'msogp-gold-medal',
  user: 'w_amadeus',
  date: ISODate('2021-04-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/hZWMxjAm',
  name: 'Chess 10+5 Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-XISGP-2022-CTCTCTCTCT',
  kind: 'msogp-gold-medal',
  user: 'ctctctctct',
  date: ISODate('2022-04-16T23:59:00Z'),
  url: '//playstrategy.org/swiss/CHDlQlo2',
  name: 'Xiangqi Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-CLSGP-Ogul1',
  kind: 'msogp-gold-medal',
  user: 'ogul1',
  date: ISODate('2022-04-19T23:59:00Z'),
  url: '//playstrategy.org/swiss/c2ITFIqY',
  name: 'Antichess Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-DISGP-jankis',
  kind: 'msogp-gold-medal',
  user: 'jankis',
  date: ISODate('2022-04-20T23:59:00Z'),
  url: '//playstrategy.org/swiss/llcFEhXx',
  name: 'International Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-CNSGP-Scabripok',
  kind: 'msogp-gold-medal',
  user: 'scabripok',
  date: ISODate('2022-04-26T23:59:00Z'),
  url: '//playstrategy.org/swiss/dqDr2IIu',
  name: 'No Castling Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-CHSGP-Le_Nonpareil_ARMENIE',
  kind: 'msogp-gold-medal',
  user: 'le_nonpareil_armenie',
  date: ISODate('2022-04-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/bYveUt26',
  name: 'Horde Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-DFIGP-pergioco',
  kind: 'msogp-gold-medal',
  user: 'pergioco',
  date: ISODate('2022-05-01T23:59:00Z'),
  url: '//playstrategy.org/swiss/N9IufMeB',
  name: 'Frisian Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-LASGP-kspttw',
  kind: 'msogp-gold-medal',
  user: 'kspttw',
  date: ISODate('2022-05-01T23:59:00Z'),
  url: '//playstrategy.org/swiss/7QJWtJLG',
  name: 'Lines of Action Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-C9SGP-mrtake',
  kind: 'msogp-gold-medal',
  user: 'mrtake',
  date: ISODate('2022-05-02T23:59:00Z'),
  url: '//playstrategy.org/swiss/gUSec6MB',
  name: 'Chess960 Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-LAEGP-Jheps',
  kind: 'msogp-gold-medal',
  user: 'jheps',
  date: ISODate('2022-05-04T23:59:00Z'),
  url: '//playstrategy.org/swiss/x2XxW7Lk',
  name: 'Scrambled Eggs Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-CCSGP-Ogul1',
  kind: 'msogp-gold-medal',
  user: 'ogul1',
  date: ISODate('2022-05-08T23:59:00Z'),
  url: '//playstrategy.org/swiss/9qUwBvTG',
  name: 'Crazyhouse Swiss - MSO GP 2022',
});

//SILVER
db.trophy.insert({
  _id: 'msogp-silver-SHSGP-2022-berserker',
  kind: 'msogp-silver-medal',
  user: 'berserker',
  date: ISODate('2022-04-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/DxlJ1OhH',
  name: 'Shogi Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-CHEGP-2022-kspttw',
  kind: 'msogp-silver-medal',
  user: 'kspttw',
  date: ISODate('2021-04-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/hZWMxjAm',
  name: 'Chess 10+5 Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-XISGP-2022-DavidKwan',
  kind: 'msogp-silver-medal',
  user: 'davidkwan',
  date: ISODate('2022-04-16T23:59:00Z'),
  url: '//playstrategy.org/swiss/CHDlQlo2',
  name: 'Xiangqi Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-CLSGP-HolyLizard',
  kind: 'msogp-silver-medal',
  user: 'holylizard',
  date: ISODate('2022-04-19T23:59:00Z'),
  url: '//playstrategy.org/swiss/c2ITFIqY',
  name: 'Antichess Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-DISGP-derarzt',
  kind: 'msogp-silver-medal',
  user: 'derarzt',
  date: ISODate('2022-04-20T23:59:00Z'),
  url: '//playstrategy.org/swiss/llcFEhXx',
  name: 'International Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-CNSGP-kspttw',
  kind: 'msogp-silver-medal',
  user: 'kspttw',
  date: ISODate('2022-04-26T23:59:00Z'),
  url: '//playstrategy.org/swiss/dqDr2IIu',
  name: 'No Castling Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-CHSGP-kspttw',
  kind: 'msogp-silver-medal',
  user: 'kspttw',
  date: ISODate('2022-04-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/bYveUt26',
  name: 'Horde Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-DFIGP-derarzt',
  kind: 'msogp-silver-medal',
  user: 'derarzt',
  date: ISODate('2022-05-01T23:59:00Z'),
  url: '//playstrategy.org/swiss/N9IufMeB',
  name: 'Frisian Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-LASGP-Vashod',
  kind: 'msogp-silver-medal',
  user: 'vashod',
  date: ISODate('2022-05-01T23:59:00Z'),
  url: '//playstrategy.org/swiss/7QJWtJLG',
  name: 'Lines of Action Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-C9SGP-Koni',
  kind: 'msogp-silver-medal',
  user: 'koni',
  date: ISODate('2022-05-02T23:59:00Z'),
  url: '//playstrategy.org/swiss/gUSec6MB',
  name: 'Chess960 Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-LAEGP-abdekker',
  kind: 'msogp-silver-medal',
  user: 'abdekker',
  date: ISODate('2022-05-04T23:59:00Z'),
  url: '//playstrategy.org/swiss/x2XxW7Lk',
  name: 'Scrambled Eggs Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-CCSGP-raphael20odrich',
  kind: 'msogp-silver-medal',
  user: 'raphael20odrich',
  date: ISODate('2022-05-08T23:59:00Z'),
  url: '//playstrategy.org/swiss/9qUwBvTG',
  name: 'Crazyhouse Swiss - MSO GP 2022',
});

//BRONZE
db.trophy.insert({
  _id: 'msogp-bronze-SHSGP-2022-clawmac',
  kind: 'msogp-bronze-medal',
  user: 'clawmac',
  date: ISODate('2022-04-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/DxlJ1OhH',
  name: 'Shogi Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-CHEGP-2022-mrtake',
  kind: 'msogp-bronze-medal',
  user: 'mrtake',
  date: ISODate('2021-04-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/hZWMxjAm',
  name: 'Chess 10+5 Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-XISGP-2022-onedollar',
  kind: 'msogp-bronze-medal',
  user: 'onedollar',
  date: ISODate('2022-04-16T23:59:00Z'),
  url: '//playstrategy.org/swiss/CHDlQlo2',
  name: 'Xiangqi Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-CLSGP-Board_Games_Master',
  kind: 'msogp-bronze-medal',
  user: 'board_games_master',
  date: ISODate('2022-04-19T23:59:00Z'),
  url: '//playstrategy.org/swiss/c2ITFIqY',
  name: 'Antichess Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DISGP-paulie',
  kind: 'msogp-bronze-medal',
  user: 'paulie',
  date: ISODate('2022-04-20T23:59:00Z'),
  url: '//playstrategy.org/swiss/llcFEhXx',
  name: 'International Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-CNSGP-mrtake',
  kind: 'msogp-bronze-medal',
  user: 'mrtake',
  date: ISODate('2022-04-26T23:59:00Z'),
  url: '//playstrategy.org/swiss/dqDr2IIu',
  name: 'No Castling Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-CHSGP-TheGreatHordini',
  kind: 'msogp-bronze-medal',
  user: 'thegreathordini',
  date: ISODate('2022-04-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/bYveUt26',
  name: 'Horde Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DFIGP-kspttw',
  kind: 'msogp-bronze-medal',
  user: 'kspttw',
  date: ISODate('2022-05-01T23:59:00Z'),
  url: '//playstrategy.org/swiss/N9IufMeB',
  name: 'Frisian Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-LASGP-Jheps',
  kind: 'msogp-bronze-medal',
  user: 'jheps',
  date: ISODate('2022-05-01T23:59:00Z'),
  url: '//playstrategy.org/swiss/7QJWtJLG',
  name: 'Lines of Action Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-C9SGP-kspttw',
  kind: 'msogp-bronze-medal',
  user: 'kspttw',
  date: ISODate('2022-05-02T23:59:00Z'),
  url: '//playstrategy.org/swiss/gUSec6MB',
  name: 'Chess960 Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-LAEGP-komacchin',
  kind: 'msogp-bronze-medal',
  user: 'komacchin',
  date: ISODate('2022-05-04T23:59:00Z'),
  url: '//playstrategy.org/swiss/x2XxW7Lk',
  name: 'Scrambled Eggs Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-CCSGP-kspttw',
  kind: 'msogp-bronze-medal',
  user: 'kspttw',
  date: ISODate('2022-05-08T23:59:00Z'),
  url: '//playstrategy.org/swiss/9qUwBvTG',
  name: 'Crazyhouse Swiss - MSO GP 2022',
});
