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

function podiumTrophy(img: string): VNode {
  if (img) {
    return h(
      'div',
      h('img.customTrophy', {
        attrs: { src: playstrategy.assetUrl('images/trophy/' + img + '.png') },
      })
    );
  } else return h('div.trophy');
}

function podiumPosition(
  p: PodiumPlayer,
  pos: string,
  trophyImg: string,
  trans: Trans,
  isMM: boolean
): VNode | undefined {
  return p
    ? h(
        'div.' + pos,
        {
          class: {
            engine: !!p.engine,
          },
        },
        [
          podiumTrophy(trophyImg),
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
    podiumPosition(p[1], 'second', ctrl.data.trophy2nd, ctrl.trans, isMM),
    podiumPosition(p[0], 'first', ctrl.data.trophy1st, ctrl.trans, isMM),
    podiumPosition(p[2], 'third', ctrl.data.trophy3rd, ctrl.trans, isMM),
  ]);
}
