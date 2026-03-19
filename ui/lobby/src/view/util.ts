import { h, Hooks } from 'snabbdom';
import { MaybeVNodes } from '../interfaces';

export function bind(eventName: string, f: (e: Event) => any, redraw?: () => void): Hooks {
  return {
    insert(vnode) {
      (vnode.elm as HTMLElement).addEventListener(eventName, e => {
        const res = f(e);
        if (redraw) redraw();
        return res;
      });
    },
  };
}

export function tds(bits: MaybeVNodes): MaybeVNodes {
  return bits.map(function (bit) {
    return h('td', [bit]);
  });
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

// keys can have to match the translations in translation/source/variantName.xml or just a perf name depending on what file refers to that util from lobby
export const perfIcons: any = {
  Standard: '⁄',
  Blitz: ')',
  'Racing Kings': '',
  UltraBullet: '{',
  Bullet: 'T',
  Classical: '+',
  Rapid: '#',
  'Three-check': '',
  'Five-check': '',
  Antichess: '@',
  Horde: '_',
  Atomic: '>',
  Crazyhouse: '',
  Chess960: "'",
  Correspondence: ';',
  'King of the Hill': '(',
  Monster: '',
  'No Castling': '',
  'Lines Of Action': '',
  'Scrambled Eggs': '',
  International: '',
  Frisian: '',
  'Frysk!': '',
  Antidraughts: '',
  BRKTHRU: '',
  Russian: '',
  Brazilian: '',
  Pool: '',
  Spanish: '',
  'American/English': '‹',
  Shogi: '',
  Xiangqi: '',
  'Mini Shogi': '',
  'Mini Xiangqi': '',
  Othello: '',
  'Grand Othello': '',
  AntiOthello: '',
  OctagonOthello: '',
  Amazons: '€',
  Breakthrough: '',
  'Mini Breakthrough': '',
  Mancala: '',
  Oware: '',
  Togyzqumalaq: '›',
  'Go 9x9': '',
  'Go 13x13': '',
  'Go 19x19': '',
  Backgammon: '',
  Hyper: '',
  Nackgammon: '',
  Abalone: '\ue927',
  'Grand Abalone': '\ue92C',
};
