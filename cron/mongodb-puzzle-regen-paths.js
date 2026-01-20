/* Generates and saves a new generation of puzzle paths.
 * Drops the previous generation.
 *
 * mongosh <IP>:<PORT>/<DB> mongodb-puzzle-regen-paths.js
 *
 * Must run on the puzzle database.
 * Should run every 60 minutes.
 * Should complete within 3 minutes.
 * OK to run many times in a row.
 * OK to skip runs.
 * NOT OK to run concurrently.
 *
 * might require this mongodb config: (https://jira.mongodb.org/browse/SERVER-44174)
 * setParameter:
 *   internalQueryMaxPushBytes: 314572800
 */

const verbose = false;
const puzzleColl = db.puzzle2_puzzle;
const pathCollName = 'puzzle2_path';
const pathColl = db[pathCollName];
const pathNextColl = db.puzzle2_path_next;
const maxRatingBuckets = 20;
const maxPathLength = 200;
const maxPuzzlesPerTheme = 3800000; // avoids memory restrictions in some envs, like:
// MongoServerError: document constructed by $facet is 104948160 bytes, which exceeds the limit of 104857600 bytes
const maxPathsPerGroup = 30;

const sep = '|';

const generation = Date.now();
const retentionMs = 70 * 60 * 1000; // 70 minutes (make this slightly larger than the cron interval)
const cutoff = Date.now() - retentionMs;

pathNextColl.drop({});

const tiers = [
  ['top', 20 / 100],
  ['good', 50 / 100],
  ['all', 95 / 100],
];

// const mixBoundaries = [
//   100, 650, 800, 900, 1000, 1100, 1200, 1270, 1340, 1410, 1480, 1550, 1620, 1690, 1760, 1830, 1900, 2000, 2100, 2200,
//   2350, 2500, 2650, 2800, 9999,
// ];
//Due to lack of initial puzzles we essentially want one large bucket for all puzzles.
const reducedMixBoundaries = [100, 500, 4000, 9999];

const themes = puzzleColl.distinct('themes', {}).filter(t => t && t != 'checkFirst');

const variantKeys = puzzleColl
  .aggregate([{ $group: { _id: { v: '$v', l: '$l' } } }])
  .toArray()
  .map(v => `${v._id.l}${sep}${v._id.v}`);

if (verbose) print(`variantKeys: ${variantKeys}, themes: ${themes}`);

function chunkify(a, n) {
  let len = a.length,
    out = [],
    i = 0,
    size;
  if (len % n === 0) {
    size = Math.floor(len / n);
    while (i < len) {
      out.push(a.slice(i, (i += size)));
    }
  } else
    while (i < len) {
      size = Math.ceil((len - i) / n--);
      out.push(a.slice(i, (i += size)));
    }
  return out;
}
const padRating = r => (r < 1000 ? '0' : '') + r;

let anyBuggy = false;

