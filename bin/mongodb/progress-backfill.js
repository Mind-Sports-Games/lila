// $ mongosh <db> progress-backfill.js
//
// One-off backfill of the `progress` collection (modules/progress) for players who did things before
// the onboarding checklist existed. Run after deploy. It only sets steps that are absent and only
// raises badge levels, so it is safe to re-run and safe to run while the site is up.
//
// Sources are limited to user fields, the `pref` and `crosstable2` collections and small distinct
// lookups: nothing here scans the `game` collection. Known gaps, all ticked live on the next action:
// - playHuman and playBot come from crosstables, so games against anonymous players are not seen.
// - playCorrespondence comes from perfs, so only rated correspondence games are seen, and only
//   standard chess ones are told apart from other speeds (variants share one perf for every speed).
// - playTournament counts joining an arena or swiss, whether or not a game was played.
// - variants come from perfs.nb, which, like the live rule, counts rated games only.
// - customiseBoard compares the saved theme and piece set to today's defaults, so a player whose pref
//   was saved under an older default counts as having customised.
// - not backfilled: chooseGames (placeholder for a future feature), inviteFriend (challenges expire),
//   playMedley (needs game data), checkPreferences (a pref document does not show the pages were
//   opened) and the visit-only steps memoryGame, analyseGame, boardEditor, watchTv, watchStreamer.
// Backfilled badges send no notification.
//
// STEP_KEYS, CORE_KEYS, NOT_FOR_KIDS_KEYS and VARIANT_KEYS mirror lila.progress.Step and
// Progress.variantKeys (PerfType.allVariant); DEFAULT_THEME and DEFAULT_PIECE_SET mirror
// GameFamily.boardThemeDefault and pieceSetDefault in strategygames, keyed by GameFamily.id. Keep
// them in step with the code.

const CORE_KEYS = ['chooseGames', 'completeProfile', 'playHuman', 'joinTeam', 'playTournament'];
const STEP_KEYS = CORE_KEYS.concat([
  'playBot',
  'inviteFriend',
  'playCorrespondence',
  'playRated',
  'playMedley',
  'createTournament',
  'solvePuzzle',
  'learnRules',
  'coordinates',
  'memoryGame',
  'analyseGame',
  'boardEditor',
  'createStudy',
  'followPlayer',
  'forumPost',
  'watchTv',
  'watchStreamer',
  'customiseBoard',
  'checkPreferences',
  'patron',
]);

const NOT_FOR_KIDS_KEYS = ['forumPost', 'watchStreamer'];

const SPEED_KEYS = ['ultraBullet', 'bullet', 'blitz', 'rapid', 'classical', 'correspondence'];

// prettier-ignore
const VARIANT_KEYS = [
  'standard', 'chess960', 'kingOfTheHill', 'threeCheck', 'fiveCheck', 'antichess', 'atomic', 'horde',
  'racingKings', 'crazyhouse', 'noCastling', 'monster', 'linesOfAction', 'scrambledEggs', 'frisian',
  'frysk', 'international', 'antidraughts', 'breakthrough', 'russian', 'brazilian', 'pool',
  'portuguese', 'english', 'dameo', 'shogi', 'xiangqi', 'minishogi', 'minixiangqi', 'flipello',
  'flipello10', 'antiflipello', 'octagonflipello', 'amazons', 'breakthroughtroyka',
  'minibreakthroughtroyka', 'oware', 'togyzkumalak', 'bestemshe', 'go9x9', 'go13x13', 'go19x19',
  'backgammon', 'hyper', 'nackgammon', 'abalone', 'grandabalone', 'entropy', 'gomoku', 'pente', 'renju',
];

// prettier-ignore
const DEFAULT_THEME = {
  0: 'maple', 1: 'blue3', 2: 'marble', 3: 'wood', 4: 'green', 5: 'green', 6: 'light-wood', 7: 'blue',
  8: 'leather', 9: 'light-wood', 10: 'classic', 11: 'purple-diag', 12: 'classic', 13: 'blue3', 14: 'wood',
  15: 'wood', 16: 'marble',
};

// prettier-ignore
const DEFAULT_PIECE_SET = {
  0: 'staunty', 1: 'wide_crown', 2: 'check_yb_loa', 3: '2kanji', 4: '2dhanzi', 5: 'disc_flipello',
  6: 'green', 7: 'black_gloss', 8: 'arrow', 9: 'classic_stone', 10: 'wooden', 11: 'staunty', 12: 'classic',
  13: 'wide_crown_dameo', 14: 'classic_entropy', 15: 'classic_stone', 16: 'classic_yonkoma',
};

const now = new Date();

function idSet(coll, field, match) {
  const pipeline = (match ? [{ $match: match }] : []).concat([{ $group: { _id: '$' + field } }]);
  const set = new Set();
  db[coll].aggregate(pipeline, { allowDiskUse: true }).forEach(d => d._id && set.add(d._id));
  print(`${coll}.${field}: ${set.size}`);
  return set;
}

const teamMembers = idSet('team_member', 'user');
const arenaPlayers = idSet('tournament_player', 'uid');
const swissPlayers = idSet('swiss_player', 'u');
const arenaCreators = idSet('tournament2', 'createdBy');
const swissCreators = idSet('swiss', 'createdBy');
const followers = idSet('relation', 'u1', { r: true });
const posters = idSet('f_post', 'userId');
const studyOwners = idSet('study', 'ownerId');
const coordinators = idSet('coordinate_score', '_id');
const learners = idSet('learn_progress', '_id');

