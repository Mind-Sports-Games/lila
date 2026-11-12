import type AnalyseCtrl from '../../ctrl';
import { readDests } from 'stratutils';

// A player with no legal move has the turn skipped (pass action).
export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.onAfterAddNode = () => {
    const node = ctrl.node;
    const dests = readDests(node.dests);
    if (!dests || dests.size > 0) return;
    if (node.children.length > 0 || ctrl.outcome(node)) return;
    ctrl.sendPass();
  };
};
