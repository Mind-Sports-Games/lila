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
      timeMode: 1,
      time: pool.lim,
      increment: pool.inc,
      days: 1,
      playerIndex: 'random',
    }),
  });
