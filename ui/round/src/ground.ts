import { h } from 'snabbdom';
import { Chessground } from 'chessground';
import * as cg from 'chessground/types';
import {
  oppositeOrientationForLOA,
  orientationForLOA,
  oppositeOrientationForBackgammon,
  orientationForBackgammon,
} from 'chessground/util';
import { Api as CgApi } from 'chessground/api';
import { Config } from 'chessground/config';
import changeColorHandle from 'common/coordsColor';
import resizeHandle from 'common/resize';
import * as util from './util';
import { plyStep } from './round';
import RoundController from './ctrl';
import { RoundData } from './interfaces';
import * as stratUtils from 'stratutils';
import * as Prefs from 'common/prefs';

export function makeConfig(ctrl: RoundController): Config {
  const data = ctrl.data,
    hooks = ctrl.makeCgHooks(),
    step = plyStep(data, ctrl.ply),
    playing = ctrl.isPlaying(),
    variantKey = data.game.variant.key as cg.Variant,
    turnPlayerIndex = util.turnPlayerIndexFromLastTurn(step.turnCount),
    dice = data.dice
      ? data.dice
      : stratUtils.readDice(step.fen, data.game.variant.key, data.canEndTurn, ctrl.areDiceDescending);
  return {
    fen: step.fen,
    orientation: boardOrientation(data, ctrl.flip),
    myPlayerIndex: data.player.playerIndex,
    turnPlayerIndex: turnPlayerIndex,
    lastMove: util.lastMove(data.onlyDropsVariant, step.uci),
    check: !!step.check,
    coordinates: data.pref.coords !== Prefs.Coords.Hidden,
    boardScores: ['togyzkumalak', 'bestemshe', 'backgammon', 'hyper', 'nackgammon'].includes(data.game.variant.key), // abalone ?
    dice: dice,
    canUndo: data.canUndo,
    showUndoButton: playing && turnPlayerIndex == data.player.playerIndex && dice.length > 0,
    addPieceZIndex: ctrl.data.pref.is3d,
    selectOnly: data.selectMode,
    highlight: {
      lastMove:
        data.pref.highlight &&
        !data.selectMode &&
        !['backgammon', 'hyper', 'nackgammon'].includes(data.game.variant.key),
      check: data.pref.highlight && !['backgammon', 'hyper', 'nackgammon'].includes(data.game.variant.key),
    },
    events: {
      move: hooks.onMove,
      dropNewPiece: hooks.onNewPiece,
      insert(elements) {
        resizeHandle(elements, ctrl.data.pref.resizeHandle, ctrl.ply);
        if (data.pref.coords === Prefs.Coords.Inside) changeColorHandle();
      },
      select: hooks.onSelect,
      selectDice: hooks.onSelectDice,
      undoButton: hooks.onUndoButton,
    },
    movable: {
      free: false,
      playerIndex: playing ? data.player.playerIndex : undefined,
      dests: playing ? util.parsePossibleMoves(data.possibleMoves, ctrl.activeDiceValue(dice)) : new Map(),
      showDests: data.pref.destination,
      rookCastle: data.pref.rookCastle,
      events: {
        after: hooks.onUserMove,
        afterNewPiece: hooks.onUserNewPiece,
      },
    },
    liftable: {
      liftDests: playing ? util.parsePossibleLifts(data.possibleLifts) : [],
      events: {
        after: hooks.onUserLift,
      },
    },
    animation: {
      enabled: !['backgammon', 'hyper', 'nackgammon'].includes(data.game.variant.key),
      duration: data.pref.animationDuration,
    },
    premovable: {
      enabled:
        data.pref.enablePremove &&
        !data.onlyDropsVariant &&
        !['oware', 'togyzkumalak', 'bestemshe', 'backgammon', 'hyper', 'nackgammon'].includes(data.game.variant.key),
      showDests: data.pref.destination,
      castle: data.game.variant.key !== 'antichess' && data.game.variant.key !== 'noCastling',
      events: {
        set: hooks.onPremove,
        unset: hooks.onCancelPremove,
      },
    },
    predroppable: {
      enabled: data.pref.enablePremove && ['crazyhouse', 'shogi', 'minishogi'].includes(data.game.variant.key),
      events: {
        set: hooks.onPredrop,
        unset() {
          hooks.onPredrop(undefined);
        },
      },
    },
    dropmode: {
      showDropDests: !['go9x9', 'go13x13', 'go19x19', 'backgammon', 'hyper', 'nackgammon'].includes(
        data.game.variant.key,
      ),
      dropDests: playing ? stratUtils.readDropsByRole(data.possibleDropsByRole) : new Map(),
      active: data.onlyDropsVariant && playing ? true : false,
      piece:
        data.onlyDropsVariant && playing
          ? stratUtils.onlyDropsVariantPiece(data.game.variant.key, turnPlayerIndex)
          : undefined,
      events: {
        cancel: hooks.onCancelDropMode,
      },
    },
    draggable: {
      enabled:
        data.pref.moveEvent !== Prefs.MoveEvent.Click &&
        !['oware', 'backgammon', 'hyper', 'nackgammon'].includes(data.game.variant.key),
      showGhost: data.pref.highlight,
    },
    selectable: {
      enabled: data.pref.moveEvent !== Prefs.MoveEvent.Drag,
    },
    drawable: {
      enabled: true,
      defaultSnapToValidMove: (playstrategy.storage.get('arrow.snap') || 1) != '0',
      pieces: {
        baseUrl:
          variantKey === 'shogi' || variantKey === 'minishogi'
            ? 'https://playstrategy.org/assets/piece/shogi/' +
              data.pref.pieceSet.filter(ps => ps.gameFamily === 'shogi')[0].name +
              '/'
            : variantKey === 'flipello' || variantKey === 'flipello10'
              ? 'https://playstrategy.org/assets/piece/flipello/' +
                data.pref.pieceSet.filter(ps => ps.gameFamily === 'flipello')[0].name +
                '/'
              : variantKey === 'amazons'
                ? 'https://playstrategy.org/assets/piece/amazons/' +
                  data.pref.pieceSet.filter(ps => ps.gameFamily === 'amazons')[0].name +
                  '/'
                : variantKey === 'oware'
                  ? 'https://playstrategy.org/assets/piece/oware/' +
                    data.pref.pieceSet.filter(ps => ps.gameFamily === 'oware')[0].name +
                    '/'
                  : variantKey === 'togyzkumalak' || variantKey === 'bestemshe'
                    ? 'https://playstrategy.org/assets/piece/togyzkumalak/' +
                      data.pref.pieceSet.filter(ps => ps.gameFamily === 'togyzkumalak')[0].name +
                      '/'
                    : variantKey === 'go9x9' || variantKey === 'go13x13' || variantKey === 'go19x19'
                      ? 'https://playstrategy.org/assets/piece/go/' +
                        data.pref.pieceSet.filter(ps => ps.gameFamily === 'go')[0].name +
                        '/'
                      : variantKey === 'backgammon' || variantKey === 'hyper' || variantKey === 'nackgammon'
                        ? 'https://playstrategy.org/assets/piece/backgammon/' +
                          data.pref.pieceSet.filter(ps => ps.gameFamily === 'backgammon')[0].name +
                          '/'
                        : variantKey === 'abalone'
                          ? 'https://playstrategy.org/assets/piece/abalone/' + // @TODO VFR: check it actually works
                            data.pref.pieceSet.filter(ps => ps.gameFamily === 'abalone')[0].name +
                            '/'
                          : variantKey === 'xiangqi' || variantKey === 'minixiangqi'
                            ? 'https://playstrategy.org/assets/piece/xiangqi/' +
                              data.pref.pieceSet.filter(ps => ps.gameFamily === 'xiangqi')[0].name +
                              '/'
                            : 'https://playstrategy.org/assets/piece/chess/' +
                              data.pref.pieceSet.filter(ps => ps.gameFamily === 'chess')[0].name +
                              '/',
      },
    },
    disableContextMenu: true,
    dimensions: data.game.variant.boardSize,
    variant: variantKey,
    chess960: data.game.variant.key === 'chess960',
    onlyDropsVariant: data.onlyDropsVariant,
    singleClickMoveVariant:
      ['togyzkumalak', 'bestemshe', 'backgammon', 'hyper', 'nackgammon'].includes(data.game.variant.key) ||
      (data.game.variant.key === 'oware' && data.pref.mancalaMove),
  };
}

