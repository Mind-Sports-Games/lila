// Migration: mirror shape coordinates (arrows and circles) in all standard Abalone study chapters.
//
// Companion to 260325-fix-abalone-chapters-mirror-coordinates.js, which handled UCI/SAN.
// This script applies the same coordinate mirror to shapes stored on each node.
//
// Shape storage (field "h" on each node):
//   Circle: { "b": brush, "p": "e1" }          → mirror "p"
//   Arrow:  { "b": brush, "o": "e1", "d": "e3" } → mirror "o" and "d"
//
// Coordinate mirror (OLD → NEW system):
//   Swap file-index ↔ rank digit. e.g. "e1" → "a5", "e3" → "c5".
//
// Applies to all nodes including the root node ("_"), which can have shapes.
// GL=7 (Abalone game logic), v=1.
//
// Safe to re-run: if the backup collection exists, shape nodes are restored first.
// To roll back manually: restore from study_chapter_flat_bak_shapes_2603
//   (each doc: { _id: chapterId, nodes: { pathKey: [shapes] } }).

// ---------------------------------------------------------------------------
// Step 1: backup — store only the shape nodes (field "h") of each chapter.
// Backup doc: { _id: chapterId, nodes: { pathKey: [shapes], ... } }
// Restore: for each backed-up node, $set root.<pathKey>.h back to original.
// ---------------------------------------------------------------------------
const BAK = 'study_chapter_flat_bak_shapes_2603';
const FILTER = { 'setup.variant.gl': 7, 'setup.variant.v': 1 };

if (db.getCollectionNames().indexOf(BAK) >= 0) {
  print('Backup collection ' + BAK + ' found — restoring originals before re-running...');
  let restored = 0;
  db[BAK].find({}).forEach(function (doc) {
    const updates = {};
    for (const pathKey in doc.nodes) {
      updates['root.' + pathKey + '.h'] = doc.nodes[pathKey];
    }
    db.study_chapter_flat.updateOne({ _id: doc._id }, { $set: updates });
    restored++;
  });
  print('Restored ' + restored + ' chapters from backup.');
} else {
  let backedUp = 0;
  db.study_chapter_flat.find(FILTER).forEach(function (chapter) {
    const nodes = {};
    for (const pathKey in chapter.root) {
      const h = chapter.root[pathKey].h;
      if (h && h.length) nodes[pathKey] = h;
    }
    if (Object.keys(nodes).length) {
      db[BAK].insertOne({ _id: chapter._id, nodes: nodes });
      backedUp++;
    }
  });
  print('Backup created: ' + BAK + ' (' + backedUp + ' chapters with shapes)');
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

let chaptersUpdated = 0;
let shapesUpdated = 0;

db.study_chapter_flat.find(FILTER).forEach(chapter => {
  const root = chapter.root;
  let changed = false;
  const nodeLines = [];

  for (const pathKey in root) {
    const node = root[pathKey];
    if (!node.h || !node.h.length) continue;

    const oldH = JSON.stringify(node.h);
    let nodeChanged = false;
    node.h.forEach(shape => {
      if (shape.p !== undefined) {
        // Circle
        shape.p = mirrorKey(shape.p);
        shapesUpdated++;
        nodeChanged = true;
      } else if (shape.o !== undefined && shape.d !== undefined) {
        // Arrow
        shape.o = mirrorKey(shape.o);
        shape.d = mirrorKey(shape.d);
        shapesUpdated++;
        nodeChanged = true;
      }
    });

    if (nodeChanged) {
      changed = true;
      nodeLines.push('  node ' + pathKey + ': ' + oldH + ' → ' + JSON.stringify(node.h));
    }
  }

  if (changed) {
    print('chapter ' + chapter._id + ' (study ' + chapter.studyId + '):');
    nodeLines.forEach(line => print(line));
    db.study_chapter_flat.updateOne({ _id: chapter._id }, { $set: { root: root } });
    chaptersUpdated++;
  }
});

print('Chapters updated: ' + chaptersUpdated);
print('Shapes updated:   ' + shapesUpdated);
