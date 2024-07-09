import { h } from 'snabbdom';
import * as ground from './ground';
import { bind, onInsert } from './util';
import * as util from 'chessground/util';
import { Role } from 'chessground/types';
import AnalyseCtrl from './ctrl';
import { MaybeVNode, JustCaptured } from './interfaces';
import { promotion } from 'stratutils';

interface Promoting {
  orig: Key;
  dest: Key;
  capture?: JustCaptured;
  callback: Callback;
}

type Callback = (orig: Key, dest: Key, capture: JustCaptured | undefined, role: Role) => void;

let promoting: Promoting | undefined;

export function start(
  ctrl: AnalyseCtrl,
  orig: Key,
  dest: Key,
  capture: JustCaptured | undefined,
  callback: Callback,
): boolean {
  const s = ctrl.chessground.state;
  const premovePiece = s.pieces.get(orig);
  const piece = s.pieces.get(dest);
  const variantKey = ctrl.data.game.variant.key;

  if (promotion.possiblePromotion(ctrl.chessground, orig, dest, variantKey)) {
    promoting = {
      orig,
      dest,
      capture,
      callback,
    };
    if (variantKey === 'shogi' && promotion.forcedShogiPromotion(ctrl.chessground, orig, dest)) {
      const role = premovePiece ? premovePiece.role : piece!.role;
      finish(ctrl, ('p' + role) as Role);
      return true;
    }
    if (variantKey === 'minishogi' && promotion.forcedMiniShogiPromotion(ctrl.chessground, orig, dest)) {
      const role = premovePiece ? premovePiece.role : piece!.role;
      finish(ctrl, ('p' + role) as Role);
      return true;
    }
    ctrl.redraw();
    return true;
  }
  return false;
}

function finish(ctrl: AnalyseCtrl, role: Role): void {
  if (promoting) {
    ground.promote(ctrl.chessground, promoting.dest, role);
    if (promoting.callback) promoting.callback(promoting.orig, promoting.dest, promoting.capture, role);
  }
  promoting = undefined;
}

export function cancel(ctrl: AnalyseCtrl): void {
  if (promoting) {
    promoting = undefined;
    ctrl.chessground.set(ctrl.cgConfig);
    ctrl.redraw();
  }
}

function renderPromotion(
  ctrl: AnalyseCtrl,
  dest: Key,
  roles: Role[],
  playerIndex: PlayerIndex,
  orientation: Orientation,
): MaybeVNode {
  if (!promoting) return;

  const rows = ctrl.chessground.state.dimensions.height;
  const columns = ctrl.chessground.state.dimensions.width;

  let left = (columns - util.key2pos(dest)[0]) * (100 / columns);
  if (orientation === 'p1') left = 100 - 100 / columns - left;
  const vertical = playerIndex === orientation ? 'top' : 'bottom';

  return h(
    'div#promotion-choice.' + vertical,
    {
      hook: onInsert(el => {
        el.addEventListener('click', _ => cancel(ctrl));
        el.oncontextmenu = () => false;
      }),
    },
    roles.map(function (serverRole: Role, i) {
      let top = 0;
      if (playerIndex === orientation) {
        if (playerIndex === 'p1') {
          top = (rows - util.key2pos(dest)[1] + i) * (100 / rows);
        } else {
          top = (util.key2pos(dest)[1] - 1 + i) * (100 / rows);
        }
      } else {
        if (playerIndex === 'p1') {
          top = (util.key2pos(dest)[1] - 1 - i) * (100 / rows);
        } else {
          top = (rows - util.key2pos(dest)[1] - i) * (100 / rows);
        }
      }
      return h(
        'square',
        {
          attrs: {
            style: `top:${top}%;left:${left}%`,
          },
          hook: bind('click', e => {
            e.stopPropagation();
            finish(ctrl, serverRole);
          }),
        },
        [h(`piece.${serverRole}.${playerIndex}.ally`)],
      );
    }),
  );
}

const roles: Role[] = ['q-piece', 'n-piece', 'r-piece', 'b-piece'];

export function view(ctrl: AnalyseCtrl): MaybeVNode {
  if (!promoting) return;

  const piece = ctrl.chessground.state.pieces.get(promoting.dest);
  if (!piece) return;
  const variantKey = ctrl.data.game.variant.key,
    rolesToChoose =
      variantKey === 'shogi' || variantKey === 'minishogi'
        ? (['p' + piece.role, piece.role] as Role[])
        : variantKey === 'antichess'
        ? roles.concat('k-piece')
        : roles;

  return renderPromotion(ctrl, promoting.dest, rolesToChoose, piece.playerIndex, ctrl.chessground.state.orientation);
}
