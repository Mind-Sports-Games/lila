import changeColorHandle from 'common/coordsColor';
import resizeHandle from 'common/resize';
import { Chessground } from 'chessground';
import { Config as CgConfig } from 'chessground/config';
import { Controller } from '../interfaces';
import { h, VNode } from 'snabbdom';
import * as Prefs from 'common/prefs';
import * as cg from 'chessground/types';

export default function (ctrl: Controller): VNode {
  const config = makeConfig(ctrl);
  return h('div.cg-wrap', {
    hook: {
      insert: vnode => ctrl.ground(Chessground(vnode.elm as HTMLElement, config)),
      destroy: _ => ctrl.ground()!.destroy(),
    },
  });
}

function makeConfig(ctrl: Controller): CgConfig {
  const opts = ctrl.makeCgOpts();
  return {
    fen: opts.fen,
    orientation: opts.orientation,
    myPlayerIndex: opts.myPlayerIndex,
    turnPlayerIndex: opts.turnPlayerIndex,
    check: opts.check,
    lastMove: opts.lastMove,
    coordinates: ctrl.pref.coords,
    addPieceZIndex: ctrl.pref.is3d,
    movable: {
      free: false,
      playerIndex: opts.movable!.playerIndex,
      dests: opts.movable!.dests,
      showDests: ctrl.pref.destination,
      rookCastle: ctrl.pref.rookCastle,
    },
    draggable: {
      enabled: ctrl.pref.moveEvent > 0,
      showGhost: ctrl.pref.highlight,
    },
    selectable: {
      enabled: ctrl.pref.moveEvent !== 1,
    },
    events: {
      move: ctrl.userMove,
      insert(elements) {
        resizeHandle(elements, Prefs.ShowResizeHandle.Always, ctrl.vm.node.ply, _ => true);
        if (ctrl.pref.coords === cg.Coords.Inside) changeColorHandle();
      },
    },
    premovable: {
      enabled: opts.premovable!.enabled,
    },
    drawable: {
      enabled: true,
      defaultSnapToValidMove: (playstrategy.storage.get('arrow.snap') || 1) != '0',
    },
    highlight: {
      lastMove: ctrl.pref.highlight,
      check: ctrl.pref.highlight,
    },
    animation: {
      enabled: true,
      duration: ctrl.pref.animation.duration,
    },
    disableContextMenu: true,
    dimensions: opts.dimensions,
    variant: opts.variant,
    chess960: opts.chess960,
  };
}
