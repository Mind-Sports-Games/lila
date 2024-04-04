import { h, VNode } from 'snabbdom';
import { Redraw, Close, bind, header } from './util';
import * as xhr from 'common/xhr';
import throttle from 'common/throttle';

export interface ColorCtrl {
  list: Color[];
  set(k: string): void;
  get(): string;
  trans: Trans;
  close: Close;
}

export interface ColorData {
  current: string;
}

interface Color {
  key: string;
  name: string;
  title?: string;
}

export function ctrl(data: ColorData, trans: Trans, redraw: Redraw, close: Close): ColorCtrl {
  const list: Color[] = [
    { key: 'original', name: 'original' },
    { key: 'black', name: trans.noarg('black') },
    { key: 'red', name: 'red' },
    { key: 'blue', name: 'blue' },
    { key: 'green', name: 'green' },
    { key: 'yellow', name: 'yellow' },
  ];

  const announceFail = () => playstrategy.announce({ msg: 'Failed to save color preference' });

  const reloadAllTheThings = () => {
    if (window.Highcharts) playstrategy.reload();
  };

  return {
    list,
    trans,
    get: () => data.current,
    set: throttle(700, (c: string) => {
      data.current = c;
      xhr
        .text('/pref/color', {
          body: xhr.form({ color: c }),
          method: 'post',
        })
        .then(reloadAllTheThings, announceFail);
      applyColor(data, list);
      redraw();
    }),
    close,
  };
}

export function view(ctrl: ColorCtrl): VNode {
  const cur = ctrl.get();

  return h('div.sub.color', [
    header(ctrl.trans.noarg('colorTheme'), ctrl.close),
    h(
      'div.selector.large',
      ctrl.list.map(c => {
        return h(
          'a.text',
          {
            class: { active: cur === c.key },
            attrs: { 'data-icon': 'E', title: c.title || '' },
            hook: bind('click', () => ctrl.set(c.key)),
          },
          h(`div.color-choice.${c.name}`)
        );
      })
    ),
  ]);
}

function applyColor(data: ColorData, list: Color[]) {
  const key = data.current;
  $('body')
    .removeClass(list.map(b => `selected-color-${b.key}`).join(' '))
    .addClass(`selected-color-${key}`);
  $('body').data('selected-color', key);
}
