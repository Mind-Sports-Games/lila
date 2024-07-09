import { drag, selectToDrop } from './crazyCtrl';
import { h } from 'snabbdom';
import * as cg from 'chessground/types';
import { onInsert } from '../util';
import AnalyseCtrl from '../ctrl';

const eventNames1 = ['mousedown', 'touchmove'];
const eventNames2 = ['click'];
const pieceRoles: cg.Role[] = ['p-piece', 'n-piece', 'b-piece', 'r-piece', 'q-piece'];
const pieceShogiRoles: cg.Role[] = ['p-piece', 'l-piece', 'n-piece', 's-piece', 'g-piece', 'b-piece', 'r-piece'];
const pieceMiniShogiRoles: cg.Role[] = ['p-piece', 's-piece', 'g-piece', 'b-piece', 'r-piece'];

type Position = 'top' | 'bottom';

export default function (ctrl: AnalyseCtrl, playerIndex: PlayerIndex, position: Position) {
  if (!ctrl.node.crazy || ctrl.data.onlyDropsVariant) return;
  if (['backgammon', 'nackgammon'].includes(ctrl.data.game.variant.key)) return;
  const pocket = ctrl.node.crazy.pockets[playerIndex === 'p1' ? 0 : 1];
  const dropped = ctrl.justDropped;
  const dropMode = ctrl.chessground?.state.dropmode;
  const dropPiece = ctrl.chessground?.state.dropmode.piece;
  const capturedPiece = ctrl.justCaptured;
  const shogiPlayer = position === 'top' ? 'enemy' : 'ally';
  const variantKey = ctrl.data.game.variant.key;
  const oKeys =
    variantKey == 'crazyhouse' ? pieceRoles : variantKey == 'minishogi' ? pieceMiniShogiRoles : pieceShogiRoles;
  const captured =
    capturedPiece &&
    ((variantKey === 'shogi' || variantKey === 'minishogi') && capturedPiece['promoted']
      ? (capturedPiece.role.slice(1) as cg.Role)
      : capturedPiece['promoted']
      ? 'p-piece'
      : capturedPiece.role);
  const activePlayerIndex = playerIndex === ctrl.turnPlayerIndex();
  const usable = !ctrl.embed && activePlayerIndex;
  return h(
    `div.pocket.is2d.pocket-${position}.pos-${ctrl.bottomPlayerIndex()}`,
    {
      class: { usable },
      hook: onInsert(el => {
        if (ctrl.embed) return;
        eventNames1.forEach(name => {
          el.addEventListener(name, e => drag(ctrl, playerIndex, e as cg.MouchEvent));
        });
        eventNames2.forEach(name => {
          el.addEventListener(name, (e: cg.MouchEvent) => selectToDrop(ctrl, playerIndex, e));
        });
      }),
    },
    oKeys.map(role => {
      let nb = pocket[role] || 0;
      const selectedSquare = dropMode?.active && dropPiece?.role == role && dropPiece?.playerIndex == playerIndex;
      if (activePlayerIndex) {
        if (dropped === role) nb--;
        if (captured === role) nb++;
      }
      return h(
        'div.pocket-c1',
        h(
          'div.pocket-c2',
          h('piece.' + role + '.' + playerIndex + '.' + shogiPlayer, {
            class: { 'selected-square': selectedSquare },
            attrs: {
              'data-role': role,
              'data-playerindex': playerIndex,
              'data-nb': nb,
            },
          }),
        ),
      );
    }),
  );
}
