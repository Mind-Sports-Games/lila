import type { PlayerIndex as CgPlayerIndex } from 'chessground/types';
import {
  orientationForLOA as CgOrientationForLOA,
  oppositeOrientationForLOA as CgOppositeOrientationForLOA,
} from 'chessground/util';

import type AnalyseCtrl from '../../ctrl';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.getOrientation = () => {
    const c = ctrl.data.player.playerIndex as CgPlayerIndex;
    return ctrl.flipped ? CgOppositeOrientationForLOA(c) : CgOrientationForLOA(c);
  };
};
