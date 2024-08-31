import { h } from 'snabbdom';
import * as round from '../round';
import { drag, crazyKeys, pieceRoles, pieceShogiRoles, pieceMiniShogiRoles, selectToDrop } from './crazyCtrl';
import * as cg from 'chessground/types';
import RoundController from '../ctrl';
import { onInsert } from '../util';
import { Position } from '../interfaces';

const eventNames1 = ['mousedown', 'touchmove'];
const eventNames2 = ['click'];

export default function pocket(ctrl: RoundController, playerIndex: PlayerIndex, position: Position) {
  const step = round.plyStep(ctrl.data, ctrl.ply);
  const variantKey = ctrl.data.game.variant.key;
  const dropRoles =
    variantKey == 'crazyhouse' ? pieceRoles : variantKey == 'minishogi' ? pieceMiniShogiRoles : pieceShogiRoles;
  if (!step.crazy || ctrl.data.onlyDropsVariant) return;
  if (['backgammon', 'nackgammon', 'amazons'].includes(ctrl.data.game.variant.key)) return;
  const droppedRole = ctrl.justDropped,
    dropMode = ctrl.chessground?.state.dropmode,
    dropPiece = ctrl.chessground?.state.dropmode.piece,
    preDropRole = ctrl.preDrop,
    pocket = step.crazy.pockets[playerIndex === 'p1' ? 0 : 1],
    usablePos = position === (ctrl.flip ? 'top' : 'bottom'),
    shogiPlayer = position === 'top' ? 'enemy' : 'ally',
    usable = usablePos && !ctrl.replaying() && ctrl.isPlaying(),
    activePlayerIndex = playerIndex === ctrl.data.player.playerIndex;
  const capturedPiece = ctrl.justCaptured;
  const captured =
    capturedPiece &&
    ((variantKey === 'shogi' || variantKey === 'minishogi') && capturedPiece['promoted']
      ? (capturedPiece.role.slice(1) as cg.Role)
      : capturedPiece['promoted']
        ? 'p-piece'
        : capturedPiece.role);
  return h(
    'div.pocket.is2d.pocket-' + position,
    {
      class: { usable },
      hook: onInsert(el => {
        eventNames1.forEach(name =>
          el.addEventListener(name, (e: cg.MouchEvent) => {
            if (position === (ctrl.flip ? 'top' : 'bottom') && crazyKeys.length == 0) drag(ctrl, e);
          }),
        );
        eventNames2.forEach(name =>
          el.addEventListener(name, (e: cg.MouchEvent) => {
            if (position === (ctrl.flip ? 'top' : 'bottom') && crazyKeys.length == 0) selectToDrop(ctrl, e);
          }),
        );
      }),
    },
    dropRoles.map(role => {
      let nb = pocket[role] || 0;
      const selectedSquare = dropMode?.active && dropPiece?.role == role && dropPiece?.playerIndex == playerIndex;
      if (activePlayerIndex) {
        if (droppedRole === role) nb--;
        if (captured === role) nb++;
      }
      return h(
        'div.pocket-c1',
        h(
          'div.pocket-c2',
          h('piece.' + role + '.' + playerIndex + '.' + shogiPlayer, {
            class: { premove: activePlayerIndex && preDropRole === role, 'selected-square': selectedSquare },
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
