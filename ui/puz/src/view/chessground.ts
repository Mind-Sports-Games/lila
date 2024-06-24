import changePlayerIndexHandle from 'common/coordsColor';
import resizeHandle from 'common/resize';
import { Config as CgConfig } from 'chessground/build/config';
import { PuzPrefs, UserMove } from '../interfaces';

export function makeConfig(opts: CgConfig, pref: PuzPrefs, userMove: UserMove): CgConfig {
  return {
    fen: opts.fen,
    orientation: opts.orientation,
    myPlayerIndex: opts.myPlayerIndex,
    turnPlayerIndex: opts.turnPlayerIndex,
    check: opts.check,
    lastMove: opts.lastMove,
    coordinates: pref.coords !== Prefs.Coords.Hidden,
    addPieceZIndex: pref.is3d,
    movable: {
      free: false,
      playerIndex: opts.movable!.playerIndex,
      dests: opts.movable!.dests,
      showDests: pref.destination,
      rookCastle: pref.rookCastle,
    },
    draggable: {
      enabled: pref.moveEvent > 0,
      showGhost: pref.highlight,
    },
    selectable: {
      enabled: pref.moveEvent !== 1,
    },
    events: {
      move: userMove,
      insert(elements) {
        resizeHandle(elements, Prefs.ShowResizeHandle.OnlyAtStart, 0, p => p == 0);
        if (pref.coords == Prefs.Coords.Inside) changePlayerIndexHandle();
      },
    },
    premovable: {
      enabled: false,
    },
    drawable: {
      enabled: true,
      defaultSnapToValidMove: (playstrategy.storage.get('arrow.snap') || 1) != '0',
    },
    highlight: {
      lastMove: pref.highlight,
      check: pref.highlight,
    },
    animation: {
      enabled: false,
    },
    disableContextMenu: true,
    dimensions: opts.dimensions,
    variant: opts.variant,
    chess960: opts.chess960,
  };
}
