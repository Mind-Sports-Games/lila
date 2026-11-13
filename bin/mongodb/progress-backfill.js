// $ mongosh <db> progress-backfill.js
//
// One-off backfill of the `progress` collection (modules/progress) for players who did things before
// the onboarding checklist existed. Run after deploy. It only sets steps that are absent and only
// raises badge levels, so it is safe to re-run and safe to run while the site is up.
//
// Sources are limited to user fields and small distinct lookups: nothing here scans the `game`
// collection, so bot, correspondence (beyond rated) and medley games are only partly backfilled,
// and visit-only steps (library, memory, analysis, editor, tv, streamer) are not backfilled at all.
// Backfilled badges send no notification.
//
// STEP_KEYS, CORE_KEYS, NOT_FOR_KIDS_KEYS and VARIANT_KEYS mirror lila.progress.Step and
// Progress.variantKeys (PerfType.allVariant); keep them in step with the code.

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

const nb = (u, key) => (u.perfs && u.perfs[key] && u.perfs[key].nb) || 0;
const nonEmpty = s => typeof s === 'string' && s.length > 0;

function stepsOf(u) {
  const count = u.count || {};
  const profile = u.profile || {};
  const id = u._id;
  const steps = [];
  if ((count.game || 0) - (count.ai || 0) > 0) steps.push('playHuman');
  if (teamMembers.has(id)) steps.push('joinTeam');
  if (arenaPlayers.has(id) || swissPlayers.has(id)) steps.push('playTournament');
  if ((count.ai || 0) > 0) steps.push('playBot');
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
