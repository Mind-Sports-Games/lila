import * as domData from './data';
import { readDice, fenPlayerIndex } from 'stratutils';

export const init = (node: HTMLElement): void => {
  const [fen, orientation, lm] = node.getAttribute('data-state')!.split('|');
  initWith(node, fen, orientation as Orientation, lm);
};

export const initWith = (node: HTMLElement, fen: string, orientation: Orientation, lm?: string): void => {
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
      const [, myPlayerIndex] = $el.data('state').split('|');
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
          showUndoButton: false,
          lastMove: lm && (lm == 'pass' ? undefined : lm[1] === '@' ? [lm.slice(2)] : [lm[0] + lm[1], lm[2] + lm[3]]),
          highlight: {
            lastMove:
              lm != undefined &&
              lm! == 'pass' &&
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
                  : $el.hasClass('variant-flipello10')
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
        }),
      );
    }
  }
};

export const initAll = (parent?: HTMLElement) =>
  Array.from((parent || document).getElementsByClassName('mini-board--init')).forEach(init);

export const variantFromElement = (element: Cash): string => {
  return element.hasClass('variant-shogi')
    ? 'shogi'
    : element.hasClass('variant-xiangqi')
      ? 'xiangqi'
      : element.hasClass('variant-minishogi')
        ? 'minishogi'
        : element.hasClass('variant-minixiangqi')
          ? 'minixiangqi'
          : element.hasClass('variant-flipello')
            ? 'flipello'
            : element.hasClass('variant-flipello10')
              ? 'flipello10'
              : element.hasClass('variant-amazons')
                ? 'amazons'
                : element.hasClass('variant-oware')
                  ? 'oware'
                  : element.hasClass('variant-togyzkumalak')
                    ? 'togyzkumalak'
                    : element.hasClass('variant-bestemshe')
                      ? 'bestemshe'
                      : element.hasClass('variant-go9x9')
                        ? 'go9x9'
                        : element.hasClass('variant-go13x13')
                          ? 'go13x13'
                          : element.hasClass('variant-go19x19')
                            ? 'go19x19'
                            : element.hasClass('variant-backgammon')
                              ? 'backgammon'
                              : element.hasClass('variant-hyper')
                                ? 'hyper'
                                : element.hasClass('variant-nackgammon')
                                  ? 'nackgammon'
                                  : element.hasClass('variant-abalone')
                                    ? 'abalone'
                                    : element.hasClass('variant-threeCheck')
                                      ? 'threeCheck'
                                      : element.hasClass('variant-fiveCheck')
                                        ? 'fiveCheck'
                                        : 'standard';
};
