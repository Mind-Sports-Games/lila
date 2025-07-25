print(
  JSON.stringify(
    db.tournament_pairing
      .aggregate([
        {
          $match: {
            s: { $in: [30, 32, 39, 40, 41, 42, 60, 35, 45, 46, 47, 48, 49] },
          },
        },
        {
          $lookup: {
            from: 'tournament2',
            localField: 'tid',
            foreignField: '_id',
            as: 't2',
          },
        },
        { $unwind: '$t2' },
        {
          $match: {
            't2.schedule.freq': { $in: ['shield', 'weekly', 'yearly'] },
          },
        },
        {
          $group: {
            _id: {
              lib: { $ifNull: ['$t2.lib', 0] },
              variant: { $ifNull: ['$t2.variant', 1] },
            },
            games_count: {
              $sum: {
                $cond: [{ $in: ['$s', [30, 32, 39, 40, 41, 42, 60]] }, 1, 0],
              },
            },
            avg_t: {
              $avg: {
                $cond: [{ $in: ['$s', [30, 32, 39, 40, 41, 42, 60]] }, '$t', null],
              },
            },
            std_t: {
              $stdDevPop: {
                $cond: [{ $in: ['$s', [30, 32, 39, 40, 41, 42, 60]] }, '$t', null],
              },
            },
            berserk_count: {
              $sum: {
                $cond: [
                  { $in: ['$s', [30, 32, 39, 40, 41, 42, 60]] },
                  { $add: [{ $cond: ['$b1', 1, 0] }, { $cond: ['$b2', 1, 0] }] },
                  0,
                ],
              },
            },
            timeout_count: {
              $sum: {
                $cond: [{ $in: ['$s', [35, 45, 46, 47, 48, 49]] }, 1, 0],
              },
            },
            tids: { $addToSet: '$tid' },
            bot_count: {
              $sum: {
                $cond: [
                  {
                    $and: [
                      { $in: ['$s', [30, 32, 39, 40, 41, 42, 60]] },
                      { $isArray: '$u' },
                      { $gt: [{ $size: { $setIntersection: ['$u', ['pst-rando', 'pst-greedy-tom']] } }, 0] },
                    ],
                  },
                  1,
                  0,
                ],
              },
            },
          },
        },
        {
          $project: {
            _id: 0,
            lib: '$_id.lib',
            variant: '$_id.variant',
            games_count: 1,
            avg_t: 1,
            std_t: 1,
            berserk_count: 1,
            berserk_rate: {
              $cond: [
                { $eq: ['$games_count', 0] },
                0,
                { $divide: ['$berserk_count', { $multiply: ['$games_count', 2] }] },
              ],
            },
            timeout_count: 1,
            timeout_rate: {
              $cond: [
                { $eq: [{ $add: ['$timeout_count', '$games_count'] }, 0] },
                0,
                { $divide: ['$timeout_count', { $add: ['$timeout_count', '$games_count'] }] },
              ],
            },
            num_tournaments: { $size: '$tids' },
            bot_count: 1,
            bot_rate: {
              $cond: [{ $eq: ['$games_count', 0] }, 0, { $divide: ['$bot_count', '$games_count'] }],
            },
            human_count: { $subtract: ['$games_count', '$bot_count'] },
          },
        },
      ])
      .toArray(),
    null,
    2,
  ),
);
