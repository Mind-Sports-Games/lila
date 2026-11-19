import { Attrs, h, Hooks, VNode } from 'snabbdom';
import { favMark, favourites } from './favourites';

export interface PickerGame {
  key: string;
  name: string;
  icon: string;
  off?: boolean;
}

export interface PickerGroup {
  key: string;
  name: string;
  icon: string;
  variantKeys: string[];
  games: PickerGame[];
}

export interface GamePicker {
  labels: { group: string; variant: string };
  groups: PickerGroup[];
}

export interface GridState {
  groups?: boolean;
  games?: boolean;
}

export interface GridOpts {
  picker: GamePicker;
  chosen: string;
  state: GridState;
  choose: (key: string) => void;
  redraw: () => void;
  disabled?: (key: string) => boolean;
  automatic?: string;
}

export function grid(o: GridOpts): VNode {
  const { picker, state } = o;
  if (o.automatic !== undefined)
    return h('div.game-grid.game-grid--off', [row(picker.labels.variant, { name: o.automatic, icon: '' }, false, {})]);
  const favs = favourites();
  const isDisabled = (v: PickerGame) => !!v.off || !!o.disabled?.(v.key);
  const chosenGroup = picker.groups.find(g => g.games.some(v => v.key === o.chosen));
  const chosenGame = chosenGroup?.games.find(v => v.key === o.chosen);
  const toggle = (which: 'groups' | 'games') => bind('click', () => (state[which] = !state[which]), o.redraw);
  const pick = (key: string) =>
    bind('click', e => (e.currentTarget as HTMLElement).classList.contains('disabled') || o.choose(key), o.redraw);
  const pickGroup = (g: PickerGroup) =>
    bind(
      'click',
      e => {
        const tile = e.currentTarget as HTMLElement;
        const first = tile.dataset.first;
        if (!first) return;
        if (g.games.length > 1) {
          state.games = true;
          if (tile.classList.contains('open')) return;
        }
        o.choose(first);
      },
      o.redraw,
    );
  return h('div.game-grid', [
    row(
      picker.labels.group,
      chosenGroup,
      !!state.groups,
      chosenGroup ? favMark(chosenGroup.variantKeys, favs) : {},
      toggle('groups'),
    ),
    h('div.game-grid__panel', { class: { open: !!state.groups } }, [
      h(
        'div.game-grid__tiles',
        picker.groups.map(g =>
          tile(
            g,
            { open: g === chosenGroup, disabled: g.games.every(isDisabled), ...favMark(g.variantKeys, favs) },
            pickGroup(g),
            { 'data-first': g.games.find(v => !isDisabled(v))?.key || false },
          ),
        ),
      ),
    ]),
    row(
      picker.labels.variant,
      chosenGame,
      !!state.games,
      { fav: !!chosenGame && favs.has(chosenGame.key) },
      toggle('games'),
    ),
    h('div.game-grid__panel', { class: { open: !!state.games } }, [
      chosenGroup
        ? h(
            'div.game-grid__tiles.game-grid__games.open',
            chosenGroup.games.map(v =>
              tile(v, { chosen: v.key === o.chosen, disabled: isDisabled(v), fav: favs.has(v.key) }, pick(v.key)),
            ),
          )
        : null,
    ]),
  ]);
}

const row = (
  label: string,
  value: { name: string; icon: string } | undefined,
  open: boolean,
  fav: Record<string, boolean>,
  hook?: Hooks,
): VNode =>
  h('button.game-grid__row', { attrs: { type: 'button' }, class: { open }, hook }, [
    h('span.game-grid__label', label),
    value ? h('span.game-grid__value', { attrs: { 'data-icon': value.icon }, class: fav }, value.name) : null,
  ]);

const tile = (item: PickerGame, classes: Record<string, boolean>, hook: Hooks, attrs: Attrs = {}): VNode =>
  h(
    'button.game-grid__tile',
    { key: item.key, attrs: { type: 'button', 'data-icon': item.icon, ...attrs }, class: classes, hook },
    item.name,
  );

const bind = (eventName: string, f: (e: Event) => unknown, redraw?: () => void): Hooks => ({
  insert: vnode =>
    (vnode.elm as HTMLElement).addEventListener(eventName, e => {
      f(e);
      redraw?.();
    }),
});

export function bindStaticGrids(): void {
  document.addEventListener('click', e => {
    const el = e.target as HTMLElement;
    const grid = el.closest('.game-grid--static');
    if (!grid) return;
    const row = el.closest('.game-grid__row');
    if (row) return setOpen(row, !row.classList.contains('open'));
    const group = el.closest<HTMLElement>('button.game-grid__tile[data-group]');
    if (!group) return;
    grid.querySelectorAll('.game-grid__tile[data-group]').forEach(t => t.classList.toggle('open', t === group));
    grid
      .querySelectorAll<HTMLElement>('.game-grid__games')
      .forEach(g => g.classList.toggle('open', g.dataset.group === group.dataset.group));
    setOpen(grid.querySelector('.game-grid__row--game'), true);
  });
}

const setOpen = (row: Element | null, open: boolean) => {
  row?.classList.toggle('open', open);
  row?.nextElementSibling?.classList.toggle('open', open);
};
