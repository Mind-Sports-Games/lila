import { Coords as CgCoords } from 'chessground/types';
import * as domData from 'common/data';
import { boardDimensions, variantFromElement } from 'common/mini-board';
import { loadChessground, loadDraughtsground } from 'common/board-lib';
import { displayScore, fenPlayerIndex, parseLastMove, backgammon as bgUtils } from 'stratutils';
import clockWidget from './clock-widget';

interface UpdateData {
  lm: string; // last move
  fen: string;
  p1?: number; // clock
  p1Pending?: number; // clock
  p1Delay?: number; // clock
  p2?: number;
  p2Pending?: number;
  p2Delay?: number;
}

// Returns data-live synchronously - initAll feeds those ids to startWatching - while
// the board library itself is fetched in the background. The marker class is dropped
// and the promise memoised before that await, so the redraws that call initAll on
// every websocket tick cannot build a second board on the same element.
export const init = (node: HTMLElement): string | null => {
  const live = node.getAttribute('data-live'),
    $el = $(node);
  $el.removeClass('mini-game--init');
  const wrap = $el.find('.cg-wrap')[0] as HTMLElement | undefined;
  if (!wrap || domData.get(wrap, 'board-pending')) return live;
  domData.set(
    wrap,
    'board-pending',
    ($el.hasClass('draughts') ? initDraughts($el, node, wrap) : initChess($el, node, wrap)).catch(e => {
      domData.set(wrap, 'board-pending', undefined); // the next initAll may retry this node
      throw e;
    }),
  );
  return live;
};

const initDraughts = ($el: Cash, node: HTMLElement, wrap: HTMLElement): Promise<any> => {
  const [fen, board, orientation, lm] = $el.data('state').split('|'),
    config = {
      coordinates: CgCoords.Hidden,
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
    };
  renderClocks($el, fen[0].toLowerCase() === 'w' ? 'p1' : 'p2');
  return loadDraughtsground().then(Draughtsground => {
    const dg = Draughtsground(wrap, config);
    domData.set(wrap, 'draughtsground', dg);
    return dg;
  });
};

const initChess = ($el: Cash, node: HTMLElement, wrap: HTMLElement): Promise<any> => {
  const [fen, orientation, lm, multiPointState] = node.getAttribute('data-state')!.split('|'),
    variant = variantFromElement($el) as VariantKey,
    config = {
      coordinates: CgCoords.Hidden,
      viewOnly: true,
      myPlayerIndex: orientation === 'p1vflip' ? 'p2' : orientation,
      turnPlayerIndex: fenPlayerIndex(variant, fen),
      resizable: false,
      fen,
      dice: bgUtils.readDice(fen, variant),
      doublingCube: bgUtils.readDoublingCube(fen, variant),
      showUndoButton: false,
      orientation,
      lastMove: lm && (lm[1] === '@' ? [lm.slice(2)] : parseLastMove(lm)),
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
    };
  renderClocks($el, fenPlayerIndex(variant, fen));
  return loadChessground().then(Chessground => {
    const cg = Chessground(wrap, config);
    domData.set(wrap, 'chessground', cg);
    return cg;
  });
};

const renderClocks = ($el: Cash, turnPlayerIndex: string): void =>
  ['p1', 'p2'].forEach(playerIndex =>
    $el.find('.mini-game__clock--' + playerIndex).each(function (this: HTMLElement) {
      clockWidget(this, {
        time: parseInt(this.getAttribute('data-time')!),
        delay: parseInt(this.getAttribute('data-time-delay')!),
        pending: parseInt(this.getAttribute('data-time-pending')!),
        pause: playerIndex != turnPlayerIndex,
      });
    }),
  );

export const initAll = (parent?: HTMLElement) => {
  const nodes = Array.from((parent || document).getElementsByClassName('mini-game--init')),
    ids = nodes.map(node => init(node as HTMLElement)).filter(id => id);
  if (ids.length) playstrategy.StrongSocket.firstConnect.then(send => send('startWatching', ids.join(' ')));
};

