import { h, VNode } from 'snabbdom';
import { spinner, bind, userName, dataIcon, player as renderPlayer, numberRow } from './util';
import { PairingExt, Outcome } from '../interfaces';
import { isOutcome } from '../util';
import SwissCtrl from '../ctrl';

interface MicroMatchOutcome {
  t: 'o';
  outcome: Outcome;
  round: number;
}

interface MicroMatchPairing extends PairingExt {
  t: 'p';
  round: number;
  isFinalGame: boolean;
}

const isMicroMatchOutcome = (p: MicroMatchPairing | MicroMatchOutcome): p is MicroMatchOutcome => p.t === 'o';

const microMatchGames = (sheet: (PairingExt | Outcome)[]): (MicroMatchPairing | MicroMatchOutcome)[] => {
  const newSheet: (MicroMatchPairing | MicroMatchOutcome)[] = [];
  let round = 1;
  sheet.forEach(v => {
    if (isOutcome(v)) {
      newSheet.push({ t: 'o', round, outcome: v });
    } else if (v.m && v.mmid) {
      newSheet.push({ t: 'p', round: round, isFinalGame: false, ...v });
      newSheet.push({ t: 'p', round: round, isFinalGame: true, ...v, g: v.mmid });
    } else {
      newSheet.push({ t: 'p', round: round, isFinalGame: true, ...v });
    }
    round += 1;
  });
  return newSheet;
};

export default function (ctrl: SwissCtrl): VNode | undefined {
  if (!ctrl.playerInfoId) return;
  const isMM = ctrl.data.isMicroMatch;
  const data = ctrl.data.playerInfo;
  const noarg = ctrl.trans.noarg;
  const tag = 'div.swiss__player-info.swiss__table';
  if (data?.user.id !== ctrl.playerInfoId) return h(tag, [h('div.stats', [h('h2', ctrl.playerInfoId), spinner()])]);
  const games = data.sheet.filter((p: any) => p.g).length;
  const wins = data.sheet.filter((p: any) => p.w).length;
  const avgOp: number | undefined = games
    ? Math.round(data.sheet.reduce((r, p) => r + ((p as any).rating || 1), 0) / games)
    : undefined;
  return h(
    tag,
    {
      hook: {
        insert: setup,
        postpatch(_, vnode) {
          setup(vnode);
        },
      },
    },
    [
      h('a.close', {
        attrs: dataIcon('L'),
        hook: bind('click', () => ctrl.showPlayerInfo(data), ctrl.redraw),
      }),
      h('div.stats', [
        h('h2', [h('span.rank', data.rank + '. '), renderPlayer(data, true, false)]),
        h('table', [
          numberRow('Points', isMM ? data.points * 2 : data.points, 'raw'),
          numberRow('Tie break', data.tieBreak, 'raw'),
          ...(games
            ? [
                data.performance
                  ? numberRow(noarg('performance'), data.performance + (games < 3 ? '?' : ''), 'raw')
                  : null,
                numberRow(noarg('winRate'), [wins, games], 'percent'),
                numberRow(noarg('averageOpponent'), avgOp, 'raw'),
              ]
            : []),
        ]),
      ]),
      h('div', [
        h(
          'table.pairings.sublist',
          {
            hook: bind('click', e => {
              const href = ((e.target as HTMLElement).parentNode as HTMLElement).getAttribute('data-href');
              if (href) window.open(href, '_blank', 'noopener');
            }),
          },
          microMatchGames(data.sheet).map(p => {
            const round = ctrl.data.round - p.round + 1;
            if (isMicroMatchOutcome(p))
              return h(
                'tr.' + p.outcome,
                {
                  key: round,
                },
                [
                  h('th', '' + round),
                  h('td.outcome', { attrs: { colspan: 3 } }, p.outcome),
                  h('td', p.outcome == 'absent' ? '-' : p.outcome == 'bye' ? '1' : '½'),
                ]
              );
            const res = result(p);
            return h(
              'tr.glpt.' + (res === '1' ? '.win' : res === '0' ? '.loss' : ''),
              {
                key: round,
                attrs: { 'data-href': '/' + p.g + (p.c ? '' : '/black') },
                hook: {
                  destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement),
                },
              },
              [
                h('th', p.isFinalGame ? '' + round : ''),
                h('td', userName(p.user)),
                h('td', '' + p.rating),
                h('td.is.sgPlayer-icon.' + (p.c ? 'white' : 'black')),
                h('td', p.isFinalGame ? res : ''),
              ]
            );
          })
        ),
      ]),
    ]
  );
}

function result(p: MicroMatchPairing): string {
  switch (p.w) {
    case true:
      return p.m ? '2' : '1';
    case false:
      return '0';
    default:
      return p.o ? '*' : p.m ? '1' : '½';
  }
}

function setup(vnode: VNode) {
  const el = vnode.elm as HTMLElement,
    p = playstrategy.powertip;
  p.manualUserIn(el);
  p.manualGameIn(el);
}
