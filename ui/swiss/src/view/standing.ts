import { h, VNode } from 'snabbdom';
import SwissCtrl from '../ctrl';
import { player as renderPlayer, bind, onInsert } from './util';
import { MaybeVNodes, PairingBase, Player, Pager } from '../interfaces';

function playerTr(ctrl: SwissCtrl, player: Player) {
  const isMM = ctrl.data.isMicroMatch;
  const useMatchScore = ctrl.data.useMatchScore;
  console.log(isMM, useMatchScore);
  const userId = player.user.id;
  return h(
    'tr',
    {
      key: userId,
      class: {
        me: ctrl.data.me?.id == userId,
        active: ctrl.playerInfoId === userId,
      },
      hook: bind('click', _ => ctrl.showPlayerInfo(player), ctrl.redraw),
    },
    [
      h(
        'td.rank',
        player.absent && ctrl.data.status != 'finished'
          ? h('i', {
              attrs: {
                'data-icon': 'Z',
                title: 'Absent',
              },
            })
          : [player.rank]
      ),
      h('td.player', renderPlayer(player, false, !ctrl.data.isMedley)),
      h(
        'td.pairings',
        h(
          'div',
          player.sheet
            .map(p =>
              p == 'absent'
                ? h(p, title('Absent'), '-')
                : p == 'bye'
                ? h(p, title('Bye'), isMM ? '2' : '1')
                : p == 'late'
                ? h(p, title('Late'), isMM ? '1' : '½')
                : isMM
                ? h(
                    'span.glpt.' + (p.o ? 'ongoing' : p.w === true ? 'win' : p.w === false ? 'loss' : 'draw'),
                    { attrs: { key: p.g } },
                    result(p)
                  )
                : h(
                    'a.glpt.' + (p.o ? 'ongoing' : p.w === true ? 'win' : p.w === false ? 'loss' : 'draw'),
                    {
                      attrs: {
                        key: p.g,
                        href: `/${p.g}`,
                      },
                      hook: onInsert(playstrategy.powertip.manualGame),
                    },
                    result(p)
                  )
            )
            .concat([...Array(Math.max(0, ctrl.data.nbRounds - player.sheet.length))].map(_ => h('r')))
        )
      ),
      h('td.points', title('Points'), '' + (isMM ? player.points * 2 : player.points)),
      h('td.tieBreak', title('Tiebreak'), '' + player.tieBreak),
    ]
  );
}

const result = (p: PairingBase): string => {
  switch (p.w) {
    case true:
      return p.m ? '2' : '1';
    case false:
      return '0';
    default:
      return p.o ? '*' : p.m ? '1' : '½';
  }
};

const title = (str: string) => ({ attrs: { title: str } });

let lastBody: MaybeVNodes | undefined;

const preloadUserTips = (vn: VNode) => playstrategy.powertip.manualUserIn(vn.elm as HTMLElement);

export default function standing(ctrl: SwissCtrl, pag: Pager, klass?: string): VNode {
  const tableBody = pag.currentPageResults ? pag.currentPageResults.map(res => playerTr(ctrl, res)) : lastBody;
  if (pag.currentPageResults) lastBody = tableBody;
  return h(
    'table.slist.swiss__standing' + (klass ? '.' + klass : ''),
    {
      class: {
        loading: !pag.currentPageResults,
        long: ctrl.data.round > 10,
        xlong: ctrl.data.round > 20,
      },
    },
    [
      h(
        'tbody',
        {
          hook: {
            insert: preloadUserTips,
            update(_, vnode) {
              preloadUserTips(vnode);
            },
          },
        },
        tableBody
      ),
    ]
  );
}
