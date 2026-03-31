// Migration: fix all standard Abalone study chapters.
//
// Three transformations applied per node:
//
// 1. u / s  (UCI + SAN): coordinate mirror + Nacre → PlayStrategy dest.
//    OLD coord system: file(a-i) + rank(1-9),  e.g. "e1".
//    NEW coord system: swap file-index ↔ rank digit, e.g. "e1" → "a5".
//    Non-capture push (Nacre dest = opponent's CURRENT position):
//      → PlayStrategy dest = first EMPTY cell after all pushed marbles.
//    Capture: PlayStrategy dest = last opponent marble before the board edge.
//    Single marble / broadside: mirror only.
//
// 2. h  (shapes): mirror circle and arrow coordinates.
//
// 3. root field names (path keys): rebuilt from the new u values.
//    relay.path and serverEval.path are also remapped.
//
// GL=7, v=1 (standard Abalone).
//
// PREREQUISITE: chapters must be in their ORIGINAL (pre-mirror) state.
// Safe to re-run: restores from this script's own backup if it exists.

const STUDY_ID = ''; // set to a study ID string to migrate a single study, e.g. 'aBcDeFgH'
const BAK = STUDY_ID ? 'study_chapter_flat_bak_' + STUDY_ID + '_2604' : 'study_chapter_flat_bak_combined_2604';
const FILTER = Object.assign({ 'setup.variant.gl': 7, 'setup.variant.v': 1 }, STUDY_ID ? { studyId: STUDY_ID } : {});

if (db.getCollectionNames().indexOf(BAK) >= 0) {
  print('Backup ' + BAK + ' found — restoring originals before re-running...');
  db[BAK].find({}).forEach(doc => db.study_chapter_flat.replaceOne({ _id: doc._id }, doc));
  print('Restored ' + db[BAK].count() + ' chapters from backup.');
} else {
  db.study_chapter_flat.aggregate([{ $match: FILTER }, { $out: BAK }]);
  print('Backup created: ' + BAK + ' (' + db[BAK].count() + ' documents)');
}

// ---------------------------------------------------------------------------
// Coordinate helpers — OLD coord system: file(a-i) + rank(1-9), e.g. "e1"
// ---------------------------------------------------------------------------

