if (typeof jsonFile === 'undefined') {
  throw new Error('jsonFile variable not set. Pass with --eval "var jsonFile=\'yourfile.json\'"');
}

//note; 'var puzzles=' was added to the start of puzzle json file during download process
load(jsonFile);

const collection = db.puzzle2_puzzle;

const idChars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
const idLength = 5;

function randomId() {
  let result = '';
  for (let i = idLength; i > 0; --i) result += idChars[Math.floor(Math.random() * idChars.length)];
  return result;
}

function calculateRating(line) {
  const moves = line.trim().split(' ');
  const numMoves = Math.ceil(moves.length / 2);
  return Math.min(1000 + (numMoves - 1) * 200, 2800);
}

puzzles.forEach(function (puzzle) {
  if (puzzle.vote === undefined) puzzle.vote = 1.0;
  if (puzzle.plays === undefined) puzzle.plays = 0;
  if (puzzle.glicko === undefined) {
    puzzle.glicko = { r: calculateRating(puzzle.line), d: 500.0, v: 0.09 };
  }

  let unique = false;
  let id;
  while (!unique) {
    id = randomId();
    if (!collection.findOne({ _id: id })) unique = true;
  }
  puzzle._id = id;
  collection.insertOne(puzzle);
  print('Inserted puzzle with id: ' + id);
});
