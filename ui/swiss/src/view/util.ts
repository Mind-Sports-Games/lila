import { Attrs, h, Hooks, VNode } from 'snabbdom';
import { BasePlayer } from '../interfaces';
import { numberFormat } from 'common/number';
import SwissCtrl from '../ctrl';

export function bind(eventName: string, f: (e: Event) => any, redraw?: () => void): Hooks {
  return onInsert(el =>
    el.addEventListener(eventName, e => {
      const res = f(e);
      if (redraw) redraw();
      return res;
    }),
  );
}

export function onInsert(f: (element: HTMLElement) => void): Hooks {
  return {
    insert(vnode: VNode) {
      f(vnode.elm as HTMLElement);
    },
  };
}

export function dataIcon(icon: string): Attrs {
  return {
    'data-icon': icon,
  };
}

export const userName = (u: LightUser) => (u.title ? [h('span.utitle', u.title), ' ' + u.name] : [u.name]);

export function player(p: BasePlayer, asLink: boolean, withRating: boolean, withFlag: boolean) {
  return h(
    'a.ulpt.user-link' + (((p.user.title || '') + p.user.name).length > 15 ? '.long' : ''),
    {
      attrs: asLink ? { href: '/@/' + p.user.name } : { 'data-href': '/@/' + p.user.name },
      hook: {
        destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement),
      },
    },
    [h('div.player-info', playerInfo(p, withRating, withFlag))],
  );
}

export function playerInfo(p: BasePlayer, withRating: boolean, withFlag: boolean) {
  return [
    withFlag && p.user.country
      ? h(
          'span.country',
          h('img.flag', {
            attrs: {
              src: playstrategy.assetUrl('images/flags/' + p.user.country + '.png'),
            },
          }),
        )
      : null,
    h(p.disqualified ? 'span.name.dq' : 'span.name', userName(p.user)),
    withRating
      ? h('span.rating' + (p.inputRating ? '.unused' : ''), ' ' + p.rating + (p.provisional ? '?' : ''))
      : null,
    withRating && p.inputRating ? h('span.rating.input', ' ' + p.inputRating.toString() + '*') : null,
  ];
}

export const ratio2percent = (r: number) => Math.round(100 * r) + '%';

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
        : numberFormat(value),
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

export function matchScoreDisplay(mp: string | undefined): string {
  const regex = /^[0-9]*$/g;
  if (mp && regex.test(mp)) {
    const score = parseInt(mp, 10);
    const isOdd = score % 2 == 1;
    const oddPart = isOdd ? '½' : '';
    const base = Math.floor(score / 2);
    if (score == 1) {
      return '½';
    } else {
      return `${base}${oddPart}`;
    }
  } else {
    return '?';
  }
}

export function multiMatchByeScore(ctrl: SwissCtrl): string {
  return (ctrl.data.isPlayX ? ctrl.data.nbGamesPerRound * 2 : ctrl.data.nbGamesPerRound + 2).toString();
}
