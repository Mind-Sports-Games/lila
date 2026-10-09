import * as domData from './data';
import { loadChessground, loadDraughtsground } from './board-lib';
import { fenPlayerIndex, parseLastMove, backgammon as bgUtils } from 'stratutils';

export const init = (node: HTMLElement): Promise<any> => {
  const [fen, orientation] = readState(node);
  return initWith(node, fen, orientation as Orientation);
};

// Resolves with the board api instance. Idempotent per node: the marker class is
// dropped and the promise memoised synchronously, before the library is awaited,
// so a redraw or a second initAll in that window cannot build a second board.
export const initWith = (node: HTMLElement, fen: string, orientation: Orientation): Promise<any> => {
  const pending = domData.get(node, 'board-pending');
  if (pending) return pending;
  const $el = $(node);
  $el.removeClass('mini-board--init');
  const promise = (
    $el.hasClass('draughts') ? initDraughts($el, node, fen, orientation) : initChess($el, node, fen, orientation)
  ).catch(e => {
    domData.set(node, 'board-pending', undefined); // the next initAll may retry this node
    throw e;
  });
  domData.set(node, 'board-pending', promise);
  return promise;
};

export const initAll = (parent?: HTMLElement): void => {
  Array.from((parent || document).getElementsByClassName('mini-board--init')).forEach(node =>
    init(node as HTMLElement),
  );
};

const initDraughts = (
  $el: Cash,
  node: HTMLElement,
  fallbackFen: string,
  fallbackOrientation: Orientation,
): Promise<any> => {
  const [stateFen, board, stateOrientation, lm] = readState(node);
  return loadDraughtsground().then(Draughtsground => {
    const dg = Draughtsground(node, {
      coordinates: 0,
      boardSize: board ? board.split('x').map((s: string) => parseInt(s)) : [10, 10],
      viewOnly: !node.getAttribute('data-playable'),
      resizable: false,
      fen: stateFen || fallbackFen,
      orientation: stateOrientation || fallbackOrientation,
      lastMove: lm && [lm.slice(-4, -2), lm.slice(-2)],
      drawable: {
        enabled: false,
        visible: false,
      },
    });
    $el.data('draughtsground', dg);
    domData.set(node, 'draughtsground', dg);
    return dg;
  });
};

const initChess = ($el: Cash, node: HTMLElement, fen: string, orientation: Orientation): Promise<any> => {
  const [, myPlayerIndex, lm, multiPointState] = readState(node);
  const variant = variantFromElement($el) as VariantKey;
  return loadChessground().then(Chessground => {
    const cg = Chessground(node, {
      orientation,
      coordinates: false,
      myPlayerIndex: myPlayerIndex,
      turnPlayerIndex: fenPlayerIndex(variant, fen),
      viewOnly: !node.getAttribute('data-playable'),
      resizable: false,
      fen,
      dice: bgUtils.readDice(fen, variant),
      doublingCube: bgUtils.readDoublingCube(fen, variant),
      showUndoButton: false,
      lastMove: parseLastMove(lm),
      highlight: {
        lastMove:
          lm != undefined && lm !== 'pass' && variant != 'backgammon' && variant != 'hyper' && variant != 'nackgammon',
      },
      drawable: {
        enabled: false,
        visible: false,
      },
      dimensions: boardDimensions($el),
      variant,
      ...(multiPointState?.length === 6 && {
        multiPointState: {
          target: parseInt(multiPointState.substring(0, 2)),
          p1: parseInt(multiPointState.substring(2, 4)),
          p2: parseInt(multiPointState.substring(4, 6)),
        },
      }),
    });
    domData.set(node, 'chessground', cg);
    return cg;
  });
};

// storm's end-of-run history boards carry no data-state - they pass fen and
// orientation to initWith instead.
const readState = (node: HTMLElement): string[] => (node.getAttribute('data-state') || '').split('|');

export const boardDimensions = ($el: Cash): { width: number; height: number } =>
  $el.hasClass('variant-shogi')
    ? { width: 9, height: 9 }
    : $el.hasClass('variant-xiangqi')
      ? { width: 9, height: 10 }
      : $el.hasClass('variant-minishogi') || $el.hasClass('variant-minibreakthroughtroyka')
        ? { width: 5, height: 5 }
        : $el.hasClass('variant-minixiangqi') || $el.hasClass('variant-entropy')
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
                            : $el.hasClass('variant-grandabalone')
                              ? { width: 11, height: 11 }
                              : $el.hasClass('variant-abalone')
                                ? { width: 9, height: 9 }
                                : { width: 8, height: 8 };

// @TODO: rename into variantKeyFromElement
export const variantFromElement = (element: Cash): string => {
  const match = element.attr('class')?.match(/variant-([^\s]+)/);
  return match ? match[1] : 'standard';
};
