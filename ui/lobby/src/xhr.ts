import * as xhr from 'common/xhr';
import debounce from 'debounce-promise';
import { Pool, Seek } from './interfaces';

export const seeks: () => Promise<Seek[]> = debounce(() => xhr.json('/lobby/seeks'), 2000);

export const nowPlaying = () => xhr.json('/account/now-playing').then(o => o.nowPlaying);

export const anonPoolSeek = (pool: Pool) =>
  xhr.json('/setup/hook/' + playstrategy.sri, {
    method: 'POST',
    body: xhr.form({
      variant: pool.variantId,
      timeMode: pool.byoyomi ? 3 : pool.delay ? 5 : 1,
      time: pool.lim,
      increment: pool.inc || pool.delay || 0,
      byoyomi: pool.byoyomi || 0,
      periods: pool.periods || 1,
      days: 1,
      playerIndex: 'random',
    }),
  });
