import { h, VNode } from 'snabbdom';
import { Chessground } from 'chessground';
import { Api as CgApi } from 'chessground/api';
import { Config as CgConfig } from 'chessground/config';
import * as cg from 'chessground/types';
import { DrawShape } from 'chessground/draw';
import changeColorHandle from 'common/coordsColor';
import resizeHandle from 'common/resize';
import AnalyseCtrl from './ctrl';
import { isOnlyDropsPly } from './util';
import * as stratUtils from 'stratutils';

export function render(ctrl: AnalyseCtrl): VNode {
  return h('div.cg-wrap.cgv' + ctrl.cgVersion.js, {
    hook: {
      insert: vnode => {
        ctrl.chessground = Chessground(vnode.elm as HTMLElement, makeConfig(ctrl));
        ctrl.setAutoShapes();
        if (ctrl.node.shapes) ctrl.chessground.setShapes(ctrl.node.shapes as DrawShape[]);
        ctrl.cgVersion.dom = ctrl.cgVersion.js;
        ctrl.setDropMode(ctrl.chessground);
      },
      destroy: _ => ctrl.chessground.destroy(),
    },
  });
}

export function promote(ground: CgApi, key: Key, role: cg.Role) {
  const piece = ground.state.pieces.get(key);
  if (piece && piece.role == 'p-piece') {
    ground.setPieces(
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

export function makeConfig(ctrl: AnalyseCtrl): CgConfig {
  const d = ctrl.data,
    hooks = ctrl.makeCgHooks(),
    pref = d.pref,
    opts = ctrl.makeCgOpts(),
    variantKey = d.game.variant.key,
    cgVariantKey = variantKey as cg.Variant;
  const config = {
    turnPlayerIndex: opts.turnPlayerIndex,
    fen: opts.fen,
    check: opts.check,
    lastMove: opts.lastMove,
    orientation: ctrl.getOrientation(),
    myPlayerIndex: ctrl.data.player.playerIndex,
    coordinates: pref.coords !== Prefs.Coords.Hidden && !ctrl.embed,
    boardScores: d.game.variant.key == 'togyzkumalak',
    addPieceZIndex: pref.is3d,
    viewOnly: !!ctrl.embed,
    movable: {
      free: false,
      playerIndex: opts.movable!.playerIndex,
      dests: opts.movable!.dests,
      showDests: pref.destination,
      rookCastle: pref.rookCastle,
    },
    events: {
      move: ctrl.userMove,
      dropNewPiece: ctrl.userNewPiece,
      insert(elements: cg.Elements) {
        if (!ctrl.embed) resizeHandle(elements, Prefs.ShowResizeHandle.Always, ctrl.node.ply);
        if (!ctrl.embed && ctrl.data.pref.coords == Prefs.Coords.Inside) changeColorHandle();
      },
    },
    premovable: {
      enabled: opts.premovable!.enabled,
      showDests: pref.destination,
      events: {
        set: ctrl.onPremoveSet,
      },
    },
    drawable: {
      enabled: !ctrl.embed,
      eraseOnClick: !ctrl.opts.study || !!ctrl.opts.practice,
      defaultSnapToValidMove: (playstrategy.storage.get('arrow.snap') || 1) != '0',
      pieces: {
        baseUrl:
          cgVariantKey === 'shogi' || cgVariantKey === 'minishogi'
            ? 'https://playstrategy.org/assets/piece/shogi/' +
              d.pref.pieceSet.filter(ps => ps.gameFamily === 'shogi')[0].name +
              '/'
            : cgVariantKey === 'flipello' || cgVariantKey === 'flipello10'
            ? 'https://playstrategy.org/assets/piece/flipello/' +
              d.pref.pieceSet.filter(ps => ps.gameFamily === 'flipello')[0].name +
              '/'
            : cgVariantKey === 'amazons'
            ? 'https://playstrategy.org/assets/piece/amazons/' +
              d.pref.pieceSet.filter(ps => ps.gameFamily === 'amazons')[0].name +
              '/'
            : cgVariantKey === 'oware'
            ? 'https://playstrategy.org/assets/piece/oware/' +
              d.pref.pieceSet.filter(ps => ps.gameFamily === 'oware')[0].name +
              '/'
            : cgVariantKey === 'togyzkumalak'
            ? 'https://playstrategy.org/assets/piece/togyzkumalak/' +
              d.pref.pieceSet.filter(ps => ps.gameFamily === 'togyzkumalak')[0].name +
              '/'
            : cgVariantKey === 'go9x9' || cgVariantKey === 'go13x13' || cgVariantKey === 'go19x19'
            ? 'https://playstrategy.org/assets/piece/go/' +
              d.pref.pieceSet.filter(ps => ps.gameFamily === 'go')[0].name +
              '/'
            : cgVariantKey === 'xiangqi' || cgVariantKey === 'minixiangqi'
            ? 'https://playstrategy.org/assets/piece/xiangqi/' +
              d.pref.pieceSet.filter(ps => ps.gameFamily === 'xiangqi')[0].name +
              '/'
            : 'https://playstrategy.org/assets/piece/chess/' +
              d.pref.pieceSet.filter(ps => ps.gameFamily === 'chess')[0].name +
              '/',
      },
    },
    highlight: {
      lastMove: pref.highlight,
      check: pref.highlight,
    },
    animation: {
      duration: pref.animationDuration,
    },
    dropmode: {
      showDropDests: true,
      dropDests: stratUtils.readDropsByRole(ctrl.node.dropsByRole),
      events: {
        cancel: hooks.onCancelDropMode,
      },
    },
    disableContextMenu: true,
    dimensions: d.game.variant.boardSize,
    variant: cgVariantKey,
    chess960: cgVariantKey == 'chess960',
    onlyDropsVariant: isOnlyDropsPly(ctrl.node, variantKey, d.onlyDropsVariant),
    singleClickMoveVariant:
      cgVariantKey === 'togyzkumalak' ||
      (stratUtils.variantUsesMancalaNotation(d.game.variant.key) && d.pref.mancalaMove),
  };
  ctrl.study && ctrl.study.mutateCgConfig(config);
  return config;
}
