db.challenge.createIndex(
  { seenAt: 1 },
  { partialFilterExpression: { status: 10, timeControl: { $exists: true }, seenAt: { $exists: true } } },
);

db.simul.createIndex({ hostId: 1 }, { partialFilterExpression: { status: 10 } });
db.simul.createIndex({ hostSeenAt: -1 }, { partialFilterExpression: { status: 10, featurable: true } });

db.cache.createIndex({ e: 1 }, { expireAfterSeconds: 0 });

// slow queries
db.game5.createIndex({ ca: -1 });
db.game5.createIndex({ us: 1, ca: -1 });
db.game5.createIndex({ 'pgni.user': 1, 'pgni.ca': -1 }, { sparse: 1 });
db.game5.createIndex({ ck: 1 }, { sparse: 1, background: 1 });
db.game5.createIndex({ pl: 1 }, { sparse: true, background: true });
db.game5.createIndex({ 'pgni.h': 1 }, { sparse: true, background: true });

db.tournament_pairing.createIndex({ tid: 1, d: -1 });
db.tournament_pairing.createIndex({ tid: 1, u: 1, d: -1 });
db.tournament_pairing.createIndex({ tid: 1 }, { partialFilterExpression: { s: { $lt: 30 } }, background: 1 });

db.tournament_player.createIndex({ tid: 1, m: -1 });
db.tournament_player.createIndex({ tid: 1, t: 1, m: -1 }, { partialFilterExpression: { t: { $exists: true } } });
db.tournament_player.createIndex({ tid: 1, uid: 1 }, { unique: true });

db.swiss_pairing.createIndex({ s: 1, p: 1, r: 1 });
db.swiss_pairing.createIndex({ t: 1 }, { partialFilterExpression: { t: true } });
