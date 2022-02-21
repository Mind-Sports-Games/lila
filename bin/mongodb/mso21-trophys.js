db.trophyKind.insert({
  _id: 'mso21-gold-medal',
  name: 'MSO 2021 Gold Medal',
  url: '//mso.juliahayward.com/Report/YearMedals?year=2021',
  order: NumberInt(20),
  withCustomImage: true,
});
db.trophyKind.insert({
  _id: 'mso21-silver-medal',
  name: 'MSO 2021 Silver Medal',
  url: '//mso.juliahayward.com/Report/YearMedals?year=2021',
  order: NumberInt(21),
  withCustomImage: true,
});
db.trophyKind.insert({
  _id: 'mso21-bronze-medal',
  name: 'MSO 2021 Bronze Medal',
  url: '//mso.juliahayward.com/Report/YearMedals?year=2021',
  order: NumberInt(22),
  withCustomImage: true,
});

//GOLD
db.trophy.insert({
  _id: 'mso21-gold-CHCR5-W_Amadeus',
  kind: 'mso21-gold-medal',
  user: 'w_amadeus',
  date: ISODate('2021-08-13T23:59:00Z'),
  url: '//playstrategy.org/swiss/xn14UriT',
  name: 'MSO 2021 Gold Medal - Crazyhouse Swiss',
});
db.trophy.insert({
  _id: 'mso21-gold-LOBZ-Jheps',
  kind: 'mso21-gold-medal',
  user: 'jheps',
  date: ISODate('2021-08-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/2ciF7lI1',
  name: 'MSO 2021 Gold Medal - Lines of Action Blitz',
});
db.trophy.insert({
  _id: 'mso21-gold-CH9S-W_Amadeus',
  kind: 'mso21-gold-medal',
  user: 'w_amadeus',
  date: ISODate('2021-08-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/VG1X1mdn',
  name: 'MSO 2021 Gold Medal - Chess960',
});
db.trophy.insert({
  _id: 'mso21-gold-CH3A-W_Amadeus',
  kind: 'mso21-gold-medal',
  user: 'w_amadeus',
  date: ISODate('2021-08-15T23:59:00Z'),
  url: '//playstrategy.org/tournament/c1Sccjtu',
  name: 'MSO 2021 Gold Medal - Chess Arena',
});
db.trophy.insert({
  _id: 'mso21-gold-CHKH-MaestroASK',
  kind: 'mso21-gold-medal',
  user: 'maestroask',
  date: ISODate('2021-08-18T23:59:00Z'),
  url: '//playstrategy.org/tournament/8qlcur2U',
  name: 'MSO 2021 Gold Medal - King of the Hill',
});
db.trophy.insert({
  _id: 'mso21-gold-LOWC-Vashod',
  kind: 'mso21-gold-medal',
  user: 'vashod',
  date: ISODate('2021-08-20T23:59:00Z'),
  url: '//playstrategy.org/swiss/HiIWXAGJ',
  name: 'MSO 2021 Gold Medal - Lines of Action World Championship',
});
db.trophy.insert({
  _id: 'mso21-gold-CHT3-Vashod',
  kind: 'mso21-gold-medal',
  user: 'vashod',
  date: ISODate('2021-08-21T23:59:00Z'),
  url: '//playstrategy.org/swiss/bI3BLQ98',
  name: 'MSO 2021 Gold Medal - Three Check',
});
db.trophy.insert({
  _id: 'mso21-gold-CHT3-MerryHatMan',
  kind: 'mso21-gold-medal',
  user: 'merryhatman',
  date: ISODate('2021-08-21T23:59:00Z'),
  url: '//playstrategy.org/swiss/bI3BLQ98',
  name: 'MSO 2021 Gold Medal - Three Check',
});
db.trophy.insert({
  _id: 'mso21-gold-CHRS-kspttw',
  kind: 'mso21-gold-medal',
  user: 'kspttw',
  date: ISODate('2021-08-22T23:59:00Z'),
  url: '//playstrategy.org/swiss/LBtq7lhA',
  name: 'MSO 2021 Gold Medal - Chess Swiss',
});
db.trophy.insert({
  _id: 'mso21-gold-CHHO-Raymond_Duck',
  kind: 'mso21-gold-medal',
  user: 'raymond_duck',
  date: ISODate('2021-08-22T23:59:00Z'),
  url: '//playstrategy.org/tournament/QtDUekTp',
  name: 'MSO 2021 Gold Medal - Horde',
});
db.trophy.insert({
  _id: 'mso21-gold-CHRK-QueenEatingDragon',
  kind: 'mso21-gold-medal',
  user: 'queeneatingdragon',
  date: ISODate('2021-08-25T23:59:00Z'),
  url: '//playstrategy.org/tournament/OkNDUywg',
  name: 'MSO 2021 Gold Medal - Racing Kings',
});
db.trophy.insert({
  _id: 'mso21-gold-CHCR-Raymond_Duck',
  kind: 'mso21-gold-medal',
  user: 'raymond_duck',
  date: ISODate('2021-08-26T23:59:00Z'),
  url: '//playstrategy.org/tournament/M8tYK78b',
  name: 'MSO 2021 Gold Medal - Crazyhouse Arena',
});
db.trophy.insert({
  _id: 'mso21-gold-DRIN-DamSpielerin',
  kind: 'mso21-gold-medal',
  user: 'damspielerin',
  date: ISODate('2021-08-26T23:59:00Z'),
  url: '//playstrategy.org/swiss/JLeZ3Rp7',
  name: 'MSO 2021 Gold Medal - International Draughts',
});
db.trophy.insert({
  _id: 'mso21-gold-CHAT-QueenEatingDragon',
  kind: 'mso21-gold-medal',
  user: 'queeneatingdragon',
  date: ISODate('2021-08-27T23:59:00Z'),
  url: '//playstrategy.org/tournament/JYPFRsDy',
  name: 'MSO 2021 Gold Medal - Atomic Arena',
});
db.trophy.insert({
  _id: 'mso21-gold-CH1S-LunarGuardianNasus',
  kind: 'mso21-gold-medal',
  user: 'lunarguardiannasus',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/5YxeJMW4',
  name: 'MSO 2021 Gold Medal - Bullet Chess Swiss',
});
db.trophy.insert({
  _id: 'mso21-gold-DRBT-derarzt',
  kind: 'mso21-gold-medal',
  user: 'derarzt',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/YMYfrCvg',
  name: 'MSO 2021 Gold Medal - Breakthrough Draughts',
});
db.trophy.insert({
  _id: 'mso21-gold-CHAT3-Arman',
  kind: 'mso21-gold-medal',
  user: 'arman',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/Zj6aJKaX',
  name: 'MSO 2021 Gold Medal - Atomic Swiss',
});
db.trophy.insert({
  _id: 'mso21-gold-CHAC-Arman',
  kind: 'mso21-gold-medal',
  user: 'arman',
  date: ISODate('2021-08-29T23:59:00Z'),
  url: '//playstrategy.org/tournament/ReSgHIjk',
  name: 'MSO 2021 Gold Medal - Antichess',
});
db.trophy.insert({
  _id: 'mso21-gold-DRFK-DamSpielerin',
  kind: 'mso21-gold-medal',
  user: 'damspielerin',
  date: ISODate('2021-08-30T23:59:00Z'),
  url: '//playstrategy.org/swiss/NzwZ3G2A',
  name: 'MSO 2021 Gold Medal - Frysk Draughts',
});
db.trophy.insert({
  _id: 'mso21-gold-DRGA-egormmaksymov341',
  kind: 'mso21-gold-medal',
  user: 'egormmaksymov341',
  date: ISODate('2021-09-01T23:59:00Z'),
  url: '//playstrategy.org/swiss/qwwHGzdi',
  name: 'MSO 2021 Gold Medal - Antidraughts',
});
db.trophy.insert({
  _id: 'mso21-gold-DRFC-derarzt',
  kind: 'mso21-gold-medal',
  user: 'derarzt',
  date: ISODate('2021-09-08T23:59:00Z'),
  url: '//playstrategy.org/swiss/EY1k6otN',
  name: 'MSO 2021 Gold Medal - Frisian Draughts',
});

//SILVER
db.trophy.insert({
  _id: 'mso21-silver-CHCR5-BenP',
  kind: 'mso21-silver-medal',
  user: 'benp',
  date: ISODate('2021-08-13T23:59:00Z'),
  url: '//playstrategy.org/swiss/xn14UriT',
  name: 'MSO 2021 Silver Medal - Crazyhouse Swiss',
});
db.trophy.insert({
  _id: 'mso21-silver-LOBZ-AndrewLSmith84',
  kind: 'mso21-silver-medal',
  user: 'andrewlsmith84',
  date: ISODate('2021-08-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/2ciF7lI1',
  name: 'MSO 2021 Silver Medal - Lines of Action Blitz',
});
db.trophy.insert({
  _id: 'mso21-silver-CH9S-Kimster98',
  kind: 'mso21-silver-medal',
  user: 'kimster98',
  date: ISODate('2021-08-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/VG1X1mdn',
  name: 'MSO 2021 Silver Medal - Chess960',
});
db.trophy.insert({
  _id: 'mso21-silver-CH3A-Kimster98',
  kind: 'mso21-silver-medal',
  user: 'kimster98',
  date: ISODate('2021-08-15T23:59:00Z'),
  url: '//playstrategy.org/tournament/c1Sccjtu',
  name: 'MSO 2021 Silver Medal - Chess Arena',
});
db.trophy.insert({
  _id: 'mso21-silver-CH3A-Robert_Kreisl',
  kind: 'mso21-silver-medal',
  user: 'robert_kreisl',
  date: ISODate('2021-08-15T23:59:00Z'),
  url: '//playstrategy.org/tournament/c1Sccjtu',
  name: 'MSO 2021 Silver Medal - Chess Arena',
});
db.trophy.insert({
  _id: 'mso21-silver-CHKH-raphael20odrich',
  kind: 'mso21-silver-medal',
  user: 'raphael20odrich',
  date: ISODate('2021-08-18T23:59:00Z'),
  url: '//playstrategy.org/tournament/8qlcur2U',
  name: 'MSO 2021 Silver Medal - King of the Hill',
});
db.trophy.insert({
  _id: 'mso21-silver-LOWC-komacchin',
  kind: 'mso21-silver-medal',
  user: 'komacchin',
  date: ISODate('2021-08-20T23:59:00Z'),
  url: '//playstrategy.org/swiss/HiIWXAGJ',
  name: 'MSO 2021 Silver Medal - Lines of Action World Championship',
});
db.trophy.insert({
  _id: 'mso21-silver-CHT3-QueenEatingDragon',
  kind: 'mso21-silver-medal',
  user: 'queeneatingdragon',
  date: ISODate('2021-08-21T23:59:00Z'),
  url: '//playstrategy.org/swiss/bI3BLQ98',
  name: 'MSO 2021 Silver Medal - Three Check',
});
db.trophy.insert({
  _id: 'mso21-silver-CHT3-knightleap',
  kind: 'mso21-silver-medal',
  user: 'knightleap',
  date: ISODate('2021-08-21T23:59:00Z'),
  url: '//playstrategy.org/swiss/bI3BLQ98',
  name: 'MSO 2021 Silver Medal - Three Check',
});
db.trophy.insert({
  _id: 'mso21-silver-CHRS-BenP',
  kind: 'mso21-silver-medal',
  user: 'benp',
  date: ISODate('2021-08-22T23:59:00Z'),
  url: '//playstrategy.org/swiss/LBtq7lhA',
  name: 'MSO 2021 Silver Medal - Chess Swiss',
});
db.trophy.insert({
  _id: 'mso21-silver-CHHO-BenP',
  kind: 'mso21-silver-medal',
  user: 'benp',
  date: ISODate('2021-08-22T23:59:00Z'),
  url: '//playstrategy.org/tournament/QtDUekTp',
  name: 'MSO 2021 Silver Medal - Horde',
});
db.trophy.insert({
  _id: 'mso21-silver-CHRK-Arman',
  kind: 'mso21-silver-medal',
  user: 'arman',
  date: ISODate('2021-08-25T23:59:00Z'),
  url: '//playstrategy.org/tournament/OkNDUywg',
  name: 'MSO 2021 Silver Medal - Racing Kings',
});
db.trophy.insert({
  _id: 'mso21-silver-CHCR-Gameking51',
  kind: 'mso21-silver-medal',
  user: 'gameking51',
  date: ISODate('2021-08-26T23:59:00Z'),
  url: '//playstrategy.org/tournament/M8tYK78b',
  name: 'MSO 2021 Silver Medal - Crazyhouse Arena',
});
db.trophy.insert({
  _id: 'mso21-silver-DRIN-derarzt',
  kind: 'mso21-silver-medal',
  user: 'derarzt',
  date: ISODate('2021-08-26T23:59:00Z'),
  url: '//playstrategy.org/swiss/JLeZ3Rp7',
  name: 'MSO 2021 Silver Medal - International Draughts',
});
db.trophy.insert({
  _id: 'mso21-silver-CHAT-Arman',
  kind: 'mso21-silver-medal',
  user: 'arman',
  date: ISODate('2021-08-27T23:59:00Z'),
  url: '//playstrategy.org/tournament/JYPFRsDy',
  name: 'MSO 2021 Silver Medal - Atomic Arena',
});
db.trophy.insert({
  _id: 'mso21-silver-CH1S-Gameking51',
  kind: 'mso21-silver-medal',
  user: 'gameking51',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/5YxeJMW4',
  name: 'MSO 2021 Silver Medal - Bullet Chess Swiss',
});
db.trophy.insert({
  _id: 'mso21-silver-DRBT-DamSpielerin',
  kind: 'mso21-silver-medal',
  user: 'damspielerin',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/YMYfrCvg',
  name: 'MSO 2021 Silver Medal - Breakthrough Draughts',
});
db.trophy.insert({
  _id: 'mso21-silver-CHAT3-QueenEatingDragon',
  kind: 'mso21-silver-medal',
  user: 'queeneatingdragon',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/Zj6aJKaX',
  name: 'MSO 2021 Silver Medal - Atomic Swiss',
});
db.trophy.insert({
  _id: 'mso21-silver-CH1A-W_Amadeus',
  kind: 'mso21-silver-medal',
  user: 'w_amadeus',
  date: ISODate('2021-08-30T23:59:00Z'),
  url: '//playstrategy.org/tournament/YxVbtw8B',
  name: 'MSO 2021 Silver Medal - Bullet Chess Arena',
});
db.trophy.insert({
  _id: 'mso21-silver-DRFK-derarzt',
  kind: 'mso21-silver-medal',
  user: 'derarzt',
  date: ISODate('2021-08-30T23:59:00Z'),
  url: '//playstrategy.org/swiss/NzwZ3G2A',
  name: 'MSO 2021 Silver Medal - Frysk Draughts',
});
db.trophy.insert({
  _id: 'mso21-silver-DRGA-derarzt',
  kind: 'mso21-silver-medal',
  user: 'derarzt',
  date: ISODate('2021-09-01T23:59:00Z'),
  url: '//playstrategy.org/swiss/qwwHGzdi',
  name: 'MSO 2021 Silver Medal - Antidraughts',
});
db.trophy.insert({
  _id: 'mso21-silver-DRFC-Ayala7',
  kind: 'mso21-silver-medal',
  user: 'ayala7',
  date: ISODate('2021-09-08T23:59:00Z'),
  url: '//playstrategy.org/swiss/EY1k6otN',
  name: 'MSO 2021 Silver Medal - Frisian Draughts',
});

//BRONZE
db.trophy.insert({
  _id: 'mso21-bronze-CHKH-Robert_Kreisl',
  kind: 'mso21-bronze-medal',
  user: 'robert_kreisl',
  date: ISODate('2021-08-18T23:59:00Z'),
  url: '//playstrategy.org/tournament/8qlcur2U',
  name: 'MSO 2021 Bronze Medal - King of the Hill',
});
db.trophy.insert({
  _id: 'mso21-bronze-LOBZ-kspttw',
  kind: 'mso21-bronze-medal',
  user: 'kspttw',
  date: ISODate('2021-08-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/2ciF7lI1',
  name: 'MSO 2021 Bronze Medal - Lines of Action Blitz',
});
db.trophy.insert({
  _id: 'mso21-bronze-CH9S-MaestroASK',
  kind: 'mso21-bronze-medal',
  user: 'maestroask',
  date: ISODate('2021-08-14T23:59:00Z'),
  url: '//playstrategy.org/swiss/VG1X1mdn',
  name: 'MSO 2021 Bronze Medal - Chess960',
});
db.trophy.insert({
  _id: 'mso21-bronze-LOWC-SOLFAREMI',
  kind: 'mso21-bronze-medal',
  user: 'solfaremi',
  date: ISODate('2021-08-20T23:59:00Z'),
  url: '//playstrategy.org/swiss/HiIWXAGJ',
  name: 'MSO 2021 Bronze Medal - Lines of Action World Championship',
});
db.trophy.insert({
  _id: 'mso21-bronze-CHRS-sehyunkwon-1998',
  kind: 'mso21-bronze-medal',
  user: 'sehyunkwon-1998',
  date: ISODate('2021-08-22T23:59:00Z'),
  url: '//playstrategy.org/swiss/LBtq7lhA',
  name: 'MSO 2021 Bronze Medal - Chess Swiss',
});
db.trophy.insert({
  _id: 'mso21-bronze-CHHO-MaestroASK',
  kind: 'mso21-bronze-medal',
  user: 'maestroask',
  date: ISODate('2021-08-22T23:59:00Z'),
  url: '//playstrategy.org/tournament/QtDUekTp',
  name: 'MSO 2021 Bronze Medal - Horde',
});
db.trophy.insert({
  _id: 'mso21-bronze-CHHO-Saravanan',
  kind: 'mso21-bronze-medal',
  user: 'saravanan',
  date: ISODate('2021-08-22T23:59:00Z'),
  url: '//playstrategy.org/tournament/QtDUekTp',
  name: 'MSO 2021 Bronze Medal - Horde',
});
db.trophy.insert({
  _id: 'mso21-bronze-CHRK-Raymond_Duck',
  kind: 'mso21-bronze-medal',
  user: 'raymond_duck',
  date: ISODate('2021-08-25T23:59:00Z'),
  url: '//playstrategy.org/tournament/OkNDUywg',
  name: 'MSO 2021 Bronze Medal - Racing Kings',
});
db.trophy.insert({
  _id: 'mso21-bronze-CHCR-BenP',
  kind: 'mso21-bronze-medal',
  user: 'benp',
  date: ISODate('2021-08-26T23:59:00Z'),
  url: '//playstrategy.org/tournament/M8tYK78b',
  name: 'MSO 2021 Bronze Medal - Crazyhouse Arena',
});
db.trophy.insert({
  _id: 'mso21-bronze-DRIN-Vashod',
  kind: 'mso21-bronze-medal',
  user: 'vashod',
  date: ISODate('2021-08-26T23:59:00Z'),
  url: '//playstrategy.org/swiss/JLeZ3Rp7',
  name: 'MSO 2021 Bronze Medal - International Draughts',
});
db.trophy.insert({
  _id: 'mso21-bronze-CHAT-Raymond_Duck',
  kind: 'mso21-bronze-medal',
  user: 'raymond_duck',
  date: ISODate('2021-08-27T23:59:00Z'),
  url: '//playstrategy.org/tournament/JYPFRsDy',
  name: 'MSO 2021 Bronze Medal - Atomic Arena',
});
db.trophy.insert({
  _id: 'mso21-bronze-CH1S-Koni',
  kind: 'mso21-bronze-medal',
  user: 'koni',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/5YxeJMW4',
  name: 'MSO 2021 Bronze Medal - Bullet Chess Swiss',
});
db.trophy.insert({
  _id: 'mso21-bronze-DRBT-egormmaksymov341',
  kind: 'mso21-bronze-medal',
  user: 'egormmaksymov341',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/YMYfrCvg',
  name: 'MSO 2021 Bronze Medal - Breakthrough Draughts',
});
db.trophy.insert({
  _id: 'mso21-bronze-DRBT-raphael20odrich',
  kind: 'mso21-bronze-medal',
  user: 'raphael20odrich',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/YMYfrCvg',
  name: 'MSO 2021 Bronze Medal - Breakthrough Draughts',
});
db.trophy.insert({
  _id: 'mso21-bronze-CHAT3-knightleap',
  kind: 'mso21-bronze-medal',
  user: 'knightleap',
  date: ISODate('2021-08-28T23:59:00Z'),
  url: '//playstrategy.org/swiss/Zj6aJKaX',
  name: 'MSO 2021 Bronze Medal - Atomic Swiss',
});
db.trophy.insert({
  _id: 'mso21-bronze-CHAC-QueenEatingDragon',
  kind: 'mso21-bronze-medal',
  user: 'queeneatingdragon',
  date: ISODate('2021-08-29T23:59:00Z'),
  url: '//playstrategy.org/tournament/ReSgHIjk',
  name: 'MSO 2021 Bronze Medal - Antichess',
});
db.trophy.insert({
  _id: 'mso21-bronze-CH1A-LunarGuardianNasus',
  kind: 'mso21-bronze-medal',
  user: 'lunarguardiannasus',
  date: ISODate('2021-08-30T23:59:00Z'),
  url: '//playstrategy.org/tournament/YxVbtw8B',
  name: 'MSO 2021 Bronze Medal - Bullet Chess Arena',
});
db.trophy.insert({
  _id: 'mso21-bronze-DRFK-berserker',
  kind: 'mso21-bronze-medal',
  user: 'berserker',
  date: ISODate('2021-08-30T23:59:00Z'),
  url: '//playstrategy.org/swiss/NzwZ3G2A',
  name: 'MSO 2021 Bronze Medal - Frysk Draughts',
});
db.trophy.insert({
  _id: 'mso21-bronze-DRGA-raphael20odrich',
  kind: 'mso21-bronze-medal',
  user: 'raphael20odrich',
  date: ISODate('2021-09-01T23:59:00Z'),
  url: '//playstrategy.org/swiss/qwwHGzdi',
  name: 'MSO 2021 Bronze Medal - Antidraughts',
});
db.trophy.insert({
  _id: 'mso21-bronze-DRFC-raphael20odrich',
  kind: 'mso21-bronze-medal',
  user: 'raphael20odrich',
  date: ISODate('2021-09-08T23:59:00Z'),
  url: '//playstrategy.org/swiss/EY1k6otN',
  name: 'MSO 2021 Bronze Medal - Frisian Draughts',
});
