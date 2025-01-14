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

db.notify.createIndex({ notifies: 1, read: 1, createdAt: -1 });
db.notify.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 });

db.timeline_entry.createIndex({ users: 1, date: -1 });
db.timeline_entry.createIndex({ typ: 1, date: -1 });

db.tournament_leaderboard.createIndex({ t: 1 });
db.tournament_leaderboard.createIndex({ u: 1, d: -1 });
db.tournament_leaderboard.createIndex({ u: 1, w: 1 });

db.tournament_pairing.createIndex({ tid: 1, d: -1 });
db.tournament_pairing.createIndex({ tid: 1, u: 1, d: -1 });
db.tournament_pairing.createIndex({ tid: 1 }, { partialFilterExpression: { s: { $lt: 30 } }, background: 1 });

db.tournament_player.createIndex({ tid: 1, m: -1 });
db.tournament_player.createIndex({ tid: 1, t: 1, m: -1 }, { partialFilterExpression: { t: { $exists: true } } });
db.tournament_player.createIndex({ tid: 1, uid: 1 }, { unique: true });

db.tournament2.createIndex({ status: 1 });
db.tournament2.createIndex({ startsAt: 1 });
db.tournament2.createIndex({ 'schedule.freq': 1, startsAt: -1 }, { background: true });
db.tournament2.createIndex({ status: 1, startsAt: 1 }, { partialFilterExpression: { status: 10 }, background: 1 });
db.tournament2.createIndex({ forTeams: 1, startsAt: -1 }, { partialFilterExpression: { forTeams: { $exists: 1 } } });
db.tournament2.createIndex(
  { createdBy: 1, startsAt: -1, status: 1 },
  { partialFilterExpression: { createdBy: { $exists: true } } },
);

db.swiss_pairing.createIndex({ s: 1, p: 1, r: 1 });
db.swiss_pairing.createIndex({ t: 1 }, { partialFilterExpression: { t: true } });
