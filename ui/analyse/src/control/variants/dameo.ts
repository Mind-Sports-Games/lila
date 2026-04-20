import type AnalyseCtrl from '../../ctrl';
import { dameo as dameoStratUtils } from 'stratutils';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.mutateCgOpts = (node, config) => {
    config.selected = dameoStratUtils.activePiecePosition(node.fen) as unknown as typeof config.selected;
  };
  ctrl.controlConfig.afterJump = () => {
    if (ctrl.chessground) ctrl.chessground.selectSquare(dameoStratUtils.activePiecePosition(ctrl.node.fen));
  };
};
