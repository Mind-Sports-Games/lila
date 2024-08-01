import { dragNewPiece } from 'chessground/drag';
import { readDrops, readDropsByRole } from 'stratutils';
import AnalyseCtrl from '../ctrl';
import * as cg from 'chessground/types';
import { Api as ChessgroundApi } from 'chessground/api';
import { setDropMode, cancelDropMode } from 'chessground/drop';
import { AnalyseData } from '../interfaces';

export function drag(ctrl: AnalyseCtrl, playerIndex: PlayerIndex, e: cg.MouchEvent): void {
  if (e.button !== undefined && e.button !== 0) return; // only touch or left click
  if (ctrl.chessground.state.movable.playerIndex !== playerIndex) return;
  const el = e.target as HTMLElement;
  const role = el.getAttribute('data-role') as cg.Role,
    number = el.getAttribute('data-nb');
  if (!role || !playerIndex || number === '0') return;
  const dropMode = ctrl.chessground?.state.dropmode;
  const dropPiece = ctrl.chessground?.state.dropmode.piece;
  if (!dropMode.active || dropPiece?.role !== role) {
    cancelDropMode(ctrl.chessground.state);
  }
  if (ctrl.chessground?.state.selected) {
    ctrl.cancelMove();
    ctrl.chessground.selectSquare(null);
    ctrl.redraw();
  }
  e.stopPropagation();
  e.preventDefault();
  dragNewPiece(ctrl.chessground.state, { playerIndex, role }, e);
}

export function selectToDrop(ctrl: AnalyseCtrl, playerIndex: PlayerIndex, e: cg.MouchEvent): void {
  if (e.button !== undefined && e.button !== 0) return; // only touch or left click
  if (ctrl.chessground.state.movable.playerIndex !== playerIndex) return;
  const el = e.target as HTMLElement,
    role = el.getAttribute('data-role') as cg.Role,
    //playerIndex = el.getAttribute('data-playerindex') as cg.PlayerIndex,
    number = el.getAttribute('data-nb');
  if (!role || !playerIndex || number === '0') return;
  const dropMode = ctrl.chessground?.state.dropmode;
  const dropPiece = ctrl.chessground?.state.dropmode.piece;
  if (!dropMode.active || dropPiece?.role !== role) {
    setDropMode(ctrl.chessground.state, { playerIndex, role });
  } else {
    cancelDropMode(ctrl.chessground.state);
  }
  e.stopPropagation();
  e.preventDefault();
  ctrl.redraw();
}

export function valid(
  chessground: ChessgroundApi,
  data: AnalyseData,
  possibleDrops: string | undefined | null,
  possibleDropsByRole: string | undefined | null,
  piece: cg.Piece,
  pos: Key,
): boolean {
  if (piece.playerIndex !== chessground.state.movable.playerIndex) return false;
  if (data.game.variant.key === 'crazyhouse') {
    if (piece.role === 'p-piece' && (pos[1] === '1' || pos[1] === '8')) return false;

    const drops = readDrops(possibleDrops);

    if (drops === null) return true;

    return drops.includes(pos);
  } else {
    //otherwise shogi and use the newer dropsByRole data
    const dropsByRole = readDropsByRole(possibleDropsByRole);
    return dropsByRole.get(piece.role)?.includes(pos) || false;
  }
}
