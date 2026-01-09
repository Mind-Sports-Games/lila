db.cache.createIndex({ e: 1 }, { expireAfterSeconds: 0 });

db.challenge.createIndex(
  { seenAt: 1 },
  { partialFilterExpression: { status: 10, timeControl: { $exists: true }, seenAt: { $exists: true } } },
);

db.f_categ.createIndex({ team: 1 });

db.f_post.createIndex({ topicId: 1, troll: 1 });
db.f_post.createIndex({ createdAt: -1, troll: 1 });
db.f_post.createIndex({ userId: 1 });
db.f_post.createIndex({ categId: 1, createdAt: -1 });
db.f_post.createIndex({ topicId: 1, createdAt: -1 });

db.f_topic.createIndex({ categId: 1, troll: 1 });
db.f_topic.createIndex({ categId: 1, updatedAt: -1, troll: 1 });
db.f_topic.createIndex({ categId: 1, slug: 1 });
db.f_topic.createIndex({ categId: 1 }, { partialFilterExpression: { sticky: true } });

db.game5.createIndex({ ca: -1 });
db.game5.createIndex({ us: 1, ca: -1 });
db.game5.createIndex({ 'pgni.user': 1, 'pgni.ca': -1 }, { sparse: 1 });
db.game5.createIndex({ ck: 1 }, { sparse: 1, background: 1 });
db.game5.createIndex({ pl: 1 }, { sparse: true, background: true });
db.game5.createIndex({ 'pgni.h': 1 }, { sparse: true, background: true });
db.game5.createIndex(
  // not inherited from lichess. triggered from https://playstrategy.org/games
  { s: 1 },
  { partialFilterExpression: { s: { $lte: 20 } } },
);

// you may want to run these on the puzzle database
db.puzzle2_round.createIndex({ p: 1 }, { partialFilterExpression: { t: { $exists: true } } });
db.puzzle2_round.createIndex({ u: 1, d: -1 }, { partialFilterExpression: { u: { $exists: 1 } } });
db.puzzle2_puzzle.createIndex({ day: 1 }, { partialFilterExpression: { day: { $exists: true } } });
db.puzzle2_puzzle.createIndex({ themes: 1, votes: -1 });
db.puzzle2_puzzle.createIndex({ themes: 1 });
db.puzzle2_puzzle.createIndex({ users: 1 });
db.puzzle2_puzzle.createIndex({ opening: 1, votes: -1 }, { partialFilterExpression: { opening: { $exists: 1 } } });
db.puzzle2_puzzle.createIndex({ tagMe: 1 }, { partialFilterExpression: { tagMe: true } });
db.puzzle2_path.createIndex({ min: 1, max: -1 });
db.msg_msg.createIndex({ tid: 1, date: -1 });

db.msg_thread.createIndex({ users: 1, 'lastMsg.date': -1 });
db.msg_thread.createIndex({ users: 1 }, { partialFilterExpression: { 'lastMsg.read': false } });
db.msg_thread.createIndex({ users: 1, 'maskWith.date': -1 });

db.notify.createIndex({ notifies: 1, read: 1, createdAt: -1 });
db.notify.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 });

db.simul.createIndex({ hostId: 1 }, { partialFilterExpression: { status: 10 } });
db.simul.createIndex({ hostSeenAt: -1 }, { partialFilterExpression: { status: 10, featurable: true } });

db.study.createIndex({ ownerId: 1, createdAt: -1 });
db.study.createIndex({ likes: 1, createdAt: -1 });
db.study.createIndex({ ownerId: 1, updatedAt: -1 });
db.study.createIndex({ likes: 1, updatedAt: -1 });
db.study.createIndex({ rank: -1 });
db.study.createIndex({ createdAt: -1 });
db.study.createIndex({ updatedAt: -1 });
db.study.createIndex({ likers: 1 });
db.study.createIndex({ uids: 1 });
db.study.createIndex({ topics: 1, rank: -1 }, { partialFilterExpression: { topics: { $exists: 1 } } });
db.study.createIndex({ topics: 1, createdAt: -1 }, { partialFilterExpression: { topics: { $exists: 1 } } });
db.study.createIndex({ topics: 1, updatedAt: -1 }, { partialFilterExpression: { topics: { $exists: 1 } } });
db.study.createIndex({ topics: 1, likes: -1 }, { partialFilterExpression: { topics: { $exists: 1 } } });
db.study.createIndex({ uids: 1, rank: -1 }, { partialFilterExpression: { topics: { $exists: 1 } } });

db.study_chapter_flat.createIndex({ studyId: 1, order: 1 });
db.study_chapter_flat.createIndex(
  { 'relay.fideIds': 1 },
  { partialFilterExpression: { 'relay.fideIds': { $exists: true } } },
);

db.swiss.createIndex({ teamId: 1, startsAt: 1 });
db.swiss.createIndex({ nextRoundAt: 1 }, { partialFilterExpression: { nextRoundAt: { $exists: true } } });
db.swiss.createIndex({ featurable: 1 }, { partialFilterExpression: { featurable: true, 'settings.i': { $lte: 600 } } });

db.swiss_pairing.createIndex({ s: 1, p: 1, r: 1 });
db.swiss_pairing.createIndex({ t: 1 }, { partialFilterExpression: { t: true } });
db.swiss_pairing.createIndex({ mmids: 1 });

db.swiss_player.createIndex({ s: 1, c: -1 });

db.team.createIndex({ enabled: 1, nbMembers: -1 });
db.team.createIndex({ createdAt: -1 });
db.team.createIndex({ createdBy: 1 });
db.team.createIndex({ leaders: 1 });

db.team_member.createIndex({ team: 1 });
db.team_member.createIndex({ user: 1 });
db.team_member.createIndex({ team: 1, date: -1 });
db.team_member.createIndex({ team: 1, perms: 1 }, { partialFilterExpression: { perms: { $exists: 1 } } });

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

db.user4.createIndex({ 'count.game': -1 });
db.user4.createIndex({ title: 1 }, { partialFilterExpression: { title: { $exists: 1 } } });
db.user4.createIndex({ email: 1 }, { unique: true, partialFilterExpression: { email: { $exists: 1 } } });
db.user4.createIndex({ roles: 1 }, { partialFilterExpression: { roles: { $exists: 1 } } });
db.user4.createIndex({ prevEmail: 1 }, { sparse: 1 });
db.user4.createIndex(
  { mustConfirmEmail: 1 },
  { partialFilterExpression: { mustConfirmEmail: { $exists: 1 } }, expireAfterSeconds: 3600 * 24 * 3 },
);
