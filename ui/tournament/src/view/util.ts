import { Attrs, h, Hooks, VNode } from 'snabbdom';
import { numberFormat } from 'common/number';
import TournamentController from '../ctrl';

export function bind(eventName: string, f: (e: Event) => any, redraw?: () => void): Hooks {
  return onInsert(el =>
    el.addEventListener(eventName, e => {
      const res = f(e);
      if (redraw) redraw();
      return res;
    })
  );
}

export function onInsert(f: (element: HTMLElement) => void): Hooks {
  return {
    insert(vnode) {
      f(vnode.elm as HTMLElement);
    },
  };
}

export function dataIcon(icon: string): Attrs {
  return {
    'data-icon': icon,
  };
}

export function ratio2percent(r: number) {
  return Math.round(100 * r) + '%';
}

function playerNameWithDQ(p) {
  return p.disqualified ? h('span.dq', p.name) : p.name;
}

export function playerName(p) {
  return p.title ? [h('span.utitle', p.title), ' ' + playerNameWithDQ(p)] : playerNameWithDQ(p);
}

export function player(p, asLink: boolean, withRating: boolean, withFlag: boolean, defender = false, leader = false) {
  return h(
    'a.ulpt.user-link' + (((p.title || '') + p.name).length > 15 ? '.long' : ''),
    {
      attrs: asLink || 'ontouchstart' in window ? { href: '/@/' + p.name } : { 'data-href': '/@/' + p.name },
      hook: {
        destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement),
      },
    },
    [h('div.player-info', playerInfo(p, withRating, withFlag, defender, leader))]
  );
}

export function playerInfo(p, withRating: boolean, withFlag: boolean, defender = false, leader = false) {
  return [
    withFlag && p.country
      ? h(
          'span.country',
          h('img.flag', {
            attrs: {
              src: playstrategy.assetUrl('images/flags/' + p.country + '.png'),
            },
          })
        )
      : null,
    h(
      'span.name' + (defender ? '.defender' : leader ? '.leader' : ''),
      defender ? { attrs: dataIcon('5') } : leader ? { attrs: dataIcon('8') } : {},
      playerName(p)
    ),
    withRating ? h('span.rating', ' ' + p.rating + (p.provisional ? '?' : '')) : null,
  ];
}

export function numberRow(name: string, value: any, typ?: string) {
  return h('tr', [
    h('th', name),
    h(
      'td',
      typ === 'raw'
        ? value
        : typ === 'percent'
        ? value[1] > 0
          ? ratio2percent(value[0] / value[1])
          : 0
        : numberFormat(value)
    ),
  ]);
}

export function spinner(): VNode {
  return h('div.spinner', [
    h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
      h('circle', {
        attrs: { cx: 20, cy: 20, r: 18, fill: 'none' },
      }),
    ]),
  ]);
}

export function medleyVariantsHoriz(ctrl: TournamentController) {
  return h('div.medley-variants-horiz', [
    h(
      'div.medley-horiz-icon',
      {
        attrs: { 'data-icon': 5 },
        hook: bind('click', _ => ctrl.showMedleyVariants(!ctrl.showingMedleyVariants), ctrl.redraw),
      },
      ''
    ),
    h(
      'div.medley-variants-wide',
      h(
        'div.medley-variants-scrollable',
        medleyVariantListItems(
          ctrl.data.medleyVariants,
          ctrl.data.medleyRound,
          ctrl.data.isFinished,
          ctrl.data.medleyBalanceIntervals,
          ctrl.data.medleyIntervalSeconds
        )
      )
    ),
  ]);
}

export function medleyVariantListItems(
  variants: Variant[],
  medleyRound: number,
  displayCompleted: boolean,
  showIntervalTimes: boolean,
  IntervalSeconds: number[]
) {
  const variantsH = [] as (string | VNode)[];
  variants.forEach((v, index) => {
    displayCompleted || index >= medleyRound
      ? variantsH.push(
          h(
            'section.medley-variant__item.medley-round-' + index + (index == medleyRound ? '.current-variant' : ''),
            h(
              'h2',
              h(
                'a.medley-variant',
                {
                  attrs: {
                    href: '/variant/' + v.key,
                    'data-icon': typeof v.iconChar == 'undefined' ? '' : v.iconChar,
                  },
                },
                h(
                  'span.medley-variant-name',
                  v.name + (showIntervalTimes ? ` (${Math.floor(IntervalSeconds[index] / 60)})` : '')
                )
              )
            )
          )
        )
      : null;
  });
  return variantsH;
}

export function medleyVariantsList(ctrl: TournamentController, withClose: boolean) {
  return h('div.medley-variants.tour__actor-info', [
    withClose
      ? h('a.close', {
          attrs: dataIcon('L'),
          hook: bind('click', () => ctrl.showMedleyVariants(false), ctrl.redraw),
        })
      : null,
    h(
      'h1',
      ctrl.data.medleyBalanceIntervals
        ? 'Medley Variants: Balanced Intervals (minutes)'
        : ctrl.trans('medleyVariantsXMinutesEach', ctrl.data.medleyMinutes)
    ),
    h(
      'div.medley-variants-list',
      medleyVariantListItems(
        ctrl.data.medleyVariants,
        ctrl.data.medleyRound,
        true,
        ctrl.data.medleyBalanceIntervals,
        ctrl.data.medleyIntervalSeconds
      )
    ),
  ]);
}
