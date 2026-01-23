import { h } from 'snabbdom';
import { bind, onInsert } from './util';
import { Api as CgApi } from 'chessground/api';
import { key2pos, opposite } from 'chessground/util';
import * as cg from 'chessground/types';
import { MaybeVNode, Vm, Redraw, Promotion } from './interfaces';
import { promotion } from 'stratutils';
import { Prop } from 'common';

export default function (vm: Vm, getGround: Prop<CgApi>, redraw: Redraw): Promotion {
  let promoting: false | { orig: Key; dest: Key; callback: (orig: Key, key: Key, prom: cg.Role) => void } = false;

  function sendPromotion(
    callback: (orig: Key, key: Key, prom: cg.Role) => void,
    orig: cg.Key,
    dest: cg.Key,
    role: cg.Role,
  ): boolean {
    const ground = getGround(),
      piece = ground.state.pieces.get(dest);
    if (['shogi', 'minishogi'].includes(vm.variant.key) && piece && piece.role === role) {
      // shogi decision not to promote
      callback(orig, dest, undefined);
    } else {
      promote(ground, dest, role);
      callback(orig, dest, role);
    }
    return true;
  }

  function start(orig: Key, dest: Key, callback: (orig: Key, key: Key, prom: cg.Role) => void) {
    const ground = getGround(),
      piece = ground.state.pieces.get(dest),
      variantKey = vm.variant.key;
    if (promotion.possiblePromotion(ground, orig, dest, variantKey)) {
      if (variantKey === 'shogi' && promotion.forcedShogiPromotion(ground, orig, dest)) {
        const role = piece!.role;
        return sendPromotion(callback, orig, dest, ('p' + role) as cg.Role);
      }
      if (variantKey === 'minishogi' && promotion.forcedMiniShogiPromotion(ground, orig, dest)) {
        const role = piece!.role;
        return sendPromotion(callback, orig, dest, ('p' + role) as cg.Role);
      }
      promoting = {
        orig,
        dest,
        callback,
      };
      redraw();
      return true;
    }
    return false;
  }

  function promote(g: CgApi, key: Key, role: cg.Role): void {
    const piece = g.state.pieces.get(key);
    if (
      (piece && piece.role === 'p-piece' && g.state.variant !== 'shogi' && g.state.variant !== 'minishogi') ||
      (piece &&
        (g.state.variant == 'shogi' || g.state.variant == 'minishogi') &&
        piece.role !== 'k-piece' &&
        piece.role !== 'g-piece')
    ) {
      g.setPieces(
        new Map([
          [
            key,
            {
              playerIndex: piece.playerIndex,
              role,
              promoted: true,
            },
          ],
        ]),
      );
    }
  }

  function finish(role: cg.Role): void {
    if (promoting) {
      promote(getGround(), promoting.dest, role);
      promoting.callback(promoting.orig, promoting.dest, role);
    }
    promoting = false;
  }

  function cancel(): void {
    if (promoting) {
      promoting = false;
      getGround().set(vm.cgConfig);
      redraw();
    }
  }

  function renderPromotion(
    dest: Key,
    roles: cg.Role[],
    playerIndex: PlayerIndex,
    orientation: Orientation,
  ): MaybeVNode {
    if (!promoting) return;
    const ground = getGround(),
      rows = ground.state.dimensions.height,
      columns = ground.state.dimensions.width;
    let left = (columns - key2pos(dest)[0]) * (100 / columns);
    if (orientation === 'p1') left = 100 - 100 / columns - left;
    const vertical = playerIndex === orientation ? 'top' : 'bottom';

    return h(
      'div#promotion-choice.' + vertical,
      {
        hook: onInsert(el => {
          el.addEventListener('click', cancel);
          el.addEventListener('contextmenu', e => {
            e.preventDefault();
            return false;
          });
        }),
      },
      roles.map(function (serverRole, i) {
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
              finish(serverRole);
            }),
          },
          [h(`piece.${serverRole}.${playerIndex}.ally`)],
        );
      }),
    );
  }

  const roles: cg.Role[] = ['q-piece', 'n-piece', 'r-piece', 'b-piece'];

  return {
    start,
    cancel,
    view() {
      if (!promoting) return;
      const piece = getGround().state.pieces.get(promoting.dest),
        variantKey = vm.variant.key,
        rolesToChoose =
          variantKey === 'shogi' || variantKey === 'minishogi'
            ? (['p' + piece?.role, piece?.role] as cg.Role[])
            : variantKey === 'antichess'
              ? roles.concat('k-piece')
              : roles;
      return renderPromotion(
        promoting.dest,
        rolesToChoose,
        opposite(getGround().state.turnPlayerIndex),
        getGround().state.orientation,
      );
    },
  };
}
