// The per-variant study listing (/study/variant/<key>/<order>) runs a distinct over chapters by variant.
// Builds online: mongo <db> bin/mongodb/260922-study-chapter-variant-index.js
db.study_chapter_flat.createIndex({ 'setup.variant.gl': 1, 'setup.variant.v': 1 });
