import { h, VNode } from 'snabbdom';
import { opposite } from 'chessground/util';
import { displayScore } from 'stratutils';
import { player as renderPlayer } from './util';
import { Board } from '../interfaces';

export function many(boards: Board[]): VNode {
  return h('div.swiss__boards.now-playing', boards.map(renderBoard));
}

export function top(boards: Board[]): VNode {
  return h('div.swiss__board__top.swiss__table', boards.slice(0, 1).map(renderBoard));
}

const renderBoardClasses = (board: Board): string =>
  board.gameLogic === 'draughts' && !!board.boardSize
    ? `.swiss__board.mini-game.mini-game-${board.id}.mini-game--init.is2d.${board.gameLogic}.is${board.boardSize.key}.${board.variantKey}.variant-${board.variantKey}`
    : `.swiss__board.mini-game.mini-game-${board.id}.mini-game--init.is2d.${board.gameFamily}.${board.variantKey}.variant-${board.variantKey}`;

const orientation = (p: PlayerIndex, gl: string): string => {
  switch (gl) {
    case 'backgammon':
      return p === 'p2' ? 'p1vflip' : 'p1';
    default:
      return p;
  }
};

const renderBoardState = (board: Board): string =>
  board.gameLogic === 'draughts' && !!board.boardSize
    ? `${board.fen}|${board.boardSize.size[0]}x${board.boardSize.size[1]}|${board.orientation}|${board.lastMove}`
    : `${board.fen}|${orientation(board.orientation, board.gameLogic)}|${board.lastMove}`;

const renderBoard = (incomingBoard: Board): VNode => {
  const board =
    (incomingBoard.isBestOfX || incomingBoard.isPlayX) && incomingBoard.multiMatchGames
      ? incomingBoard.multiMatchGames.slice(-1)[0]
      : incomingBoard;
  return h(
    `div${renderBoardClasses(board)}`,
    {
      key: board.id,
      attrs: {
        'data-state': renderBoardState(board),
        'data-live': board.id,
      },
      hook: {
        insert(vnode) {
          playstrategy.powertip.manualUserIn(vnode.elm as HTMLElement);
        },
      },
    },
    [
      boardPlayer(board, opposite(board.orientation)),
      h('a.cg-wrap', {
        attrs: {
          href: `/${board.id}/${board.orientation}`,
        },
      }),
      boardPlayer(board, board.orientation),
    ],
  );
};

function boardPlayer(board: Board, playerIndex: PlayerIndex) {
  const player = board[playerIndex];
  const score = displayScore(board.variantKey as VariantKey, board.fen, playerIndex);
  return h('span.mini-game__player', [
    h('span.mini-game__user.is.playerIndex-icon.text.' + (playerIndex == 'p1' ? board.p1Color : board.p2Color), [
      h('strong', '#' + player.rank),
      renderPlayer(player, true, true, false),
    ]),
    h(`span.mini-game__score--${playerIndex}`, board.clock ? score : ''),
    board.clock
      ? h(`span.mini-game__clock.mini-game__clock--${playerIndex}`, {
          attrs: {
            'data-time': board.clock[playerIndex],
            'data-managed': 1,
          },
        })
      : h('span.mini-game__result', (board.winner ? (board.winner == playerIndex ? 1 : 0) : '½') + score),
  ]);
}
