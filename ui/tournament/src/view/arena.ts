import { h, VNode } from 'snabbdom';
import TournamentController from '../ctrl';
import { player as renderPlayer, ratio2percent, bind, dataIcon, playerName } from './util';
import { teamName } from './battle';
import { MaybeVNodes } from '../interfaces';
import * as button from './button';
import * as pagination from '../pagination';

const scoreTagNames = ['score', 'streak', 'double'];

function scoreTag(s: any) {
  return h(scoreTagNames[(s[1] || 1) - 1], [Array.isArray(s) ? s[0] : s]);
}

function playerTr(ctrl: TournamentController, player: any) {
  const userId = player.name.toLowerCase(),
    nbScores = player.sheet.scores.length;
  const battle = ctrl.data.teamBattle;
  return h(
    'tr',
    {
      key: userId,
      class: {
        me: ctrl.opts.userId === userId,
        long: nbScores > 35,
        xlong: nbScores > 80,
        active: ctrl.playerInfo.id === userId,
        dq: player.disqualified,
      },
      hook: bind('click', _ => ctrl.showPlayerInfo(player), ctrl.redraw),
    },
    [
      h(
        'td.rank',
        player.withdraw
          ? h('i', {
              attrs: {
                'data-icon': 'Z',
                title: ctrl.trans.noarg('pause'),
              },
            })
          : player.rank,
      ),
      h('td.player', [
        renderPlayer(player, false, !ctrl.data.medley, true, userId === ctrl.data.defender),
        ...(battle && player.team ? [' ', teamName(battle, player.team)] : []),
      ]),
      h('td.sheet', player.sheet.scores.map(scoreTag)),
      h('td.total', [
        player.sheet.fire && !ctrl.data.isFinished
          ? h('strong.is-gold', { attrs: dataIcon('Q') }, player.sheet.total)
          : h('strong', player.sheet.total),
      ]),
    ],
  );
}

function podiumUsername(p: any) {
  return h(
    'a.text.ulpt.user-link',
    {
      attrs: { href: '/@/' + p.name },
    },
    playerName(p),
  );
}

function podiumStats(p, berserkable, isMedley, isHandicapped, trans: Trans): VNode {
  const noarg = trans.noarg,
    nb = p.nb;
  return h('table.stats', [
    p.performance && !isMedley && !isHandicapped
      ? h('tr', [h('th', noarg('performance')), h('td', p.performance)])
      : null,
    h('tr', [h('th', noarg('gamesPlayed')), h('td', nb.game)]),
    ...(nb.game
      ? [
          h('tr', [h('th', noarg('winRate')), h('td', ratio2percent(nb.win / nb.game))]),
          berserkable ? h('tr', [h('th', noarg('berserkRate')), h('td', ratio2percent(nb.berserk / nb.game))]) : null,
        ]
      : []),
  ]);
}

function podiumTrophy(img: string): VNode {
  if (img) {
    return h(
      'div',
      h('img.customTrophy', {
        attrs: { src: playstrategy.assetUrl('images/trophy/' + img + '.png') },
      }),
    );
  } else return h('div.trophy');
}

function podiumPosition(
  p: any,
  pos: string,
  trophyImg: string,
  berserkable: any,
  isMedley: boolean,
  isHandicapped: boolean,
  trans: Trans,
): VNode | undefined {
  if (p)
    return h('div.' + pos, [
      podiumTrophy(trophyImg),
      podiumUsername(p),
      podiumStats(p, berserkable, isMedley, isHandicapped, trans),
    ]);
  return undefined;
}

let lastBody: MaybeVNodes | undefined;

export function podium(ctrl: TournamentController) {
  const p = ctrl.data.podium || [];
  return h('div.podium', [
    podiumPosition(
      p[1],
      'second',
      ctrl.data.trophy2nd,
      ctrl.data.berserkable,
      ctrl.data.medley,
      ctrl.data.isHandicapped,
      ctrl.trans,
    ),
    podiumPosition(
      p[0],
      'first',
      ctrl.data.trophy1st,
      ctrl.data.berserkable,
      ctrl.data.medley,
      ctrl.data.isHandicapped,
      ctrl.trans,
    ),
    podiumPosition(
      p[2],
      'third',
      ctrl.data.trophy3rd,
      ctrl.data.berserkable,
      ctrl.data.medley,
      ctrl.data.isHandicapped,
      ctrl.trans,
    ),
  ]);
}

function preloadUserTips(el: HTMLElement) {
  playstrategy.powertip.manualUserIn(el);
}

export function controls(ctrl: TournamentController, pag: any): VNode {
  return h('div.tour__controls', [h('div.pager', pagination.renderPager(ctrl, pag)), button.joinWithdraw(ctrl)]);
}

export function standing(ctrl: TournamentController, pag: any, klass?: string): VNode {
  const tableBody = pag.currentPageResults ? pag.currentPageResults.map((res: any) => playerTr(ctrl, res)) : lastBody;
  if (pag.currentPageResults) lastBody = tableBody;
  return h(
    'table.slist.tour__standing' + (klass ? '.' + klass : ''),
    {
      class: { loading: !pag.currentPageResults },
    },
    [
      h(
        'tbody',
        {
          hook: {
            insert: vnode => preloadUserTips(vnode.elm as HTMLElement),
            update(_, vnode) {
              preloadUserTips(vnode.elm as HTMLElement);
            },
          },
        },
        tableBody,
      ),
    ],
  );
}
