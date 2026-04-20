import type AnalyseCtrl from '../../ctrl';
import { orientationForLOA, oppositeOrientationForLOA } from 'chessground/util';
import * as cg from 'chessground/types';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.getOrientation = () => {
    const c = ctrl.data.player.playerIndex as cg.PlayerIndex;
    return ctrl.flipped ? oppositeOrientationForLOA(c) : orientationForLOA(c);
  };
};
