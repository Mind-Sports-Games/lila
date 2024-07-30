db.tournament2.updateMany(
  { lib: 0, variant: { $exists: false }, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 0, variant: 5, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 0, variant: 12, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 0, variant: 13, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 1, variant: 1, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 1, variant: 6, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 1, variant: 8, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 1, variant: 9, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 1, variant: 10, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 1, variant: 11, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 1, variant: 12, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 1, variant: 13, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 2, variant: 1, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 2, variant: 2, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 2, variant: 4, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 2, variant: 5, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