export function reload(ctrl: RoundController) {
  ctrl.chessground.set(makeConfig(ctrl));
  reSelectSelectedSquares(ctrl);
}

export function reSelectSelectedSquares(ctrl: RoundController) {
  if (ctrl.data.selectedSquares) {
    if (
      ctrl.data.deadStoneOfferState &&
      ((ctrl.data.deadStoneOfferState === 'P1Offering' && ctrl.data.player.playerIndex == 'p1') ||
        (ctrl.data.deadStoneOfferState === 'P2Offering' && ctrl.data.player.playerIndex == 'p2'))
    ) {
      ctrl.chessground.set({ selectOnly: true, viewOnly: true });
    }
    const goStonesToSelect = util.goStonesToSelect(
      ctrl.data.selectedSquares,
      ctrl.chessground.state.pieces,
      ctrl.data.game.variant.boardSize,
    );
    for (const square of goStonesToSelect) {
      ctrl.chessground.selectSquare(square as cg.Key);
    }
    //also set currentSelectedSquares as this becomes undefined upon reload
    ctrl.data.currentSelectedSquares = ctrl.data.selectedSquares;
  }
}

export function promote(ground: CgApi, key: cg.Key, role: cg.Role) {
  const piece = ground.state.pieces.get(key);
  if (
    (piece && piece.role === 'p-piece' && ground.state.variant !== 'shogi' && ground.state.variant !== 'minishogi') ||
    (piece &&
      (ground.state.variant == 'shogi' || ground.state.variant == 'minishogi') &&
      piece.role !== 'k-piece' &&
      piece.role !== 'g-piece')
  ) {
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
      ]),
    );
  }
}

export function boardOrientation(data: RoundData, flip: boolean): cg.Orientation {
  if (data.game.variant.key === 'racingKings') return flip ? 'p2' : 'p1';
  if (data.game.variant.key === 'linesOfAction' || data.game.variant.key === 'scrambledEggs') {
    return flip ? oppositeOrientationForLOA(data.player.playerIndex) : orientationForLOA(data.player.playerIndex);
  }
  if (
    data.game.variant.key === 'backgammon' ||
    data.game.variant.key === 'hyper' ||
    data.game.variant.key === 'nackgammon'
  ) {
    return flip
      ? oppositeOrientationForBackgammon(data.player.playerIndex)
      : orientationForBackgammon(data.player.playerIndex);
  } else return flip ? data.opponent.playerIndex : data.player.playerIndex;
}

export function render(ctrl: RoundController) {
  return h('div.cg-wrap', {
    hook: util.onInsert(el => ctrl.setChessground(Chessground(el, makeConfig(ctrl)))),
  });
}
