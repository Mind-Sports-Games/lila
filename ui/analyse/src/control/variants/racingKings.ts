import type AnalyseCtrl from '../../ctrl';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.getOrientation = () => 'p1';
  // Retro mode does not apply to Racing Kings.
  ctrl.controlConfig.noRetroOnFlip = () => true;
};