variantKeys.forEach(variantkey => {
  [...themes, 'mix'].forEach(theme => {
    const selector = {
      ...{ issue: { $exists: false } },
      l: parseInt(variantkey.split(sep)[0], 10),
      v: parseInt(variantkey.split(sep)[1], 10),
      themes:
        theme == 'mix'
          ? {
              $ne: 'equality',
            }
          : theme == 'equality'
            ? 'equality'
            : {
                $eq: theme,
                $ne: 'equality',
              },
    };

    const bucketBase = {
      groupBy: '$glicko.r',
      output: { puzzle: { $push: { id: '$_id', vote: '$vote' } } },
    };

    const nbPuzzles = puzzleColl.countDocuments(selector);

    if (!nbPuzzles) return [];

    const themeMaxPathLength = Math.max(10, Math.min(maxPathLength, Math.round(nbPuzzles / 150)));

    //Note dynamic bucket ranges existsed priviously for non mixed themes - we might want these back in future
    const bucketStages = [
      {
        $bucket: {
          ...bucketBase,
          boundaries: reducedMixBoundaries,
        },
      },
    ];

    const pipeline = [
      {
        $match: selector,
      },
      ...(theme == 'mix' ? [{ $sample: { size: maxPuzzlesPerTheme } }] : []),
      ...bucketStages,
      {
        $unwind: '$puzzle',
      },
      {
        $sort: {
          'puzzle.vote': -1,
        },
      },
      {
        $group: {
          _id: '$_id',
          total: {
            $sum: 1,
          },
          puzzles: {
            $push: '$puzzle.id',
          },
        },
      },
      {
        $facet: tiers.reduce(
          (facets, [name, ratio]) => ({
            ...facets,
            ...{
              [name]: [
                {
                  $project: {
                    total: 1,
                    puzzles: {
                      $slice: [
                        '$puzzles',
                        {
                          $round: {
                            $multiply: ['$total', ratio],
                          },
                        },
                      ],
                    },
                  },
                },
                {
                  $unwind: '$puzzles',
                },
                {
                  $sample: {
                    // shuffle
                    size: 10 * 1000 * 1000,
                  },
                },
                {
                  $group: {
                    _id: '$_id',
                    puzzles: {
                      $addToSet: '$puzzles',
                    },
                  },
                },
                {
                  $sort: {
                    '_id.min': 1,
                  },
                },
                {
                  $addFields: {
                    tier: name,
                  },
                },
              ],
            },
          }),
          {},
        ),
      },
      {
        $project: {
          bucket: {
            $concatArrays: tiers.map(t => '$' + t[0]),
          },
        },
      },
      {
        $unwind: '$bucket',
      },
      {
        $replaceRoot: {
          newRoot: '$bucket',
        },
      },
    ];

    if (verbose)
      print(`varaint: ${variantkey}, theme: ${theme}, puzzles: ${nbPuzzles}, path length: ${themeMaxPathLength}`);

    let prevTier = '',
      indexInTier = 0,
      buggy = false;

    puzzleColl
      .aggregate(pipeline, {
        allowDiskUse: true,
        comment: 'regen-paths',
      })
      .forEach(bucket => {
        if (prevTier == bucket.tier) indexInTier++;
        else {
          indexInTier = 0;
          prevTier = bucket.tier;
        }
        const pathLength = Math.max(10, Math.min(maxPathLength, Math.round(bucket.puzzles.length / 30)));
        const bucketIndex = reducedMixBoundaries.indexOf(bucket._id);
        const ratingMin = bucket._id;
        const ratingMax =
          bucketIndex >= 0 && bucketIndex < reducedMixBoundaries.length - 1
            ? reducedMixBoundaries[bucketIndex + 1]
            : 9999;

        const nbPaths = Math.max(1, Math.floor(bucket.puzzles.length / pathLength));
        const allPaths = chunkify(bucket.puzzles, nbPaths);
        const paths = allPaths.slice(0, maxPathsPerGroup);
        buggy = buggy || (ratingMin == 100 && ratingMax == 9999) || ratingMin > ratingMax;
        anyBuggy = anyBuggy || buggy;
        if (verbose || buggy)
          print(
            ` ${variantkey} ${theme} ${indexInTier} ${bucket.tier} ${ratingMin}->${ratingMax} puzzles: ${bucket.puzzles.length} pathLength: ${pathLength} paths: ${allPaths.length}->${paths.length}`,
          );

        pathNextColl.insertMany(
          paths.map((ids, j) => ({
            _id: `${variantkey}${sep}${theme}${sep}${bucket.tier}${sep}${padRating(ratingMin)}-${padRating(
              ratingMax,
            )}${sep}${generation}${sep}${j}`,
            min: `${variantkey}${sep}${theme}${sep}${bucket.tier}${sep}${padRating(ratingMin)}`,
            max: `${variantkey}${sep}${theme}${sep}${bucket.tier}${sep}${padRating(ratingMax)}`,
            ids,
            tier: bucket.tier,
            theme: theme,
            l: parseInt(variantkey.split(sep)[0], 10),
            v: parseInt(variantkey.split(sep)[1], 10),
            gen: generation,
          })),
          {
            ordered: false,
          },
        );
      });

    if (!buggy) {
      const idPrefix = `${variantkey}${sep}${theme}${sep}`;
      const mergeResult = pathNextColl.aggregate([{ $merge: pathCollName }]).toArray(); // blocks until merge is done
      // Optionally check mergeResult for errors, but .toArray() will throw if the merge fails
      pathColl.deleteMany({ /* theme: theme */ _id: new RegExp('^' + idPrefix), gen: { $lt: cutoff } });
    }
    pathNextColl.drop({});
  });
});

if (!anyBuggy) {
  const res = pathColl.deleteMany({ gen: { $lt: cutoff } });
  if (verbose) print(`Deleted ${res.deletedCount} other gen paths`);
}
