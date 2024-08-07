function makeKey(poolId: any) {
  return 'lobby-pool-range-' + poolId;
}

export function set(poolId: any, range: any) {
  const key = makeKey(poolId);
  if (range) playstrategy.storage.set(key, range);
  else playstrategy.storage.remove(key);
}

export function get(poolId: any) {
  return playstrategy.storage.get(makeKey(poolId));
}
