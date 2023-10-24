import * as domData from 'common/data';

interface UpdateData {
  lm: string;
  fen: string;
  p1?: number;
  p2?: number;
}

const fenPlayerIndex = (fen: string) => (fen.indexOf(' b') > 0 ? 'p2' : 'p1');

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
        turnPlayerIndex = fenPlayerIndex(fen);
      domData.set($cg[0] as HTMLElement, 'draughtsground', window.Draughtsground($cg[0], config));
      ['p1', 'p2'].forEach(playerIndex =>
        $el.find('.mini-game__clock--' + playerIndex).each(function (this: HTMLElement) {
          $(this).clock({
            time: parseInt(this.getAttribute('data-time')!),
            pause: playerIndex != turnPlayerIndex,
          });
        })
      );
    } else {
      const [fen, orientation, lm] = node.getAttribute('data-state')!.split('|'),
        config = {
          coordinates: false,
          viewOnly: true,
          myPlayerIndex: orientation,
          resizable: false,
          fen,
          orientation,
          lastMove: lm && (lm[1] === '@' ? [lm.slice(2)] : [lm[0] + lm[1], lm[2] + lm[3]]),
          drawable: {
            enabled: false,
            visible: false,
          },
          dimensions: $el.hasClass('variant-shogi')
            ? { width: 9, height: 9 }
            : $el.hasClass('variant-xiangqi')
            ? { width: 9, height: 10 }
            : $el.hasClass('variant-minishogi')
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
            : $el.hasClass('variant-go9x9')
            ? { width: 9, height: 9 }
            : $el.hasClass('variant-go13x13')
            ? { width: 13, height: 13 }
            : $el.hasClass('variant-go19x19')
            ? { width: 19, height: 19 }
            : { width: 8, height: 8 },
          variant: $el.hasClass('variant-shogi')
            ? 'shogi'
            : $el.hasClass('variant-xiangqi')
            ? 'xiangqi'
            : $el.hasClass('variant-minishogi')
            ? 'minishogi'
            : $el.hasClass('variant-minixiangqi')
            ? 'minixiangqi'
            : $el.hasClass('variant-flipello10')
            ? 'flipello10'
            : $el.hasClass('variant-amazons')
            ? 'amazons'
            : $el.hasClass('variant-oware')
            ? 'oware'
            : $el.hasClass('variant-togyzkumalak')
            ? 'togyzkumalak'
            : $el.hasClass('variant-go9x9')
            ? 'go9x9'
            : $el.hasClass('variant-go13x13')
            ? 'go13x13'
            : $el.hasClass('variant-go19x19')
            ? 'go19x19'
            : 'standard',
        },
        $cg = $el.find('.cg-wrap'),
        turnPlayerIndex = fenPlayerIndex(fen);
      domData.set($cg[0] as HTMLElement, 'chessground', window.Chessground($cg[0], config));
      ['p1', 'p2'].forEach(playerIndex =>
        $el.find('.mini-game__clock--' + playerIndex).each(function (this: HTMLElement) {
          $(this).clock({
            time: parseInt(this.getAttribute('data-time')!),
            pause: playerIndex != turnPlayerIndex,
          });
        })
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
      lastMove,
    });
  if (dg)
    dg.set({
      fen: data.fen,
      lastMove,
    });
  const turnPlayerIndex = fenPlayerIndex(data.fen);
  const renderClock = (time: number | undefined, playerIndex: string) => {
    if (!isNaN(time!))
      $el.find('.mini-game__clock--' + playerIndex).clock('set', {
        time,
        pause: playerIndex != turnPlayerIndex,
      });
  };
  renderClock(data.p1, 'p1');
  renderClock(data.p2, 'p2');
};

export const finish = (node: HTMLElement, win?: string) =>
  ['p1', 'p2'].forEach(playerIndex => {
    const $clock = $(node)
      .find('.mini-game__clock--' + playerIndex)
      .each(function (this: HTMLElement) {
        $(this).clock('destroy');
      });
    const colorLetter = playerIndex === 'p1' ? 'w' : 'b';
    if (!$clock.data('managed'))
      // snabbdom
      $clock.replaceWith(`<span class="mini-game__result">${win ? (win == colorLetter ? 1 : 0) : '½'}</span>`);
  });
