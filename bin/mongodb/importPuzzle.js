#!/usr/bin/env node

const fs = require('fs');
const { MongoClient } = require('mongodb');

const DBNAME = 'lichess';
const COLLECTION_NAME = 'puzzle2_puzzle';

const idChars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
const idLength = 5;

function randomId() {
  let result = '';
  for (let i = idLength; i > 0; --i) result += idChars[Math.floor(Math.random() * idChars.length)];
  return result;
}

//purely based on number of moves (is 200 the best jump?)
function calculateRating(line) {
  const moves = line.trim().split(' ');
  const numMoves = Math.ceil(moves.length / 2);
  return Math.min(1000 + (numMoves - 1) * 200, 2800);
}

async function importPuzzles(jsonPath, mongoUrl) {
  const client = new MongoClient(mongoUrl);
  await client.connect();
  const db = client.db(DBNAME);
  const collection = db.collection(COLLECTION_NAME);

  const puzzles = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));

  for (const puzzle of puzzles) {
    // Set defaults if missing
    if (puzzle.vote === undefined) puzzle.vote = 1.0;
    if (puzzle.plays === undefined) puzzle.plays = 0;
    if (puzzle.glicko === undefined) {
      puzzle.glicko = { r: calculateRating(puzzle.line), d: 500.0, v: 0.09 };
    }

    let unique = false;
    let id;
    while (!unique) {
      id = randomId();
      const exists = await collection.findOne({ _id: id });
      if (!exists) unique = true;
    }
    puzzle._id = id;
    await collection.insertOne(puzzle);
    console.log(`Inserted puzzle with id: ${id}`);
  }

  await client.close();
}

// Command-line support
if (require.main === module) {
  const [, , jsonPath, mongoUrl] = process.argv;
  if (!jsonPath || !mongoUrl) {
    console.error('Usage: node importPuzzle.js <jsonPath> <mongoUrl>');
    process.exit(1);
  }
  importPuzzles(jsonPath, mongoUrl)
    .then(() => console.log('Import complete.'))
    .catch(err => {
      console.error('Import failed:', err);
      process.exit(1);
    });
}

// Example usage:
//node importPuzzle.js puzzles_7vV2t81N.json mongodb://localhost:27017