const bots = idSet('user4', '_id', { title: 'BOT' });
const playedHuman = new Set();
const playedBot = new Set();
db.crosstable2.find({}, { _id: 1 }).forEach(c => {
  const [a, b] = c._id.split('/');
  (bots.has(b) ? playedBot : playedHuman).add(a);
  (bots.has(a) ? playedBot : playedHuman).add(b);
});
print(`crosstable2: ${playedHuman.size} played a human, ${playedBot.size} played a bot`);

const isCustom = (list, defaults) =>
  Array.isArray(list) && list.some(x => defaults[x.gameFamily] !== undefined && x.name !== defaults[x.gameFamily]);
const customisers = new Set();
db.pref.find({}, { theme: 1, pieceSet: 1 }).forEach(p => {
  if (isCustom(p.theme, DEFAULT_THEME) || isCustom(p.pieceSet, DEFAULT_PIECE_SET)) customisers.add(p._id);
});
print(`pref: ${customisers.size} customised their board`);

const nb = (u, key) => (u.perfs && u.perfs[key] && u.perfs[key].nb) || 0;
const nonEmpty = s => typeof s === 'string' && s.length > 0;

function stepsOf(u) {
  const count = u.count || {};
  const profile = u.profile || {};
  const id = u._id;
  const steps = [];
  if (playedHuman.has(id)) steps.push('playHuman');
  if (teamMembers.has(id)) steps.push('joinTeam');
  if (arenaPlayers.has(id) || swissPlayers.has(id)) steps.push('playTournament');
  if (playedBot.has(id)) steps.push('playBot');
  if (nb(u, 'correspondence') > 0) steps.push('playCorrespondence');
  if ((count.rated || 0) > 0) steps.push('playRated');
  if (arenaCreators.has(id) || swissCreators.has(id)) steps.push('createTournament');
  if (Object.keys(u.perfs || {}).some(k => k.startsWith('puzzle_') && nb(u, k) > 0)) steps.push('solvePuzzle');
  if (learners.has(id)) steps.push('learnRules');
  if (coordinators.has(id)) steps.push('coordinates');
  if (studyOwners.has(id)) steps.push('createStudy');
  if (followers.has(id)) steps.push('followPlayer');
  if (posters.has(id)) steps.push('forumPost');
  if (nonEmpty(profile.country) && (nonEmpty(profile.bio) || nonEmpty(profile.firstName) || nonEmpty(profile.lastName)))
    steps.push('completeProfile');
  if (customisers.has(id)) steps.push('customiseBoard');
  if (u.plan && u.plan.months > 0) steps.push('patron');
  return steps;
}

function variantsOf(u) {
  const played = VARIANT_KEYS.filter(k => k !== 'standard' && nb(u, k) > 0);
  if (nb(u, 'standard') > 0 || SPEED_KEYS.some(k => nb(u, k) > 0)) played.push('standard');
  return played;
}

function onboardedLevel(steps, kid) {
  const has = k => steps[k] !== undefined;
  const all = kid ? STEP_KEYS.filter(k => !NOT_FOR_KIDS_KEYS.includes(k)) : STEP_KEYS;
  if (all.every(has)) return 2;
  if (CORE_KEYS.every(has)) return 1;
  return 0;
}

const USER_FIELDS = { count: 1, perfs: 1, profile: 1, plan: 1, kid: 1 };

let nbUsers = 0;
let nbWritten = 0;
let nbBadges = 0;

db.user4.find({ enabled: true, title: { $ne: 'BOT' } }, USER_FIELDS).forEach(u => {
  nbUsers++;
  const steps = stepsOf(u);
  const variants = variantsOf(u);
  if (!steps.length && !variants.length) return;

  const setSteps = {};
  steps.forEach(k => (setSteps['steps.' + k] = { $ifNull: ['$steps.' + k, now] }));
  db.progress.updateOne(
    { _id: u._id },
    [
      {
        $set: Object.assign(setSteps, {
          variants: { $setUnion: [{ $ifNull: ['$variants', []] }, variants] },
        }),
      },
    ],
    { upsert: true },
  );
  nbWritten++;

  const doc = db.progress.findOne({ _id: u._id });
  const badges = doc.badges || {};
  const badgeSet = {};
  const level = onboardedLevel(doc.steps || {}, u.kid === true);
  if (level > ((badges.onboarded && badges.onboarded.level) || 0)) badgeSet['badges.onboarded'] = { level, at: now };
  if (!badges.allVariants && VARIANT_KEYS.every(k => (doc.variants || []).includes(k)))
    badgeSet['badges.allVariants'] = { level: 1, at: now };
  if (Object.keys(badgeSet).length) {
    db.progress.updateOne({ _id: u._id }, { $set: badgeSet });
    nbBadges++;
  }

  if (nbUsers % 10000 === 0) print(`${nbUsers} users scanned, ${nbWritten} written, ${nbBadges} awarded`);
});

print(`done: ${nbUsers} users scanned, ${nbWritten} progress docs written, ${nbBadges} badge updates`);