function mirrorKey(key) {
  // "e1" → "a5" (swap file-index ↔ rank digit)
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

// Parse board FEN (OLD coord system) → { posKey: 's'|'S' }
// Ranks go 9→1 top-to-bottom. Rank r starts at file index max(0, r-5).
function parseBoard(fen) {
  const rows = fen.split(' ')[0].split('/');
  const pieces = {};
  for (let i = 0; i < rows.length; i++) {
    const rank = 9 - i;
    const startFile = Math.max(0, rank - 5);
    let fileIdx = startFile;
    for (const ch of rows[i]) {
      if (ch >= '1' && ch <= '9') {
        fileIdx += parseInt(ch);
      } else {
        pieces[String.fromCharCode(97 + fileIdx) + rank] = ch;
        fileIdx++;
      }
    }
  }
  return pieces;
}

// Unit direction vector in OLD coord space (delta file_index, delta rank).
function getUnitDir(fromKey, toKey) {
  const df = toKey.charCodeAt(0) - fromKey.charCodeAt(0);
  const dr = parseInt(toKey.slice(1)) - parseInt(fromKey.slice(1));
  const n = Math.max(Math.abs(df), Math.abs(dr));
  return [df / n, dr / n];
}

// Step one cell in direction dir from key.
// Returns null if off the hex board (Abalone Hex5: valid file range for rank r is [max(0,r-5), min(8,r+3)]).
function addDir(key, dir) {
  const f = key.charCodeAt(0) - 97 + dir[0];
  const r = parseInt(key.slice(1)) + dir[1];
  if (r < 1 || r > 9) return null;
  if (f < Math.max(0, r - 5) || f > Math.min(8, r + 3)) return null;
  return String.fromCharCode(97 + f) + r;
}

// Compute new u and s from OLD-coord Nacre-format u + the two FENs.
// parentFen: board state BEFORE this move (OLD coord, for board parsing).
// nodeFen:   board state AFTER  this move (for score-diff capture detection).
function computeNewUciSan(oldU, parentFen, nodeFen) {
  const oldOrig = oldU.slice(0, 2);
  const oldDest = oldU.slice(2, 4);

  const [pa, pb] = getScores(parentFen);
  const [ca, cb] = getScores(nodeFen);
  const isCapture = ca > pa || cb > pb;

  let newU, newS;

  if (isCapture) {
    // Walk from Nacre dest in the push direction while opponent marbles are present.
    // Last in-board position = PlayStrategy capture dest (last marble before the edge).
    // Handles both 3v1 (lastValidPos = oldDest) and 3v2 (one step further).
    const board = parseBoard(parentFen);
    const dir = getUnitDir(oldOrig, oldDest);
    let lastValidPos = oldDest;
    let pos = addDir(oldDest, dir);
    while (pos !== null && board[pos] !== undefined) {
      lastValidPos = pos;
      pos = addDir(pos, dir);
    }
    newU = mirrorKey(oldOrig) + mirrorKey(lastValidPos);
    newS = mirrorKey(oldOrig) + 'x' + mirrorKey(lastValidPos);
  } else {
    const board = parseBoard(parentFen);
    const currentPiece = parentFen.split(' ')[3] === 'b' ? 'S' : 's'; // P1 ('b') uses uppercase 'S'
    const opponentPiece = currentPiece === 's' ? 'S' : 's';

    if (board[oldDest] === opponentPiece) {
      // Non-capture push: Nacre dest = opponent's current position.
      // Walk from oldDest in push direction to the first empty cell = PlayStrategy dest.
      const dir = getUnitDir(oldOrig, oldDest);
      let pos = addDir(oldDest, dir);
      while (pos !== null && board[pos] !== undefined) {
        pos = addDir(pos, dir);
      }
      newU = mirrorKey(oldOrig) + mirrorKey(pos || oldDest); // pos should never be null here
    } else {
      // Single marble move or broadside: dest is the target cell, mirror only.
      newU = mirrorUci(oldU);
    }
    newS = newU; // non-capture SAN = UCI (no 'x')
  }

  return { newU, newS };
}

// ---------------------------------------------------------------------------
// Shape mirroring
// ---------------------------------------------------------------------------

function mirrorShapes(shapes) {
  return shapes.map(shape => {
    const s = Object.assign({}, shape);
    if (s.p !== undefined) {
      s.p = mirrorKey(s.p); // circle
    } else if (s.o !== undefined && s.d !== undefined) {
      s.o = mirrorKey(s.o); // arrow orig
      s.d = mirrorKey(s.d); // arrow dest
    }
    return s;
  });
}

// ---------------------------------------------------------------------------
// Path key encoding — mirrors Path.scala#encodeDbKey + abalone UciCharPair.scala
//
// Pos(x, y).key = ('a' + y) + (x + 1)  →  "a5" means y=0, x=4
//
// posToHashIndex(x, y):
//   x < y  →  y² + x
//   x >= y →  x² + (2x − y)
//
// UciCharPair char = (hashIndex + 35).toChar   (charShift = 35)
//
// MongoDB key escaping (1-to-1, preserves key.length == 2 * depth):
//   '.' (46)  → \u0001      '$' (36)  → \u0002
//   \u0090 (144) → \u0003   \u0091 (145) → \u0004
// ---------------------------------------------------------------------------

function posToHashIndex(x, y) {
  return x < y ? y * y + x : x * x + 2 * x - y;
}

function posKeyToChar(posKey) {
  const y = posKey.charCodeAt(0) - 97; // 'a' = 97
  const x = parseInt(posKey.slice(1)) - 1;
  return String.fromCharCode(posToHashIndex(x, y) + 35);
}

function uciToPairChars(uci) {
  return posKeyToChar(uci.slice(0, 2)) + posKeyToChar(uci.slice(2, 4));
}

function encodeDbKey(s) {
  let r = '';
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if (c === 46)
      r += '\u0001'; // '.' forbidden in MongoDB keys
    else if (c === 36)
      r += '\u0002'; // '$' forbidden in MongoDB keys
    else if (c === 144)
      r += '\u0003'; // \u0090 (e.g. Go pos 109) — was former escape target
    else if (c === 145)
      r += '\u0004'; // \u0091 (e.g. Go pos 110) — was former escape target
    else r += s[i];
  }
  return r;
}

// ---------------------------------------------------------------------------
// Migration
// ---------------------------------------------------------------------------

let chaptersUpdated = 0,
  nodesUpdated = 0,
  shapesUpdated = 0,
  warnings = 0;

