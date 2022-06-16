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
db.trophy.insert({
  _id: 'msogp-gold-DRUGP-Dracarys',
  kind: 'msogp-gold-medal',
  user: 'dracarys',
  date: ISODate('2022-05-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/X5QsyPDA',
  name: 'Russian Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-OTSGP-SOLFAREMI',
  kind: 'msogp-gold-medal',
  user: 'solfaremi',
  date: ISODate('2022-05-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/z1vvLnxU',
  name: 'Othello Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-C3SGP-knightleap',
  kind: 'msogp-gold-medal',
  user: 'knightleap',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/ZgKruvOO',
  name: 'Three Check Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-DANGP-derarzt',
  kind: 'msogp-gold-medal',
  user: 'derarzt',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/lMMzpkvw',
  name: 'Antidraughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-DRKGP-FS-Schach',
  kind: 'msogp-gold-medal',
  user: 'fs-schach',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/RfudwA8Z',
  name: 'Racing Kings Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-CKSGP-abdekker',
  kind: 'msogp-gold-medal',
  user: 'abdekker',
  date: ISODate('2022-05-18T23:59:00Z'),
  url: '//playstrategy.org/swiss/AV19dsnc',
  name: 'King of the Hill Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-DBRGP-ayx',
  kind: 'msogp-gold-medal',
  user: 'ayx',
  date: ISODate('2022-05-19T23:59:00Z'),
  url: '//playstrategy.org/swiss/IEi4hVKk',
  name: 'Brazilian Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-DMEGP-derarzt',
  kind: 'msogp-gold-medal',
  user: 'derarzt',
  date: ISODate('2022-05-21T23:59:00Z'),
  url: '//playstrategy.org/swiss/kTV4wbsC',
  name: 'Draughts Medley Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-DBTGP-derarzt',
  kind: 'msogp-gold-medal',
  user: 'derarzt',
  date: ISODate('2022-05-22T23:59:00Z'),
  url: '//playstrategy.org/swiss/imlv8HOk',
  name: 'Breakthrough Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-DFYGP-derarzt',
  kind: 'msogp-gold-medal',
  user: 'derarzt',
  date: ISODate('2022-05-22T23:59:00Z'),
  url: '//playstrategy.org/swiss/yM5IMuKy',
  name: 'Frysk Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-DPOGP-Ramazon_Master_IDF',
  kind: 'msogp-gold-medal',
  user: 'ramazon_master_idf',
  date: ISODate('2022-05-24T23:59:00Z'),
  url: '//playstrategy.org/swiss/LFWq409r',
  name: 'Pool Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-PSMGP-clawmac',
  kind: 'msogp-gold-medal',
  user: 'clawmac',
  date: ISODate('2022-05-27T23:59:00Z'),
  url: '//playstrategy.org/swiss/dHxPPVOh',
  name: 'PlayStrategy Medley Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-CASGP-ginko81',
  kind: 'msogp-gold-medal',
  user: 'ginko81',
  date: ISODate('2022-05-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/OpGOTBdq',
  name: 'Atomic Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-CMSGP-AyushZzz',
  kind: 'msogp-gold-medal',
  user: 'ayushzzz',
  date: ISODate('2022-05-29T23:59:00Z'),
  url: '//playstrategy.org/swiss/jm8O8RJf',
  name: 'Chess Medley Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-gold-OWAGP-mrjohnson',
  kind: 'msogp-gold-medal',
  user: 'mrjohnson',
  date: ISODate('2022-05-29T23:59:00Z'),
  url: '//playstrategy.org/swiss/sAhJOfNX',
  name: 'Oware Swiss - MSO GP 2022',
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
db.trophy.insert({
  _id: 'msogp-silver-DRUGP-fulltilt',
  kind: 'msogp-silver-medal',
  user: 'fulltilt',
  date: ISODate('2022-05-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/X5QsyPDA',
  name: 'Russian Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-OTSGP-TuanViet',
  kind: 'msogp-silver-medal',
  user: 'tuanviet',
  date: ISODate('2022-05-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/z1vvLnxU',
  name: 'Othello Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-C3SGP-abdekker',
  kind: 'msogp-silver-medal',
  user: 'abdekker',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/ZgKruvOO',
  name: 'Three Check Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-DANGP-drobakhin59',
  kind: 'msogp-silver-medal',
  user: 'drobakhin59',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/lMMzpkvw',
  name: 'Antidraughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-DRKGP-berserker',
  kind: 'msogp-silver-medal',
  user: 'berserker',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/RfudwA8Z',
  name: 'Racing Kings Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-CKSGP-Scabripok',
  kind: 'msogp-silver-medal',
  user: 'scabripok',
  date: ISODate('2022-05-18T23:59:00Z'),
  url: '//playstrategy.org/swiss/AV19dsnc',
  name: 'King of the Hill Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-DBRGP-fulltilt',
  kind: 'msogp-silver-medal',
  user: 'fulltilt',
  date: ISODate('2022-05-19T23:59:00Z'),
  url: '//playstrategy.org/swiss/IEi4hVKk',
  name: 'Brazilian Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-DMEGP-fulltilt',
  kind: 'msogp-silver-medal',
  user: 'fulltilt',
  date: ISODate('2022-05-21T23:59:00Z'),
  url: '//playstrategy.org/swiss/kTV4wbsC',
  name: 'Draughts Medley Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-DBTGP-ZAS',
  kind: 'msogp-silver-medal',
  user: 'zas',
  date: ISODate('2022-05-22T23:59:00Z'),
  url: '//playstrategy.org/swiss/imlv8HOk',
  name: 'Breakthrough Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-DFYGP-pergioco',
  kind: 'msogp-silver-medal',
  user: 'pergioco',
  date: ISODate('2022-05-22T23:59:00Z'),
  url: '//playstrategy.org/swiss/yM5IMuKy',
  name: 'Frysk Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-DPOGP-Countess',
  kind: 'msogp-silver-medal',
  user: 'countess',
  date: ISODate('2022-05-24T23:59:00Z'),
  url: '//playstrategy.org/swiss/LFWq409r',
  name: 'Pool Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-PSMGP-kspttw',
  kind: 'msogp-silver-medal',
  user: 'kspttw',
  date: ISODate('2022-05-27T23:59:00Z'),
  url: '//playstrategy.org/swiss/dHxPPVOh',
  name: 'PlayStrategy Medley Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-CASGP-AyushZzz',
  kind: 'msogp-silver-medal',
  user: 'ayushzzz',
  date: ISODate('2022-05-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/OpGOTBdq',
  name: 'Atomic Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-CMSGP-BlackGoesFirst',
  kind: 'msogp-silver-medal',
  user: 'blackgoesfirst',
  date: ISODate('2022-05-29T23:59:00Z'),
  url: '//playstrategy.org/swiss/jm8O8RJf',
  name: 'Chess Medley Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-silver-OWAGP-Calculus',
  kind: 'msogp-silver-medal',
  user: 'calculus',
  date: ISODate('2022-05-29T23:59:00Z'),
  url: '//playstrategy.org/swiss/sAhJOfNX',
  name: 'Oware Swiss - MSO GP 2022',
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
db.trophy.insert({
  _id: 'msogp-bronze-DRUGP-Maxvolchok13',
  kind: 'msogp-bronze-medal',
  user: 'maxvolchok13',
  date: ISODate('2022-05-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/X5QsyPDA',
  name: 'Russian Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-OTSGP-shimgar',
  kind: 'msogp-bronze-medal',
  user: 'shimgar',
  date: ISODate('2022-05-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/z1vvLnxU',
  name: 'Othello Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-C3SGP-ginko81',
  kind: 'msogp-bronze-medal',
  user: 'ginko81',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/ZgKruvOO',
  name: 'Three Check Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DANGP-vitalavvs',
  kind: 'msogp-bronze-medal',
  user: 'vitalavvs',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/lMMzpkvw',
  name: 'Antidraughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DANGP-raphael20odrich',
  kind: 'msogp-bronze-medal',
  user: 'raphael20odrich',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/lMMzpkvw',
  name: 'Antidraughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DRKGP-kspttw',
  kind: 'msogp-bronze-medal',
  user: 'kspttw',
  date: ISODate('2022-05-15T23:59:00Z'),
  url: '//playstrategy.org/swiss/RfudwA8Z',
  name: 'Racing Kings Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-CKSGP-mrtake',
  kind: 'msogp-bronze-medal',
  user: 'mrtake',
  date: ISODate('2022-05-18T23:59:00Z'),
  url: '//playstrategy.org/swiss/AV19dsnc',
  name: 'King of the Hill Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DBRGP-matvey27',
  kind: 'msogp-bronze-medal',
  user: 'matvey27',
  date: ISODate('2022-05-19T23:59:00Z'),
  url: '//playstrategy.org/swiss/IEi4hVKk',
  name: 'Brazilian Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DMEGP-jankis',
  kind: 'msogp-bronze-medal',
  user: 'jankis',
  date: ISODate('2022-05-21T23:59:00Z'),
  url: '//playstrategy.org/swiss/kTV4wbsC',
  name: 'Draughts Medley Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DBTGP-pergioco',
  kind: 'msogp-bronze-medal',
  user: 'pergioco',
  date: ISODate('2022-05-22T23:59:00Z'),
  url: '//playstrategy.org/swiss/imlv8HOk',
  name: 'Breakthrough Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DFYGP-raphael20odrich',
  kind: 'msogp-bronze-medal',
  user: 'raphael20odrich',
  date: ISODate('2022-05-22T23:59:00Z'),
  url: '//playstrategy.org/swiss/yM5IMuKy',
  name: 'Frysk Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-DPOGP-fulltilt',
  kind: 'msogp-bronze-medal',
  user: 'fulltilt',
  date: ISODate('2022-05-24T23:59:00Z'),
  url: '//playstrategy.org/swiss/LFWq409r',
  name: 'Pool Draughts Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-PSMGP-Jheps',
  kind: 'msogp-bronze-medal',
  user: 'jheps',
  date: ISODate('2022-05-27T23:59:00Z'),
  url: '//playstrategy.org/swiss/dHxPPVOh',
  name: 'PlayStrategy Medley Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-CASGP-BlackGoesFirst',
  kind: 'msogp-bronze-medal',
  user: 'blackgoesfirst',
  date: ISODate('2022-05-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/OpGOTBdq',
  name: 'Atomic Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-CMSGP-kspttw',
  kind: 'msogp-bronze-medal',
  user: 'kspttw',
  date: ISODate('2022-05-29T23:59:00Z'),
  url: '//playstrategy.org/swiss/jm8O8RJf',
  name: 'Chess Medley Swiss - MSO GP 2022',
});
db.trophy.insert({
  _id: 'msogp-bronze-OWAGP-roberisco',
  kind: 'msogp-bronze-medal',
  user: 'roberisco',
  date: ISODate('2022-05-29T23:59:00Z'),
  url: '//playstrategy.org/swiss/sAhJOfNX',
  name: 'Oware Swiss - MSO GP 2022',
});

//Maciej trophy cabinet tidy up
db.trophyKind.insert({
  _id: 'msogp-silver-medal-x6',
  name: 'MSO Grand Prix Silver Medals',
  url: '//mso.juliahayward.com/Report/YearMedals?year=2022',
  order: NumberInt(18),
  withCustomImage: true,
});
db.trophyKind.insert({
  _id: 'msogp-bronze-medal-x5',
  name: 'MSO Grand Prix Bronze Medals',
  url: '//mso.juliahayward.com/Report/YearMedals?year=2022',
  order: NumberInt(19),
  withCustomImage: true,
});

db.trophy.remove({"user":"kspttw", "kind":"msogp-silver-medal"})
db.trophy.remove({"user":"kspttw", "kind":"msogp-bronze-medal"})

db.trophy.insert({
  _id: 'msogp-silver-6-kspttw',
  kind: 'msogp-silver-medal-x6',
  user: 'kspttw',
  date: ISODate('2022-05-29T23:59:00Z'),
  url: '//mso.juliahayward.com/Report/ContestantMedals?contestantId=11913',
  name: '6 MSO GP 2022 Silver Medals',
});
db.trophy.insert({
  _id: 'msogp-bronze-5-kspttw',
  kind: 'msogp-bronze-medal-x5',
  user: 'kspttw',
  date: ISODate('2022-05-29T23:59:00Z'),
  url: '//mso.juliahayward.com/Report/ContestantMedals?contestantId=11913',
  name: '5 MSO GP 2022 Bronze Medals',
});
