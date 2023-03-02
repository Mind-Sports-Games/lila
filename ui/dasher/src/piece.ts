import { h, VNode } from 'snabbdom';

import * as xhr from 'common/xhr';
import { Redraw, Open, bind, header, displayGameFamily, convertVariantKeyToGameFamily } from './util';

type Piece = {
  name: string;
  gameFamily: string;
  displayPiece: string;
};

interface PieceDimData {
  current: Piece;
  list: Piece[];
}

export interface PieceData {
  d2: PieceDimData[];
  d3: PieceDimData[];
}

export interface PieceCtrl {
  dimension: () => keyof PieceData;
  data: () => PieceDimData[];
  trans: Trans;
  set(t: Piece): void;
  open: Open;
  redraw: Redraw;
}

export function ctrl(
  data: PieceData,
  trans: Trans,
  dimension: () => keyof PieceData,
  redraw: Redraw,
  open: Open
): PieceCtrl {
  function dimensionData() {
    return data[dimension()];
  }

  return {
    dimension,
    trans,
    data: dimensionData,
    set(t: Piece) {
      const d = dimensionData();
      const dgf = d.filter(p => p.current.gameFamily === t.gameFamily)[0];
      dgf.current = t;
      applyPiece(t, dgf.list, dimension() === 'd3');
      xhr
        .text('/pref/pieceSet' + (dimension() === 'd3' ? '3d' : '') + `/${t.gameFamily}`, {
          body: xhr.form({ set: t.name }),
          method: 'post',
        })
        .catch(() => playstrategy.announce({ msg: 'Failed to save piece set  preference' }));
      redraw();
    },
    open,
    redraw,
  };
}

export function view(ctrl: PieceCtrl): VNode {
  const d = ctrl.data();
  const startingDefaultGameFamily = playstrategy.pageVariant
    ? convertVariantKeyToGameFamily(playstrategy.pageVariant)
    : 'chess';
  const selectedGameFamily = document.getElementById('gameFamilyForPiece') as HTMLInputElement;
  const sv = selectedGameFamily ? selectedGameFamily.value : startingDefaultGameFamily;
  const dgf = d.filter(p => p.current.gameFamily === sv)[0];

  return h('div.sub.piece.' + ctrl.dimension(), [
    header(ctrl.trans.noarg('pieceSet'), () => ctrl.open('links')),
    h('label', { attrs: { for: 'gameFamilyForPiece' } }, 'Game Family: '),
    h(
      'select',
      { attrs: { id: 'gameFamilyForPiece' } },
      gameFamily.map(v => gameFamilyOption(v, sv))
    ),
    pieceList(d, dgf, ctrl),
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

function pieceList(d: PieceDimData[], dgf: PieceDimData, ctrl: PieceCtrl): VNode {
  const allPieceSets = d.map(x => x.list).reduce((a, v) => a.concat(v), []);
  const currentPieceSets = d.map(x => x.current);
  return h(
    'div.list',
    { attrs: { id: 'pieceListDiv' } },
    allPieceSets.map(pieceView(currentPieceSets, dgf.list, ctrl.set, ctrl.dimension() == 'd3'))
  );
}

function pieceImage(t: Piece, is3d: boolean) {
  if (is3d) {
    const preview = t.name == 'Staunton' ? '-Preview' : '';
    return `images/staunton/piece/${t.name}/White-Knight${preview}.png`;
  }
  const ext = ['check_yb_loa', 'check_yb_flipello', 'marble_bw_flipello', 'classic_flipello'].includes(t.name)
    ? '.png'
    : '.svg';
  return `piece/${t.gameFamily.toLowerCase()}/${t.name}/${t.displayPiece}${ext}`;
}

function isActivePiece(t: Piece, current: Piece[]): boolean {
  //not sure why current.includes(t) doesn't work for the inital load of page and therefore doesn't highlight active pieces
  let found = false;
  current.forEach(p => {
    if (p && p.name === t.name && p.gameFamily === t.gameFamily && p.displayPiece === t.displayPiece) {
      found = true;
    }
  });
  return found;
}

function pieceView(current: Piece[], displayedPieces: Piece[], set: (t: Piece) => void, is3d: boolean) {
  return (t: Piece) =>
    h(
      `a.no-square ${t.gameFamily}`,
      {
        attrs: { title: t.name },
        hook: bind('click', () => set(t)),
        class: { active: isActivePiece(t, current), hidden: !displayedPieces.includes(t) },
      },
      [
        h('piece', {
          attrs: { style: `background-image:url(${playstrategy.assetUrl(pieceImage(t, is3d))})` },
        }),
      ]
    );
}

function applyPiece(t: Piece, list: Piece[], is3d: boolean) {
  if (is3d) {
    $('body').removeClass(list.join(' ')).addClass(t.name);
  } else {
    const sprite = document.getElementById('piece-sprite-' + t.gameFamily) as HTMLLinkElement;
    sprite.href = sprite.href.replace(/\w+\-\w+\.css/, t.gameFamily + '-' + t.name + '.css');
  }
}
