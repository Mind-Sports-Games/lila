import type AnalyseCtrl from '../../ctrl';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.showDropDestsInDropMode = () => false;
  ctrl.controlConfig.cgHooks = { onCancelDropMode: () => ctrl.redraw() };
};
