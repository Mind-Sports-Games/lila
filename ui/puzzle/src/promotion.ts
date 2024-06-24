import { h } from 'snabbdom';
import { bind, onInsert } from './util';
import { Api as CgApi } from 'chessground/build/api';
import * as cgUtil from 'chessground/build/util';
import { Role } from 'chessground/build/types';
import { MaybeVNode, Vm, Redraw, Promotion } from './interfaces';
import { Prop } from 'common';

export default function (vm: Vm, getGround: Prop<CgApi>, redraw: Redraw): Promotion {
  let promoting: false | { orig: Key; dest: Key; callback: (orig: Key, key: Key, prom: Role) => void } = false;

  function start(orig: Key, dest: Key, callback: (orig: Key, key: Key, prom: Role) => void) {
    const g = getGround(),
      piece = g.state.pieces.get(dest);
    if (
      piece &&
      piece.role == 'p-piece' &&
      ((dest[1] == '8' && g.state.turnPlayerIndex == 'p2') || (dest[1] == '1' && g.state.turnPlayerIndex == 'p1'))
    ) {
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

  function promote(g: CgApi, key: Key, role: Role): void {
    const piece = g.state.pieces.get(key);
    if (piece && piece.role == 'p-piece') {
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
        ])
      );
    }
  }

  function finish(role: Role): void {
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

  function renderPromotion(dest: Key, pieces: Role[], playerIndex: PlayerIndex, orientation: Orientation): MaybeVNode {
    if (!promoting) return;

    let left = (7 - cgUtil.key2pos(dest)[0]) * 12.5;
    if (orientation === 'p1') left = 87.5 - left;

    const vertical = playerIndex === orientation ? 'top' : 'bottom';

    return h(
      'div#promotion-choice.' + vertical,
      {
        hook: onInsert(el => {
          el.addEventListener('click', cancel);
          el.oncontextmenu = () => false;
        }),
      },
      pieces.map(function (serverRole, i) {
        const top = (playerIndex === orientation ? i : 7 - i) * 12.5;
        return h(
          'square',
          {
            attrs: {
              style: 'top: ' + top + '%;left: ' + left + '%',
            },
            hook: bind('click', e => {
              e.stopPropagation();
              finish(serverRole);
            }),
          },
          [h('piece.' + serverRole + '.' + playerIndex)]
        );
      })
    );
  }

  return {
    start,
    cancel,
    view() {
      if (!promoting) return;
      const pieces: Role[] = ['q-piece', 'n-piece', 'r-piece', 'b-piece'];
      return renderPromotion(
        promoting.dest,
        pieces,
        cgUtil.opposite(getGround().state.turnPlayerIndex),
        getGround().state.orientation
      );
    },
  };
}
