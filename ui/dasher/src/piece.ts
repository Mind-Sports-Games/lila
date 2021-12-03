import { h, VNode } from 'snabbdom';

import * as xhr from 'common/xhr';
import { Redraw, Open, bind, header } from './util';

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
        .text('/pref/pieceSet' + (dimension() === 'd3' ? '3d' : ''), {
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
  const selectedVariant = document.getElementById('variantForPiece') as HTMLInputElement;
  const sv = selectedVariant ? selectedVariant.value === 'LinesOfAction' ? 'loa' : selectedVariant.value.toLowerCase() : 'chess';
  const dgf = d.filter(p => p.current.gameFamily === sv)[0];
  // console.log('d ', d);
  // console.log('dgf ', dgf);

  return h('div.sub.piece.' + ctrl.dimension(), [
    header(ctrl.trans.noarg('pieceSet'), () => ctrl.open('links')),
    h('label', { attrs: { for: 'variantForPiece' } }, 'Variant: '),
    h(
      'select',
      { attrs: { id: 'variantForPiece' }, hook: bind('change', () => ctrl.redraw) },
      variants.map(v => variantOption(v))
    ),
    h(
      'div.list',
      dgf.list.map(pieceView(dgf.current, ctrl.set, ctrl.dimension() == 'd3'))
    ),
  ]);
}

const variants = ['Chess', 'LinesOfAction', 'Draughts', 'Shogi', 'Xiangqi'];

function variantOption(v: string) {
  return h('option', { attrs: { title: v } }, v);
}

function pieceImage(t: Piece, is3d: boolean) {
  if (is3d) {
    const preview = t.name == 'Staunton' ? '-Preview' : '';
    return `images/staunton/piece/${t.name}/White-Knight${preview}.png`;
  }
  return `piece/${t.gameFamily.toLowerCase()}/${t.name}/${t.displayPiece}.svg`; //Todo create a preview piece option in PieceSet
}

function pieceView(current: Piece, set: (t: Piece) => void, is3d: boolean) {
  return (t: Piece) =>
    h(
      'a.no-square',
      {
        attrs: { title: t.name },
        hook: bind('click', () => set(t)),
        class: { active: current === t },
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
