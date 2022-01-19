import { h } from 'snabbdom';
import * as ground from './ground';
import { bind, onInsert } from './util';
import * as util from 'chessground/util';
import { Role } from 'chessground/types';
import AnalyseCtrl from './ctrl';
import { MaybeVNode, JustCaptured } from './interfaces';

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
  callback: Callback
): boolean {
  const s = ctrl.chessground.state;
  const piece = s.pieces.get(dest);
  if (
    piece &&
    piece.role == 'p-piece' &&
    ((dest[1] == '8' && s.turnPlayerIndex == 'p2') || (dest[1] == '1' && s.turnPlayerIndex == 'p1'))
  ) {
    promoting = {
      orig,
      dest,
      capture,
      callback,
    };
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
  pieces: string[],
  playerIndex: PlayerIndex,
  orientation: Orientation
): MaybeVNode {
  if (!promoting) return;

  let left = (7 - util.key2pos(dest)[0]) * 12.5;
  if (orientation === 'p1') left = 87.5 - left;

  const vertical = playerIndex === orientation ? 'top' : 'bottom';

  return h(
    'div#promotion-choice.' + vertical,
    {
      hook: onInsert(el => {
        el.addEventListener('click', _ => cancel(ctrl));
        el.oncontextmenu = () => false;
      }),
    },
    pieces.map(function (serverRole: Role, i) {
      const top = (playerIndex === orientation ? i : 7 - i) * 12.5;
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
        [h(`piece.${serverRole}.${playerIndex}`)]
      );
    })
  );
}

const roles: Role[] = ['q-piece', 'n-piece', 'r-piece', 'b-piece'];

export function view(ctrl: AnalyseCtrl): MaybeVNode {
  if (!promoting) return;

  return renderPromotion(
    ctrl,
    promoting.dest,
    ctrl.data.game.variant.key === 'antichess' ? roles.concat('k-piece') : roles,
    promoting.dest[1] === '8' ? 'p1' : 'p2',
    ctrl.chessground.state.orientation
  );
}
