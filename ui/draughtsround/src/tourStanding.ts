import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode'
import { onInsert } from './util'
import { ChatPlugin } from 'chat'
import { Team, TourPlayer } from 'game';

export interface TourStandingCtrl extends ChatPlugin {
  set(players: TourPlayer[]): void;
}

export function tourStandingCtrl(players: TourPlayer[], team: Team | undefined, name: string): TourStandingCtrl {
  return {
    set(d: TourPlayer[]) { players = d },
    tab: {
      key: 'tourStanding',
      name: name
    },
    view(): VNode {
      return h('div', {
        hook: onInsert(_ => {
          window.lidraughts.loadCssPath('round.tour-standing');
        })
      }, [
        team ? h('h3.text', {
          attrs: { 'data-icon': 'f' }
        }, team.name) : null,
        h('table.slist', [
          h('tbody', players.map((p: TourPlayer, i: number) => {
            const title64 = p.t && p.t.endsWith('-64');
            return h('tr.' + p.n, [
              h('td.name', [
                h('span.rank', '' + (i + 1)),
                h('a.user-link.ulpt', 
                  { attrs: { href: `/@/${p.n}` } },
                  [
                    p.t ? h(
                      'em.title',
                      title64 ? { attrs: {'data-title64': true } } : (p.t == 'BOT' ? { attrs: {'data-bot': true } } : {}),
                      title64 ? p.t.slice(0, p.t.length - 3) : p.t
                    ) : null,
                    p.t ? ' ' + p.n : p.n
                  ])
              ]),
              h('td.total', p.f ? {
                class: { 'is-gold': true },
                attrs: { 'data-icon': 'Q' }
              } : {}, '' + p.s)
            ])
          }))
        ])
      ]);
    }
  };
}
