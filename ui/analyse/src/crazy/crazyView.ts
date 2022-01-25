import { drag } from './crazyCtrl';
import { h } from 'snabbdom';
import { MouchEvent } from 'chessground/types';
import { onInsert } from '../util';
import AnalyseCtrl from '../ctrl';

const eventNames = ['mousedown', 'touchstart'];
const oKeys = ['p-piece', 'n-piece', 'b-piece', 'r-piece', 'q-piece'];

type Position = 'top' | 'bottom';

export default function (ctrl: AnalyseCtrl, playerIndex: PlayerIndex, position: Position) {
  if (!ctrl.node.crazy) return;
  const pocket = ctrl.node.crazy.pockets[playerIndex === 'p1' ? 0 : 1];
  const dropped = ctrl.justDropped;
  const captured = ctrl.justCaptured;
  if (captured) captured.role = captured.promoted ? 'p-piece' : captured.role;
  const activePlayerIndex = playerIndex === ctrl.turnPlayerIndex();
  const usable = !ctrl.embed && activePlayerIndex;
  return h(
    `div.pocket.is2d.pocket-${position}.pos-${ctrl.bottomPlayerIndex()}`,
    {
      class: { usable },
      hook: onInsert(el => {
        if (ctrl.embed) return;
        eventNames.forEach(name => {
          el.addEventListener(name, e => drag(ctrl, playerIndex, e as MouchEvent));
        });
      }),
    },
    oKeys.map(role => {
      let nb = pocket[role] || 0;
      if (activePlayerIndex) {
        if (dropped === role) nb--;
        if (captured && captured.role === role) nb++;
      }
      return h(
        'div.pocket-c1',
        h(
          'div.pocket-c2',
          h('piece.' + role + '.' + playerIndex, {
            attrs: {
              'data-role': role,
              'data-playerindex': playerIndex,
              'data-nb': nb,
            },
          })
        )
      );
    })
  );
}
