import type AnalyseCtrl from '../ctrl';
import { path as treePath } from 'tree';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.next = ctrl => {
    const child = ctrl.node.children[0];
    if (!child) return;
    if (child.uci === 'endturn') {
      const grandchild = child.children[0];
      if (grandchild) ctrl.userJumpIfCan(ctrl.path + child.id + grandchild.id);
    } else {
      ctrl.userJumpIfCan(ctrl.path + child.id);
    }
  };
  ctrl.controlConfig.prev = ctrl => {
    const parentPath = treePath.init(ctrl.path);
    if (ctrl.nodeList.length >= 2) {
      const parent = ctrl.nodeList[ctrl.nodeList.length - 2];
      if (parent.uci === 'endturn') {
        ctrl.userJumpIfCan(treePath.init(parentPath));
        return;
      }
    }
    ctrl.userJumpIfCan(parentPath);
  };
};
