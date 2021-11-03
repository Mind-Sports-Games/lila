import * as domData from './data';
import * as cg from 'chessground/types';

export const init = (node: HTMLElement): void => {
  const [fen, orientation, lm] = node.getAttribute('data-state')!.split(',');
  initWith(node, fen, orientation as Color, lm);
};

export const initWith = (node: HTMLElement, fen: string, orientation: Color, lm?: string): void => {
  if (!window.Chessground || !window.Draughtsground) setTimeout(() => init(node), 500);
  else {
    const $el = $(node);
    $el.removeClass('mini-board--init');
    if ($el.hasClass('draughts')) {
      const [fen, board, orientation, lm] = $el.data('state').split('|');
      $el.data(
        'draughtsground',
        window.Draughtsground(node, {
          coordinates: 0,
          boardSize: board ? board.split('x').map((s: string) => parseInt(s)) : [10, 10],
          viewOnly: !node.getAttribute('data-playable'),
          resizable: false,
          fen,
          orientation,
          lastMove: lm && [lm.slice(-4, -2), lm.slice(-2)],
          drawable: {
            enabled: false,
            visible: false,
          },
        })
      );
    } else {
      domData.set(
        node,
        'chessground',
        window.Chessground(node, {
          orientation,
          coordinates: false,
          viewOnly: !node.getAttribute('data-playable'),
          resizable: false,
          fen,
          lastMove: lm && (lm[1] === '@' ? [lm.slice(2)] : [lm[0] + lm[1], lm[2] + lm[3]]),
          drawable: {
            enabled: false,
            visible: false,
          },
          dimensions: $el.hasClass('variant-shogi')
            ? { width: 9, height: 9 }
            : $el.hasClass('variant-xiangqi')
            ? { width: 9, height: 10 }
            : { width: 8, height: 8 },
          geometry: $el.hasClass('variant-shogi')
            ? cg.Geometry.dim9x9
            : $el.hasClass('variant-xiangqi')
            ? cg.Geometry.dim9x10
            : cg.Geometry.dim8x8,
          variant: $el.hasClass('variant-shogi') ? 'shogi' : $el.hasClass('variant-xiangqi') ? 'xiangqi' : 'standard',
        })
      );
    }
  }
};

export const initAll = (parent?: HTMLElement) =>
  Array.from((parent || document).getElementsByClassName('mini-board--init')).forEach(init);
