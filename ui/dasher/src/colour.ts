import { h, VNode } from 'snabbdom';
import { Redraw, Close, bind, header } from './util';
import * as xhr from 'common/xhr';
import throttle from 'common/throttle';

export interface ColourCtrl {
  list: Colour[];
  set(k: string): void;
  get(): string;
  trans: Trans;
  close: Close;
}

export interface ColourData {
  current: string;
}

interface Colour {
  key: string;
  name: string;
  title?: string;
}

export function ctrl(data: ColourData, trans: Trans, redraw: Redraw, close: Close): ColourCtrl {
  const list: Colour[] = [
    { key: 'white', name: 'white' },
    { key: 'black', name: trans.noarg('black') },
    { key: 'red', name: 'red' },
    { key: 'blue', name: 'blue' },
    { key: 'green', name: 'green' },
    { key: 'yellow', name: 'yellow' },
  ];

  const announceFail = () => playstrategy.announce({ msg: 'Failed to save colour preference' });

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
        .text('/pref/colour', {
          body: xhr.form({ colour: c }),
          method: 'post',
        })
        .then(reloadAllTheThings, announceFail);
      applyColour(data, list);
      redraw();
    }),
    close,
  };
}

export function view(ctrl: ColourCtrl): VNode {
  const cur = ctrl.get();

  return h('div.sub.colour', [
    header('Colour', ctrl.close),
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
          c.name
        );
      })
    ),
  ]);
}

function applyColour(data: ColourData, list: Colour[]) {
  const key = data.current;
  $('body')
    .removeClass(list.map(b => `main-color-${b.key}`).join(' '))
    .addClass(`main-color-${key}`);
  $('body').data('main-color', key);
}
