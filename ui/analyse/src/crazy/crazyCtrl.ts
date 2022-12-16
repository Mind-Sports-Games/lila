import { dragNewPiece } from 'chessground/drag';
import { readDrops, readDropsByRole } from 'stratutils';
import AnalyseCtrl from '../ctrl';
import * as cg from 'chessground/types';
import { Api as ChessgroundApi } from 'chessground/api';
import { AnalyseData } from '../interfaces';

export function drag(ctrl: AnalyseCtrl, playerIndex: PlayerIndex, e: cg.MouchEvent): void {
  if (e.button !== undefined && e.button !== 0) return; // only touch or left click
  if (ctrl.chessground.state.movable.playerIndex !== playerIndex) return;
  const el = e.target as HTMLElement;
  const role = el.getAttribute('data-role') as cg.Role,
    number = el.getAttribute('data-nb');
  if (!role || !playerIndex || number === '0') return;
  e.stopPropagation();
  e.preventDefault();
  dragNewPiece(ctrl.chessground.state, { playerIndex, role }, e);
}

export function valid(
  chessground: ChessgroundApi,
  data: AnalyseData,
  possibleDrops: string | undefined | null,
  possibleDropsByRole: string | undefined | null,
  piece: cg.Piece,
  pos: Key
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
