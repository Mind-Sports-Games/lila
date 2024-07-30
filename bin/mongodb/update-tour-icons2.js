db.tournament2.updateMany(
  { lib: 0, variant: 11, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 0, variant: 14, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 2, variant: 6, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 2, variant: 7, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
db.tournament2.updateMany(
  { lib: 3, variant: 1, 'spotlight.iconFont': { $exists: true } },
  { $set: { 'spotlight.iconFont': '' } },
);
