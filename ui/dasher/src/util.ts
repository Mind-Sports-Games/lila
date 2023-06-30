import { h, VNode } from 'snabbdom';

export type Redraw = () => void;
export type Close = () => void;
export type Open = (sub: string) => void;

export interface Prop<T> {
  (): T;
  (v: T): T;
}

export function defined<A>(v: A | undefined): v is A {
  return typeof v !== 'undefined';
}

// like mithril prop but with type safety
export function prop<A>(initialValue: A): Prop<A> {
  let value = initialValue;
  const fun = function (v: A | undefined) {
    if (typeof v !== 'undefined') value = v;
    return value;
  };
  return fun as Prop<A>;
}

export function bind(eventName: string, f: (e: Event) => void, redraw: Redraw | undefined = undefined) {
  return {
    insert: (vnode: VNode) => {
      (vnode.elm as HTMLElement).addEventListener(eventName, e => {
        e.stopPropagation();
        f(e);
        if (redraw) redraw();
        return false;
      });
    },
  };
}

export function header(name: string, close: Close) {
  return h(
    'a.head.text',
    {
      attrs: { 'data-icon': 'I' },
      hook: bind('click', close),
    },
    name
  );
}

export function spinner() {
  return h('div.spinner', [
    h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
      h('circle', {
        attrs: { cx: 20, cy: 20, r: 18, fill: 'none' },
      }),
    ]),
  ]);
}

export function displayGameFamily(gameFamilyKey: GameFamilyKey): string {
  switch (gameFamilyKey) {
    case 'loa':
      return 'LinesOfAction';
    case 'flipello':
      return 'Othello';
    case 'togyzkumalak':
      return 'Togyzqumalaq';
    default:
      return gameFamilyKey.charAt(0).toUpperCase() + gameFamilyKey.slice(1);
  }
}

export function convertVariantKeyToGameFamily(variantKey: VariantKey | DraughtsVariantKey): string {
  switch (variantKey) {
    case 'linesOfAction':
    case 'scrambledEggs':
      return 'loa';
    case 'shogi':
    case 'minishogi':
      return 'shogi';
    case 'xiangqi':
    case 'minixiangqi':
      return 'xiangqi';
    case 'flipello':
    case 'flipello10':
      return 'flipello';
    case 'amazons':
      return 'amazons';
    case 'oware':
      return 'oware';
    case 'togyzkumalak':
      return 'togyzkumalak';
    case 'russian':
    case 'brazilian':
    case 'breakthrough':
    case 'frisian':
    case 'fromPositionDraughts':
    case 'frysk':
    case 'antidraughts':
    case 'pool':
    case 'portuguese':
    case 'english':
    case 'international':
      return 'draughts';
    default:
      return 'chess';
  }
}
