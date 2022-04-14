import { h, VNode } from 'snabbdom';
import { opposite } from 'chessground/util';
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

const renderBoardState = (board: Board): string =>
  board.gameLogic === 'draughts' && !!board.boardSize
    ? `${board.fen}|${board.boardSize.size[0]}x${board.boardSize.size[1]}|${board.orientation}|${board.lastMove}`
    : `${board.fen},${board.orientation},${board.lastMove}`;

const renderBoard = (board: Board): VNode =>
  h(
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
    ]
  );

function calculateScore(board: Board, playerIndex: PlayerIndex): String {  
  console.log("board ", board);
  var score = ""
  switch(board.variantKey) {
      case "flipello": {
        const boardFen = board.fen.split(" ")[0].split("[")[0];
        if (playerIndex == 'p1'){
          score = "(" + (boardFen.split('p').length - 1).toString() + ")"
        }else{
          score = "(" + (boardFen.split('P').length - 1).toString() + ")"
        }
        break;
      }
      case "threeCheck":
      case "fiveCheck": {
        var numChecks: number = 0
        if (playerIndex == 'p1'){
          numChecks = board.p1Checks ? board.p1Checks : 0
        }else{
          numChecks = board.p2Checks ? board.p2Checks : 0
        }      
        score = "(" + numChecks.toString() + ")"
        break;
      }
      default: {
       score = ""
       break;
      }
  }
  return score;
}

function boardPlayer(board: Board, playerIndex: PlayerIndex) {
  const player = board[playerIndex];
  return h('span.mini-game__player', [
    h('span.mini-game__user', [h('strong', '#' + player.rank), renderPlayer(player, true, true)]),
    board.clock
      ? h(`span.mini-game__clock.mini-game__clock--${playerIndex}`, {
          attrs: {
            'data-time': board.clock[playerIndex],
            'data-managed': 1,
          },
        })
      : h('span.mini-game__result', board.winner ? (board.winner == playerIndex ? 1 : 0) : '½'),
      h('span', calculateScore(board, playerIndex)),
  ]);
}
