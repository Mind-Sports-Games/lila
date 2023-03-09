import { h, VNode } from 'snabbdom';
import changeColorHandle from 'common/coordsColor';
import * as xhr from 'common/xhr';

import { Redraw, Open, bind, header, displayGameFamily, convertVariantKeyToGameFamily } from './util';

type Theme = {
  name: string;
  gameFamily: string;
};

interface ThemeDimData {
  current: Theme;
  list: Theme[];
}

export interface ThemeData {
  d2: ThemeDimData[];
  d3: ThemeDimData[];
}

export interface ThemeCtrl {
  dimension: () => keyof ThemeData;
  data: () => ThemeDimData[];
  trans: Trans;
  set(t: Theme): void;
  open: Open;
}

export function ctrl(
  data: ThemeData,
  trans: Trans,
  dimension: () => keyof ThemeData,
  redraw: Redraw,
  open: Open
): ThemeCtrl {
  function dimensionData() {
    return data[dimension()];
  }

  return {
    dimension,
    trans,
    data: dimensionData,
    set(t: Theme) {
      const d = dimensionData();
      const dgf = d.filter(b => b.current.gameFamily === t.gameFamily)[0];
      dgf.current = t;
      applyTheme(t, dgf.list);
      xhr
        .text('/pref/theme' + (dimension() === 'd3' ? '3d' : '') + `/${t.gameFamily}`, {
          body: xhr.form({ theme: t.name }),
          method: 'post',
        })
        .catch(() => playstrategy.announce({ msg: 'Failed to save theme preference' }));
      redraw();
    },
    open,
  };
}

export function view(ctrl: ThemeCtrl): VNode {
  const d = ctrl.data();
  const startingDefaultGameFamily = playstrategy.pageVariant
    ? convertVariantKeyToGameFamily(playstrategy.pageVariant)
    : 'chess';
  const selectedGameFamily = document.getElementById('gameFamilyForTheme') as HTMLInputElement;
  const sv = selectedGameFamily ? selectedGameFamily.value : startingDefaultGameFamily;
  const dgf = d.filter(p => p.current.gameFamily === sv)[0];

  const allThemes = d.map(x => x.list).reduce((a, v) => a.concat(v), []);
  const currentTheme = d.map(x => x.current);

  return h('div.sub.theme.' + ctrl.dimension(), [
    header(ctrl.trans.noarg('boardTheme'), () => ctrl.open('links')),
    h('label', { attrs: { for: 'gameFamilyForTheme' } }, 'Game Family: '),
    h(
      'select',
      { attrs: { id: 'gameFamilyForTheme' } },
      gameFamily.map(v => gameFamilyOption(v, sv))
    ),
    h('div.list', allThemes.map(themeView(currentTheme, dgf.list, ctrl.set))),
  ]);
}

const gameFamily: GameFamilyKey[] = [
  'chess',
  'draughts',
  'loa',
  'shogi',
  'xiangqi',
  'flipello',
  'amazons',
  'oware',
  'togyzkumalak',
];

function gameFamilyOption(v: GameFamilyKey, sv: string) {
  if (v === sv) {
    return h(
      'option',
      { attrs: { title: displayGameFamily(v), value: v, selected: 'selected' } },
      displayGameFamily(v)
    );
  } else {
    return h('option', { attrs: { title: displayGameFamily(v), value: v } }, displayGameFamily(v));
  }
}

function isActiveTheme(t: Theme, current: Theme[]): boolean {
  //not sure why current.includes(t) doesn't work for the inital load of page and therefore doesn't highlight active theme
  let found = false;
  current.forEach(p => {
    if (p && p.name === t.name && p.gameFamily === t.gameFamily) {
      found = true;
    }
  });
  return found;
}

function themeView(current: Theme[], displayedThemes: Theme[], set: (t: Theme) => void) {
  return (t: Theme) =>
    h(
      `a.${t.gameFamily}`,
      {
        hook: bind('click', () => set(t)),
        attrs: { title: t.name },
        class: { active: isActiveTheme(t, current), hidden: !displayedThemes.includes(t) },
      },
      [h(`span.${t.gameFamily}-${t.name}`)]
    );
}

function applyTheme(t: Theme, list: Theme[]) {
  $('body')
    .removeClass(list.map(t => `${t.gameFamily}-${t.name}`).join(' '))
    .addClass(`${t.gameFamily}-${t.name}`);
  changeColorHandle();
}
