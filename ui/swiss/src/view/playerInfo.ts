import { h, VNode } from 'snabbdom';
import { spinner, bind, userName, dataIcon, player as renderPlayer, numberRow, matchScoreDisplay } from './util';
import { PairingExt, Outcome } from '../interfaces';
import { isOutcome } from '../util';
import SwissCtrl from '../ctrl';

interface MultiMatchOutcome {
  t: 'o';
  outcome: Outcome;
  round: number;
}

interface MultiMatchPairing extends PairingExt {
  t: 'p';
  round: number;
  isFinalGame: boolean;
}

const isMultiMatchOutcome = (p: MultiMatchPairing | MultiMatchOutcome): p is MultiMatchOutcome => p.t === 'o';

const multiMatchGames = (sheet: (PairingExt | Outcome)[]): (MultiMatchPairing | MultiMatchOutcome)[] => {
  const newSheet: (MultiMatchPairing | MultiMatchOutcome)[] = [];
  let round = 1;
  sheet.forEach(v => {
    if (isOutcome(v)) {
      newSheet.push({ t: 'o', round, outcome: v });
    } else if (v.m && v.mmid) {
      newSheet.push({ t: 'p', round: round, isFinalGame: false, ...v });
      newSheet.push({ t: 'p', round: round, isFinalGame: true, ...v, g: v.mmid });
    } else if ((v.x || v.px) && v.mmids) {
      newSheet.push({ t: 'p', round: round, isFinalGame: false, ...v });
      v.mmids.forEach(gid =>
        newSheet.push({ t: 'p', round: round, isFinalGame: gid == v.mmids![v.mmids!.length - 1], ...v, g: gid })
      );
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
  const useMatchScore = ctrl.data.useMatchScore;
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
        h('h2', [h('span.rank', data.rank + '. '), renderPlayer(data, true, !ctrl.data.isMedley)]),
        h('table', [
          numberRow('Points', isMM && !useMatchScore ? data.points * 2 : data.points, 'raw'),
          numberRow('Tiebreak' + (data.tieBreak2 ? ' [BH]' : ' [SB]'), data.tieBreak, 'raw'),
          data.tieBreak2 ? numberRow('Tiebreak [SB]', data.tieBreak2, 'raw') : null,
          ...(games
            ? [
                data.performance && !ctrl.data.isMedley
                  ? numberRow(noarg('performance'), data.performance + (games < 3 ? '?' : ''), 'raw')
                  : null,
                numberRow(noarg('winRate'), [wins, games], 'percent'),
                !ctrl.data.isMedley ? numberRow(noarg('averageOpponent'), avgOp, 'raw') : null,
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
          multiMatchGames(data.sheet).map(p => {
            const round = ctrl.data.round - p.round + 1;
            if (isMultiMatchOutcome(p))
              return h(
                'tr.' + p.outcome,
                {
                  key: round,
                },
                [
                  h('th', '' + round),
                  h('td.outcome', { attrs: { colspan: 3 } }, p.outcome),
                  h('td', p.outcome == 'absent' ? '-' : p.outcome == 'bye' ? (useMatchScore && isMM ? '2' : '1') : '½'),
                ]
              );
            const res = result(p);
            return h(
              'tr.glpt.' + (p.o ? 'ongoing' : p.w === true ? 'win' : p.w === false ? 'loss' : 'draw'),
              {
                key: round,
                attrs: { 'data-href': '/' + p.g + (p.c ? '' : '/p2') },
                hook: {
                  destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement),
                },
              },
              [
                h('th', p.isFinalGame ? '' + round : ''),
                ctrl.data.isMedley && p.vi ? h('td', { attrs: { 'data-icon': p.vi } }, '') : null,
                h('td', userName(p.user)),
                h('td', ctrl.data.isMedley ? '' : '' + p.rating),
                h('td.is.playerIndex-icon.' + (p.c ? ctrl.data.p1Color : ctrl.data.p2Color)),
                h('td', p.isFinalGame ? res : ''),
              ]
            );
          })
        ),
      ]),
    ]
  );
}

function result(p: MultiMatchPairing): string {
  if (p.ms) {
    return matchScoreDisplay(p);
  }
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
