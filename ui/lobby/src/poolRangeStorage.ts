function makeKey(poolId) {
  return 'lobby-pool-range-' + poolId;
}

export function set(poolId, range) {
  const key = makeKey(poolId);
  if (range) playstrategy.storage.set(key, range);
  else playstrategy.storage.remove(key);
}

export function get(poolId) {
  return playstrategy.storage.get(makeKey(poolId));
}
