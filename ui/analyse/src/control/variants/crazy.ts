import type AnalyseCtrl from '../../ctrl';

export const configure = (ctrl: AnalyseCtrl): void => {
  // Canceling drop mode deselects the pocket piece; redraw to reflect the UI change.
  ctrl.controlConfig.cgHooks = { onCancelDropMode: () => ctrl.redraw() };
};
