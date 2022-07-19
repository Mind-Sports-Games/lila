import { Attrs, h, Hooks, VNode } from 'snabbdom';
import { numberFormat } from 'common/number';

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

export function playerName(p) {
  return p.title ? [h('span.utitle', p.title), ' ' + p.name] : p.name;
}

export function player(p, asLink: boolean, withRating: boolean, defender = false, leader = false) {
  return h(
    'a.ulpt.user-link' + (((p.title || '') + p.name).length > 15 ? '.long' : ''),
    {
      attrs: asLink || 'ontouchstart' in window ? { href: '/@/' + p.name } : { 'data-href': '/@/' + p.name },
      hook: {
        destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement),
      },
    },
    [
      h(
        'span.name' + (defender ? '.defender' : leader ? '.leader' : ''),
        defender ? { attrs: dataIcon('5') } : leader ? { attrs: dataIcon('8') } : {},
        playerName(p)
      ),
      withRating ? h('span.rating', ' ' + p.rating + (p.provisional ? '?' : '')) : null,
    ]
  );
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

export function medleyVariantListItems(variants: Variant[], medleyRound: number) {
  const variantsH = [] as (string | VNode)[];
  variants.forEach((v, index) => {
    variantsH.push(
      h(
        'section.medley-variant__item',
        h(
          'h2' + (medleyRound == index ? '.current-variant' : ''),
          h(
            'a.medley-variant' + (medleyRound == index ? '.current-variant-link' : ''),
            {
              attrs: {
                href: '/variant/' + v.key,
                'data-icon': typeof v.iconChar == 'undefined' ? '' : v.iconChar,
              },
            },
            h('span.medley-variant-name', v.name)
          )
        )
      )
    );
  });
  return variantsH;
}

export function medleyVariantsList(variants: Variant[], medleyMinutes: number, medleyRound: number) {
  return h('div.medley-variants', [
    h('h2', 'Medley Variants'),
    h('h3', medleyMinutes.toString() + ' minutes for each variant'),
    h('div.medley-variants-list', medleyVariantListItems(variants, medleyRound)),
  ]);
}
