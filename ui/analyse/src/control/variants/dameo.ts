import type AnalyseCtrl from '../../ctrl';
import { dameo } from 'stratutils';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.mutateCgOpts = (node, config) => {
    config.selected = dameo.activePiecePosition(node.fen) as unknown as typeof config.selected;
  };
  ctrl.controlConfig.afterJump = () => {
    if (ctrl.chessground) ctrl.chessground.selectSquare(dameo.activePiecePosition(ctrl.node.fen));
  };
};
