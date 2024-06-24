import { h } from 'snabbdom';
import * as ground from './ground';
import * as cg from 'chessground/build/types';
import { DrawShape } from 'chessground/build/draw';
import * as xhr from './xhr';
import { key2pos } from 'chessground/build/util';
import { bind, onInsert } from './util';
import RoundController from './ctrl';
import { MaybeVNode } from './interfaces';
import { promotion } from 'stratutils';

interface Promoting {
  move: [cg.Key, cg.Key];
  pre: boolean;
  meta: cg.MoveMetadata;
}

let promoting: Promoting | undefined;
let prePromotionRole: cg.Role | undefined;

export function sendPromotion(
  ctrl: RoundController,
  orig: cg.Key,
  dest: cg.Key,
  role: cg.Role,
  meta: cg.MoveMetadata
): boolean {
  const piece = ctrl.chessground.state.pieces.get(dest);
  if (['shogi', 'minishogi'].includes(ctrl.data.game.variant.key) && piece && piece.role === role) {
    // shogi decision not to promote
    ctrl.sendMove(orig, dest, undefined, ctrl.data.game.variant.key, meta);
  } else {
    ground.promote(ctrl.chessground, dest, role);
    ctrl.sendMove(orig, dest, role, ctrl.data.game.variant.key, meta);
  }
  return true;
}

export function start(
  ctrl: RoundController,
  orig: cg.Key,
  dest: cg.Key,
  meta: cg.MoveMetadata = {} as cg.MoveMetadata
): boolean {
  const d = ctrl.data,
    premovePiece = ctrl.chessground.state.pieces.get(orig),
    piece = ctrl.chessground.state.pieces.get(dest),
    variantKey = ctrl.data.game.variant.key;
  if (promotion.possiblePromotion(ctrl.chessground, orig, dest, variantKey)) {
    if (variantKey === 'shogi' && promotion.forcedShogiPromotion(ctrl.chessground, orig, dest)) {
      const role = premovePiece ? premovePiece.role : piece!.role;
      return sendPromotion(ctrl, orig, dest, ('p' + role) as cg.Role, meta);
    }
    if (variantKey === 'minishogi' && promotion.forcedMiniShogiPromotion(ctrl.chessground, orig, dest)) {
      const role = premovePiece ? premovePiece.role : piece!.role;
      return sendPromotion(ctrl, orig, dest, ('p' + role) as cg.Role, meta);
    }
    if (prePromotionRole && meta && meta.premove) return sendPromotion(ctrl, orig, dest, prePromotionRole, meta);
    if (
      !meta.ctrlKey &&
      !promoting &&
      ((d.pref.autoQueen === Prefs.AutoQueen.Always && d.game.variant.lib == 0) ||
        (d.pref.autoQueen === Prefs.AutoQueen.OnPremove && premovePiece) ||
        ctrl.keyboardMove?.justSelected())
    ) {
      if (premovePiece) {
        if (variantKey === 'shogi' || variantKey === 'minishogi') {
          setPrePromotion(ctrl, dest, ('p' + premovePiece.role) as cg.Role);
        } else {
          setPrePromotion(ctrl, dest, 'q-piece');
        }
      } else sendPromotion(ctrl, orig, dest, 'q-piece', meta);
      return true;
    }
    promoting = {
      move: [orig, dest],
      pre: !!premovePiece,
      meta,
    };
    ctrl.redraw();
    return true;
  }
  return false;
}

function setPrePromotion(ctrl: RoundController, dest: cg.Key, role: cg.Role): void {
  prePromotionRole = role;
  ctrl.chessground.setAutoShapes([
    {
      orig: dest,
      piece: {
        playerIndex: ctrl.data.player.playerIndex,
        role,
        opacity: 0.8,
      },
      brush: '',
    } as DrawShape,
  ]);
}

export function cancelPrePromotion(ctrl: RoundController) {
  if (prePromotionRole) {
    ctrl.chessground.setAutoShapes([]);
    prePromotionRole = undefined;
    ctrl.redraw();
  }
}

function finish(ctrl: RoundController, role: cg.Role) {
  if (promoting) {
    const info = promoting;
    promoting = undefined;
    if (info.pre) setPrePromotion(ctrl, info.move[1], role);
    else sendPromotion(ctrl, info.move[0], info.move[1], role, info.meta);
    ctrl.redraw();
  }
}

export function cancel(ctrl: RoundController) {
  cancelPrePromotion(ctrl);
  ctrl.chessground.cancelPremove();
  if (promoting) xhr.reload(ctrl).then(ctrl.reload, playstrategy.reload);
  promoting = undefined;
}

function renderPromotion(
  ctrl: RoundController,
  dest: cg.Key,
  roles: cg.Role[],
  playerIndex: PlayerIndex,
  orientation: cg.Orientation
): MaybeVNode {
  const rows = ctrl.chessground.state.dimensions.height;
  const columns = ctrl.chessground.state.dimensions.width;
  let left = (columns - key2pos(dest)[0]) * (100 / columns);
  if (orientation === 'p1') left = 100 - 100 / columns - left;
  const vertical = playerIndex === orientation ? 'top' : 'bottom';

  return h(
    'div#promotion-choice.' + vertical,
    {
      hook: onInsert(el => {
        el.addEventListener('click', () => cancel(ctrl));
        el.addEventListener('contextmenu', e => {
          e.preventDefault();
          return false;
        });
      }),
    },
    roles.map((serverRole, i) => {
      let top = 0;
      if (playerIndex === orientation) {
        if (playerIndex === 'p1') {
          top = (rows - key2pos(dest)[1] + i) * (100 / rows);
        } else {
          top = (key2pos(dest)[1] - 1 + i) * (100 / rows);
        }
      } else {
        if (playerIndex === 'p1') {
          top = (key2pos(dest)[1] - 1 - i) * (100 / rows);
        } else {
          top = (rows - key2pos(dest)[1] - i) * (100 / rows);
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
        [h(`piece.${serverRole}.${playerIndex}.ally`)]
      );
    })
  );
}

const roles: cg.Role[] = ['q-piece', 'n-piece', 'r-piece', 'b-piece'];

export function view(ctrl: RoundController): MaybeVNode {
  if (!promoting) return;
  const piece = ctrl.chessground.state.pieces.get(promoting.move[1]),
    variantKey = ctrl.data.game.variant.key,
    rolesToChoose =
      variantKey === 'shogi' || variantKey === 'minishogi'
        ? (['p' + piece?.role, piece?.role] as cg.Role[])
        : variantKey === 'antichess'
        ? roles.concat('k-piece')
        : roles;
  return renderPromotion(
    ctrl,
    promoting.move[1],
    rolesToChoose,
    ctrl.data.player.playerIndex,
    ctrl.chessground.state.orientation
  );
}
