import * as domData from './data';
import { readDice, fenPlayerIndex, readDoublingCube, lastMove } from 'stratutils';

export const init = (node: HTMLElement): void => {
  const [fen, orientation, ,] = node.getAttribute('data-state')!.split('|');
  initWith(node, fen, orientation as Orientation);
};

export const initWith = (node: HTMLElement, fen: string, orientation: Orientation): void => {
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
        }),
      );
    } else {
      const [, myPlayerIndex, lm, multiPointState] = $el.data('state').split('|');
      domData.set(
        node,
        'chessground',
        window.Chessground(node, {
          orientation,
          coordinates: false,
          myPlayerIndex: myPlayerIndex,
          turnPlayerIndex: fenPlayerIndex(variantFromElement($el) as VariantKey, fen),
          viewOnly: !node.getAttribute('data-playable'),
          resizable: false,
          fen,
          dice: readDice(fen, variantFromElement($el) as VariantKey),
          doublingCube: readDoublingCube(fen, variantFromElement($el) as VariantKey),
          showUndoButton: false,
          lastMove: lastMove(
            ['flipello', 'flipello10', 'antiflipello', 'octagonflipello', 'go9x9', 'go13x13', 'go19x19'].includes(
              variantFromElement($el),
            ),
            lm,
          ),
          highlight: {
            lastMove:
              lm != undefined &&
              lm !== 'pass' &&
              variantFromElement($el) != 'backgammon' &&
              variantFromElement($el) != 'hyper' &&
              variantFromElement($el) != 'nackgammon',
          },
          drawable: {
            enabled: false,
            visible: false,
          },
          dimensions: $el.hasClass('variant-shogi')
            ? { width: 9, height: 9 }
            : $el.hasClass('variant-xiangqi')
              ? { width: 9, height: 10 }
              : $el.hasClass('variant-minishogi') || $el.hasClass('variant-minibreakthroughtroyka')
                ? { width: 5, height: 5 }
                : $el.hasClass('variant-minixiangqi')
                  ? { width: 7, height: 7 }
                  : $el.hasClass('variant-flipello10') || $el.hasClass('variant-octagonflipello')
                    ? { width: 10, height: 10 }
                    : $el.hasClass('variant-amazons')
                      ? { width: 10, height: 10 }
                      : $el.hasClass('variant-oware')
                        ? { width: 6, height: 2 }
                        : $el.hasClass('variant-togyzkumalak')
                          ? { width: 9, height: 2 }
                          : $el.hasClass('variant-bestemshe')
                            ? { width: 5, height: 2 }
                            : $el.hasClass('variant-go9x9')
                              ? { width: 9, height: 9 }
                              : $el.hasClass('variant-go13x13')
                                ? { width: 13, height: 13 }
                                : $el.hasClass('variant-go19x19')
                                  ? { width: 19, height: 19 }
                                  : $el.hasClass('variant-backgammon') ||
                                      $el.hasClass('variant-hyper') ||
                                      $el.hasClass('variant-nackgammon')
                                    ? { width: 12, height: 2 }
                                    : $el.hasClass('variant-abalone')
                                      ? { width: 9, height: 9 }
                                      : { width: 8, height: 8 },
          variant: variantFromElement($el),
          ...(multiPointState?.length === 6 && {
            multiPointState: {
              target: parseInt(multiPointState.substring(0, 2)),
              p1: parseInt(multiPointState.substring(2, 4)),
              p2: parseInt(multiPointState.substring(4, 6)),
            },
          }),
        }),
      );
    }
  }
};

export const initAll = (parent?: HTMLElement) =>
  Array.from((parent || document).getElementsByClassName('mini-board--init')).forEach(init);

// @TODO: rename into variantKeyFromElement
export const variantFromElement = (element: Cash): string => {
  const match = element.attr('class')?.match(/variant-([^\s]+)/);
  return match ? match[1] : 'standard';
};
