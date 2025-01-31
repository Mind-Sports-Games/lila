import * as domData from 'common/data';
import { variantFromElement } from 'common/mini-board';
import { readDice, readDoublingCube, displayScore, fenPlayerIndex } from 'stratutils';
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

export const init = (node: HTMLElement) => {
  if (!window.Chessground || !window.Draughtsground) setTimeout(() => init(node), 200);
  else {
    const $el = $(node);
    $el.removeClass('mini-game--init');
    if ($el.hasClass('draughts')) {
      const [fen, board, orientation, lm] = $el.data('state').split('|'),
        config = {
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
        },
        $cg = $el.find('.cg-wrap'),
        turnPlayerIndex = fen[0].toLowerCase() === 'w' ? 'p1' : 'p2';
      domData.set($cg[0] as HTMLElement, 'draughtsground', window.Draughtsground($cg[0], config));
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
    } else {
      const [fen, orientation, lm] = splitDataState(node),
        config = {
          coordinates: false,
          viewOnly: true,
          myPlayerIndex: orientation === 'p1vflip' ? 'p2' : orientation,
          turnPlayerIndex: fenPlayerIndex(variantFromElement($el) as VariantKey, fen),
          resizable: false,
          fen,
          dice: readDice(fen, variantFromElement($el) as VariantKey),
          doublingCube: readDoublingCube(fen, variantFromElement($el) as VariantKey),
          showUndoButton: false,
          orientation,
          lastMove: lm && (lm[1] === '@' ? [lm.slice(2)] : [lm[0] + lm[1], lm[2] + lm[3]]),
          highlight: {
            lastMove:
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
        },
        $cg = $el.find('.cg-wrap'),
        turnPlayerIndex = fenPlayerIndex(variantFromElement($el) as VariantKey, fen);
      domData.set($cg[0] as HTMLElement, 'chessground', window.Chessground($cg[0], config));
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
    }
  }
  return node.getAttribute('data-live');
};

export const initAll = (parent?: HTMLElement) => {
  const nodes = Array.from((parent || document).getElementsByClassName('mini-game--init')),
    ids = nodes.map(init).filter(id => id);
  if (ids.length) playstrategy.StrongSocket.firstConnect.then(send => send('startWatching', ids.join(' ')));
};

export const update = (node: HTMLElement, data: UpdateData) => {
  const $el = $(node),
    lm = data.lm,
    lastMove = lm && (lm[1] === '@' ? [lm.slice(2)] : [lm[0] + lm[1], lm[2] + lm[3]]),
    cg = domData.get(node.querySelector('.cg-wrap')!, 'chessground'),
    dg = domData.get(node.querySelector('.cg-wrap')!, 'draughtsground');
  if (cg)
    cg.set({
      fen: data.fen,
      turnPlayerIndex: fenPlayerIndex(variantFromElement($el) as VariantKey, data.fen),
      dice: readDice(data.fen, variantFromElement($el) as VariantKey),
      doublingCube: readDoublingCube(data.fen, variantFromElement($el) as VariantKey),
      lastMove,
    });
  if (['backgammon', 'nackgammon', 'hyper'].includes(variantFromElement($el))) cg.redrawAll(); //update dice as they are in wrap of cg
  if (dg)
    dg.set({
      fen: data.fen,
      lastMove,
    });
  const turnPlayerIndex = fenPlayerIndex(variantFromElement($el) as VariantKey, data.fen);
  const renderClock = (
    time: number | undefined,
    delay: number | undefined,
    pending: number | undefined,
    playerIndex: string,
  ) => {
    if (!isNaN(time!))
      clockWidget($el[0]?.querySelector('.mini-game__clock--' + playerIndex) as HTMLElement, {
        time: time || 0,
        delay: delay || 0,
        pending: pending || 0,
        pause: playerIndex != turnPlayerIndex,
      });
  };
  renderClock(data.p1, data.p1Delay, data.p1Pending, 'p1');
  renderClock(data.p2, data.p2Delay, data.p2Pending, 'p2');

  if (['backgammon', 'nackgammon', 'hyper'].includes(variantFromElement($el)) && isMultiPoint(node)) {
    ['p1', 'p2'].forEach(playerIndex => {
      const $score = $(node).find('.mini-game__score--' + playerIndex);
      const multiPointScore = getMultiPointScoreFromDataState(node);
      $score.html(
        displayScore(
          variantFromElement($el) as VariantKey,
          'f a k e ' + (+multiPointScore.substring(0, 2) + '') + ' ' + (+multiPointScore.substring(2, 4) + ''),
          playerIndex,
        ),
      );
    });
  } else {
    ['p1', 'p2'].forEach(playerIndex => {
      const $score = $(node).find('.mini-game__score--' + playerIndex);
      $score.html(displayScore(variantFromElement($el) as VariantKey, data.fen, playerIndex));
    });
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

const splitDataState = (node: HTMLElement): string[] => node.getAttribute('data-state')!.split('|');
const getMultiPointScoreFromDataState = (node: HTMLElement): string => splitDataState(node)[3];
const isMultiPoint = (node: HTMLElement) => getMultiPointScoreFromDataState(node) !== '-';
