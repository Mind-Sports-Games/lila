import type AnalyseCtrl from '../../ctrl';
import { readDests } from 'stratutils';

// A player with no legal move has the turn skipped. In a game the server plays that pass; on
// the analysis board it is sent right after a move of the user's own that leaves the opponent
// with nothing to move. Browsing to a stuck position adds nothing (the final position of a
// game that ended by resignation, say), and neither does a move of another study member.
export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.onAfterAddNode = () => {
    const node = ctrl.node;
    const dests = readDests(node.dests);
    if (!dests || dests.size > 0) return;
    if (node.children.length > 0 || ctrl.outcome(node)) return;
    ctrl.sendPass();
  };
};