db.study_chapter_flat.find(FILTER).forEach(chapter => {
  const root = chapter.root;

  // Root node ("_"): no move, no key change — only mirror shapes.
  const rootNode = root['_'];
  const newRoot = { _: rootNode };
  if (rootNode && rootNode.h && rootNode.h.length) {
    newRoot['_'] = Object.assign({}, rootNode, { h: mirrorShapes(rootNode.h) });
    shapesUpdated += rootNode.h.length;
  }

  // Non-root nodes: collect then sort shallowest-first.
  // Shallow-first guarantees every parent's new key is computed before its children.
  // key.length / 2 == depth because encodeDbKey is strictly 1-to-1.
  const nodes = [];
  for (const key in root) {
    if (key === '_') continue;
    const node = root[key];
    if (!node || !node.u) {
      warnings++;
      continue;
    }
    if (!node.f) {
      warnings++;
      continue;
    } // FEN required for capture detection
    nodes.push({ key, node, depth: key.length / 2 });
  }
  nodes.sort((a, b) => a.depth - b.depth);

  const oldToNew = {}; // old encoded key → new encoded key   (for child key construction)
  const oldToNewRaw = {}; // old encoded key → new raw path string (for relay/serverEval values)
  let changed = false;

  for (const { key, node, depth } of nodes) {
    const oldParentKey = depth === 1 ? '_' : key.slice(0, -2);

    // Parent FEN is always available from the ORIGINAL root (old keys still valid here).
    const parentFen = root[oldParentKey] && root[oldParentKey].f;

    let newU, newS;
    if (parentFen) {
      ({ newU, newS } = computeNewUciSan(node.u, parentFen, node.f));
    } else {
      // Defensive fallback: no parent FEN, mirror only (no Nacre→PS correction).
      print('WARNING: no parent FEN for key=' + key + ' in chapter ' + chapter._id);
      warnings++;
      newU = mirrorUci(node.u);
      newS = newU;
    }

    const rawPair = uciToPairChars(newU);
    const encodedPair = encodeDbKey(rawPair);

    let newKey, newRawPath;
    if (depth === 1) {
      newKey = encodedPair;
      newRawPath = rawPair;
    } else {
      const newParentKey = oldToNew[oldParentKey];
      if (newParentKey === undefined) {
        print('WARNING: parent key missing for key=' + key + ' in chapter ' + chapter._id);
        warnings++;
        // Keep under old key to avoid orphaning the subtree; raw path unknown.
        oldToNew[key] = key;
        oldToNewRaw[key] = '';
        newRoot[key] = node;
        continue;
      }
      newKey = newParentKey + encodedPair;
      newRawPath = oldToNewRaw[oldParentKey] + rawPair;
    }

    const newNode = Object.assign({}, node, { u: newU, s: newS });
    if (node.h && node.h.length) {
      newNode.h = mirrorShapes(node.h);
      shapesUpdated += node.h.length;
    }

    oldToNew[key] = newKey;
    oldToNewRaw[key] = newRawPath;
    newRoot[newKey] = newNode;

    // Fix "o" (child ordering) on the parent node: it stores raw UciCharPair step strings,
    // which must be updated when the encoded step changes.
    const oldRawPair = uciToPairChars(node.u);
    if (oldRawPair !== rawPair) {
      const parentInNewRoot = newRoot[depth === 1 ? '_' : oldToNew[oldParentKey]];
      if (parentInNewRoot && Array.isArray(parentInNewRoot.o)) {
        parentInNewRoot.o = parentInNewRoot.o.map(s => (s === oldRawPair ? rawPair : s));
      }
    }

    changed = true;
    nodesUpdated++;
  }

  // relay.path and serverEval.path are raw Path.toString strings (no DB key escaping).
  // Encode them to look up in oldToNewRaw, then write back the new raw path.
  const update = { root: newRoot };
  const relay = chapter.relay;
  if (relay && relay.path) {
    const newRawRelayPath = oldToNewRaw[encodeDbKey(relay.path)];
    if (newRawRelayPath !== undefined && newRawRelayPath !== relay.path) {
      update['relay.path'] = newRawRelayPath;
      changed = true;
    }
  }
  const serverEval = chapter.serverEval;
  if (serverEval && serverEval.path) {
    const newRawEvalPath = oldToNewRaw[encodeDbKey(serverEval.path)];
    if (newRawEvalPath !== undefined && newRawEvalPath !== serverEval.path) {
      update['serverEval.path'] = newRawEvalPath;
      changed = true;
    }
  }

  if (changed) {
    db.study_chapter_flat.updateOne({ _id: chapter._id }, { $set: update });
    chaptersUpdated++;
  }
});

print('Chapters updated: ' + chaptersUpdated);
print('Nodes updated:    ' + nodesUpdated);
print('Shapes updated:   ' + shapesUpdated);
print('Warnings:         ' + warnings);
