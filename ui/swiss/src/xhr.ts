import throttle from 'common/throttle';
import { json } from 'common/xhr';
import SwissCtrl from './ctrl';
import { isOutcome } from './util';

// when the tournament no longer exists
const onFail = () => playstrategy.reload();

const join = (ctrl: SwissCtrl, password?: string) =>
  json(`/swiss/${ctrl.data.id}/join`, {
    method: 'post',
    body: JSON.stringify({
      password: password || '',
    }),
    headers: { 'Content-Type': 'application/json' },
  }).catch(onFail);

const withdraw = (ctrl: SwissCtrl) => json(`/swiss/${ctrl.data.id}/withdraw`, { method: 'post' }).catch(onFail);

const loadPage = (ctrl: SwissCtrl, p: number) =>
  json(`/swiss/${ctrl.data.id}/standing/${p}`).then(data => {
    ctrl.loadPage(data);
    ctrl.redraw();
  });

const loadPageOf = (ctrl: SwissCtrl, userId: string): Promise<any> => json(`/swiss/${ctrl.data.id}/page-of/${userId}`);

const reload = (ctrl: SwissCtrl) =>
  json(`/swiss/${ctrl.data.id}?page=${ctrl.focusOnMe ? '' : ctrl.page}&playerInfo=${ctrl.playerInfoId || ''}`).then(
    data => {
      ctrl.reload(data);
      ctrl.redraw();
    },
    onFail,
  );

const playerInfo = (ctrl: SwissCtrl, userId: string) =>
  json(`/swiss/${ctrl.data.id}/player/${userId}`).then(data => {
    ctrl.data.playerInfo = data;
    ctrl.redraw();
  }, onFail);

//TODO change to load in normal json structure rather than sting that gets stripped apart.
const readSheetMin = (str: string) =>
  str
    ? str.split('|').map(s =>
        isOutcome(s)
          ? s
          : {
              g: s.slice(0, 8),
              o: s[8] == 'o',
              w: s[8] == 'w' ? true : s[8] == 'l' ? false : undefined,
              gpr: s[9],
              x: s.length > 9 && s[10] == 'x',
              px: s.length > 10 && s.slice(10, 12) == 'px',
              ms:
                (s.length > 10 && s[10] == 's') ||
                (s.length > 11 && s[10] == 'x' && s[11] == 's') ||
                (s.length > 12 && s[11] == 'x' && s[12] == 's'),
              mp:
                s.length > 10 && s[10] == 's'
                  ? s.slice(11, 13)
                  : s.length > 11 && s[10] == 'x' && s[11] == 's'
                  ? s.slice(12, 14)
                  : s.length > 12 && s[11] == 'x' && s[12] == 's'
                  ? s.slice(13, 15)
                  : undefined,
            },
      )
    : [];

export default {
  join: throttle(1000, join),
  withdraw: throttle(1000, withdraw),
  loadPage: throttle(1000, loadPage),
  loadPageOf,
  reloadNow: reload,
  playerInfo,
  readSheetMin,
};
