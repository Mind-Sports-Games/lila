import type AnalyseCtrl from '../../ctrl';

export const configure = (ctrl: AnalyseCtrl): void => {
  // Board scores are rendered from FEN data and need a full redraw on each jump.
  ctrl.controlConfig.afterJump = () => {
    if (ctrl.chessground) ctrl.chessground.redrawAll();
  };
};
