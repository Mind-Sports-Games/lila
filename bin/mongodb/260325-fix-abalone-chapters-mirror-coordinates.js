// Migration (mirror only): fix UCI and SAN coordinates in all standard Abalone study chapters.
//
// Applies ONLY the coordinate mirror — does NOT convert Nacre → PlayStrategy dest.
//
// Coordinate mirror (OLD → NEW system):
//   OLD: key = file(a-i) + rank(1-9)
//   NEW: swap file-index ↔ rank digit. e.g. "e1" → "a5", "e3" → "c5".
//
//   Non-capture pushes: last-move arrow points to the opponent's OLD position
//   ("Fake Nacre" dest (ABAPRO) = where opponent was before the push) instead of their FINAL
//   position (PlayStrategy dest = first empty cell after the push). Game replay
//   is still correct because the engine handles Nacre format internally.
//
//   3v2 captures: last-move arrow points to the FIRST opponent's position instead
//   of the ejected (second) opponent's position.
//
// SAN format (from strategygames Replay.scala):
//   Non-capture: origdest  (same as u)
//   Capture:     origxdest (x between orig and dest)
//
// GL=7 (Abalone game logic), v=1.
//
// Safe to re-run: if the backup collection exists, originals are restored first.

// ---------------------------------------------------------------------------
// Step 1: backup — copy all matching chapters to a separate collection.
// To roll back: re-run this script (restores from backup, then re-mirrors).
// Or manually: drop study_chapter_flat where gl=7/v=1, then insert from backup.
// ---------------------------------------------------------------------------
const BAK = 'study_chapter_flat_bak_2603';
const FILTER = { 'setup.variant.gl': 7, 'setup.variant.v': 1 };

if (db.getCollectionNames().indexOf(BAK) >= 0) {
  // Backup exists from a previous run: restore original docs before re-migrating.
  print('Backup collection ' + BAK + ' found — restoring originals before re-running...');
  db[BAK].find({}).forEach(function (doc) {
    db.study_chapter_flat.replaceOne({ _id: doc._id }, doc);
  });
  print('Restored ' + db[BAK].count() + ' chapters from backup.');
} else {
  db.study_chapter_flat.aggregate([{ $match: { 'setup.variant.gl': 7, 'setup.variant.v': 1 } }, { $out: BAK }]);
  const backedUp = db[BAK].count();
  print('Backup created: ' + BAK + ' (' + backedUp + ' documents)');
}

// ---------------------------------------------------------------------------
// Step 2: migration
// ---------------------------------------------------------------------------

function mirrorKey(key) {
  // "e1" → "a5"
  const letter = key[0];
  const num = parseInt(key.slice(1));
  return String.fromCharCode('a'.charCodeAt(0) + num - 1) + (letter.charCodeAt(0) - 'a'.charCodeAt(0) + 1);
}

function mirrorUci(u) {
  return mirrorKey(u.slice(0, 2)) + mirrorKey(u.slice(2, 4));
}

function getScores(fen) {
  // Abalone FEN: "boardFen p1score p2score player halfMoves fullMoves ..."
  const p = fen.split(' ');
  return [parseInt(p[1]), parseInt(p[2])];
}

let chaptersUpdated = 0;
let nodesUpdated = 0;

db.study_chapter_flat.find({ 'setup.variant.gl': 7, 'setup.variant.v': 1 }).forEach(chapter => {
  const root = chapter.root;
  let changed = false;

  for (const pathKey in root) {
    if (pathKey === '_') continue; // root node has no move
    const node = root[pathKey];
    if (!node.u) continue;
    if (!node.f) continue; // skip nodes without FEN — avoids TypeError in getScores

    const parentKey = pathKey.length === 2 ? '_' : pathKey.slice(0, -2);
    const parentFen = root[parentKey] && root[parentKey].f;
    if (!parentFen) continue;

    const newU = mirrorUci(node.u);

    const [pa, pb] = getScores(parentFen);
    const [ca, cb] = getScores(node.f);
    const isCapture = ca > pa || cb > pb;

    const orig = newU.slice(0, 2);
    const dest = newU.slice(2, 4);
    const newS = isCapture ? orig + 'x' + dest : newU;

    root[pathKey].u = newU;
    root[pathKey].s = newS;
    changed = true;
    nodesUpdated++;
  }

  if (changed) {
    // printjson({ // for dry-run verification
    //   _id: chapter._id,
    //   $set: { root: root }
    // });
    db.study_chapter_flat.updateOne({ _id: chapter._id }, { $set: { root: root } });
    chaptersUpdated++;
  }
});

print('Chapters updated: ' + chaptersUpdated);
print('Nodes updated:    ' + nodesUpdated);
