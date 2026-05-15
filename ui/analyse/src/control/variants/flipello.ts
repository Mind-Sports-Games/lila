import type AnalyseCtrl from '../../ctrl';
import { readDests } from 'stratutils';

export const configure = (ctrl: AnalyseCtrl): void => {
  // When only one move is available (a pass), play it automatically.
  ctrl.controlConfig.onAfterAddNode = (node: Tree.Node) => {
    const dests = readDests(node.dests);
    if (!dests || dests.size !== 1) return;
    const [passOrig, passDests] = [...dests.entries()][0];
    if (passDests.length === 1) ctrl.sendMove(passOrig, passDests[0], undefined, undefined);
  };
};
