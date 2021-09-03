import { h, VNode } from 'snabbdom';
import SwissCtrl from '../ctrl';
import { PodiumPlayer } from '../interfaces';
import { userName } from './util';

function podiumStats(p: PodiumPlayer, trans: Trans, isMM: boolean): VNode {
  const noarg = trans.noarg;
  return h('table.stats', [
    h('tr', [h('th', 'Points'), h('td', isMM ? '' + p.points * 2 : '' + p.points)]),
    h('tr', [h('th', 'Tie Break'), h('td', '' + p.tieBreak)]),
    p.performance ? h('tr', [h('th', noarg('performance')), h('td', '' + p.performance)]) : null,
  ]);
}

function podiumPosition(p: PodiumPlayer, pos: string, trans: Trans, isMM: boolean): VNode | undefined {
  return p
    ? h(
        'div.' + pos,
        {
          class: {
            engine: !!p.engine,
          },
        },
        [
          h('div.trophy'),
          h(
            'a.text.ulpt.user-link',
            {
              attrs: { href: '/@/' + p.user.name },
            },
            userName(p.user)
          ),
          podiumStats(p, trans, isMM),
        ]
      )
    : undefined;
}

export default function podium(ctrl: SwissCtrl) {
  const isMM = ctrl.data.isMicroMatch;
  const p = ctrl.data.podium || [];
  return h('div.podium', [
    podiumPosition(p[1], 'second', ctrl.trans, isMM),
    podiumPosition(p[0], 'first', ctrl.trans, isMM),
    podiumPosition(p[2], 'third', ctrl.trans, isMM),
  ]);
}
