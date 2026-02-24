use('lichess');

// Run in mongosh
const start = new Date('2026-02-01T00:00:00Z');
const end = new Date('2026-03-01T00:00:00Z');

// 1. Find puzzles with 'day' in February 2026
const puzzles = db.puzzle2_puzzle
  .find(
    {
      day: { $gte: start, $lt: end },
    },
    { _id: 1, day: 1, plays: 1, l: 1, v: 1 },
  )
  .toArray();

let totalOccurrences = 0;
let totalPlays = 0;

puzzles.forEach(p => {
  totalPlays += p.plays || 0;

  const puzzleId = p._id;
  const dayStart = new Date(p.day);
  const dayEnd = new Date(dayStart);
  dayEnd.setUTCHours(23, 59, 59, 999);

  const count = db.puzzle2_round.countDocuments({
    _id: { $regex: `:${puzzleId}$` },
    d: { $gte: dayStart, $lte: dayEnd },
  });

  totalOccurrences += count;
  print(
    `Puzzle ${puzzleId} on day ${dayStart.toISOString()}: ${count} occurrences, ${p.plays || 0} plays, l: ${p.l}, v: ${p.v}`,
  );
});

const numDays = puzzles.length;
const avgOccurrences = numDays > 0 ? totalOccurrences / numDays : 0;

print(`Total occurrences for daily puzzles in Feb 2026: ${totalOccurrences}`);
print(`Total plays for daily puzzles in Feb 2026: ${totalPlays}`);
print(`Average occurrences per day: ${avgOccurrences}`);
