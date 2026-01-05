// Step 1: Get duplicate IDs
const totalCount = db.puzzle2_puzzle.countDocuments();

const cursor = db.puzzle2_puzzle.aggregate([
  {
    $group: {
      _id: { fen: '$fen', v: '$v', l: '$l' },
      ids: { $push: '$_id' },
      count: { $sum: 1 },
    },
  },
  { $match: { count: { $gt: 1 } } },
  {
    $project: {
      duplicateIds: { $slice: ['$ids', 1, { $subtract: ['$count', 1] }] },
    },
  },
]);

let idsToRemove = [];
cursor.forEach(doc => (idsToRemove = idsToRemove.concat(doc.duplicateIds)));

print('Total puzzles before deletion:', totalCount);
print('Number of duplicate puzzles to delete:', idsToRemove.length);

// Step 2: Remove duplicates
const result = db.puzzle2_puzzle.deleteMany({ _id: { $in: idsToRemove } });

print('Number of puzzles deleted:', result.deletedCount);
print('Total puzzles after deletion:', db.puzzle2_puzzle.countDocuments());

//Usage:
//mongosh --host <mongo_url> lichess delete-duplicate-puzzles.js
