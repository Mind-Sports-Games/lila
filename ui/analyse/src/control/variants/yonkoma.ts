import type AnalyseCtrl from '../../ctrl';
import { readDests } from 'stratutils';

// A player with no legal move has the turn skipped. The round plays the pass for him, and so
// does the analysis: as soon as a position arrives with nothing to move and the game not
// over, the pass is sent for the player to move.
export const configure = (ctrl: AnalyseCtrl): void => {
  const passIfStuck = () => {
    const node = ctrl.node;
    const dests = readDests(node.dests);
    if (!dests || dests.size > 0) return;
    if (node.children.length > 0 || ctrl.outcome(node)) return;
    ctrl.sendPass();
  };
  ctrl.controlConfig.onAfterAddNode = passIfStuck;
  ctrl.controlConfig.onAfterAddDests = passIfStuck;
};
