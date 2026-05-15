import type AnalyseCtrl from '../../ctrl';
import { amazonsChessgroundFen } from 'stratops/compat';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.cgFen = (fen: string) => amazonsChessgroundFen(fen);
  ctrl.controlConfig.alwaysCancelDropMode = () => true;
};