export const update = (node: HTMLElement, data: UpdateData) => {
  const wrap = node.querySelector('.cg-wrap') as HTMLElement | null;
  if (!wrap) return;
  const $el = $(node),
    variant = variantFromElement($el) as VariantKey,
    lm = data.lm,
    lastMove = lm && (lm[1] === '@' ? [lm.slice(2)] : parseLastMove(lm)),
    [, , , multiPointState] = node.getAttribute('data-state')!.split('|'),
    turnPlayerIndex = fenPlayerIndex(variant, data.fen);

  // Clocks and scores stay synchronous. They share a socket with finish(), which replaces
  // the clock nodes, so deferring them past it would write to a DOM that is no longer there.
  const renderClock = (
    time: number | undefined,
    delay: number | undefined,
    pending: number | undefined,
    playerIndex: string,
  ) => {
    const clock = $el[0]?.querySelector('.mini-game__clock--' + playerIndex) as HTMLElement | null;
    if (clock && !isNaN(time!))
      clockWidget(clock, {
        time: time || 0,
        delay: delay || 0,
        pending: pending || 0,
        pause: playerIndex != turnPlayerIndex,
      });
  };
  renderClock(data.p1, data.p1Delay, data.p1Pending, 'p1');
  renderClock(data.p2, data.p2Delay, data.p2Pending, 'p2');

  if (!isMultiPoint(multiPointState)) {
    ['p1', 'p2'].forEach(playerIndex => {
      const $score = $el.find('.mini-game__score--' + playerIndex);
      $score.html(displayScore(variant, data.fen, playerIndex));
    });
  }

  // Only the position waits for the board library, where the last fen to land wins anyway.
  const setPosition = (board: any, draughts: boolean) => {
    if (draughts) {
      board.set({ fen: data.fen, lastMove });
      return;
    }
    board.set({
      fen: data.fen,
      turnPlayerIndex,
      dice: bgUtils.readDice(data.fen, variant),
      doublingCube: bgUtils.readDoublingCube(data.fen, variant),
      lastMove,
      ...(multiPointState?.length === 6 && {
        multiPointState: {
          target: parseInt(multiPointState.substring(0, 2)),
          p1: parseInt(multiPointState.substring(2, 4)),
          p2: parseInt(multiPointState.substring(4, 6)),
        },
      }),
    });
    //update dice as they are in wrap of cg
    if (['backgammon', 'nackgammon', 'hyper'].includes(variant)) board.redrawAll();
  };
  const cg = domData.get(wrap, 'chessground'),
    dg = domData.get(wrap, 'draughtsground');
  if (cg) setPosition(cg, false);
  else if (dg) setPosition(dg, true);
  else {
    const pending = domData.get(wrap, 'board-pending');
    // a failed fetch already reports itself once, rather than once per socket tick
    if (pending) pending.then((board: any) => board && setPosition(board, $el.hasClass('draughts'))).catch(() => {});
  }
};

export const finish = (node: HTMLElement, win?: string, p1Score?: string, p2Score?: string) =>
  ['p1', 'p2'].forEach(playerIndex => {
    const $clock = $(node).find('.mini-game__clock--' + playerIndex);
    const $score = $(node).find('.mini-game__score--' + playerIndex);
    const colorLetter = playerIndex === 'p1' ? 'w' : 'b';
    const score = playerIndex === 'p1' ? p1Score : p2Score;
    const scoreDisplay = score ? `(${score})` : '';
    if (!$clock.data('managed')) {
      // snabbdom
      $score.html(''); // keep around as css aligns the result/clock to the right
      $clock.replaceWith(
        `<span class="mini-game__result">${(win ? (win == colorLetter ? 1 : 0) : '½') + scoreDisplay}</span>`,
      );
    }
  });

const isMultiPoint = (multiPointState?: string) => multiPointState?.length === 6;
