import AnalyseCtrl from './ctrl';

import { path as treePath } from 'tree';

const isBackgammon = (ctrl: AnalyseCtrl) => ['backgammon', 'hyper', 'nackgammon'].includes(ctrl.data.game.variant.key);

export function canGoForward(ctrl: AnalyseCtrl): boolean {
  return ctrl.node.children.length > 0;
}

export function next(ctrl: AnalyseCtrl): void {
  const child = ctrl.node.children[0];
  if (!child) return;
  if (isBackgammon(ctrl) && child.uci === 'endturn') {
    // TODO: find a better way to override next() for backgammon
    const grandchild = child.children[0];
    if (grandchild) ctrl.userJumpIfCan(ctrl.path + child.id + grandchild.id);
  } else {
    ctrl.userJumpIfCan(ctrl.path + child.id);
  }
}

export function prev(ctrl: AnalyseCtrl): void {
  const parentPath = treePath.init(ctrl.path);
  if (isBackgammon(ctrl) && ctrl.nodeList.length >= 2) {
    // TODO: find a better way to override prev() for backgammon
    const parent = ctrl.nodeList[ctrl.nodeList.length - 2];
    if (parent.uci === 'endturn') {
      ctrl.userJumpIfCan(treePath.init(parentPath));
      return;
    }
  }
  ctrl.userJumpIfCan(parentPath);
}

export function last(ctrl: AnalyseCtrl): void {
  ctrl.userJumpIfCan(treePath.fromNodeList(ctrl.mainline));
}

export function first(ctrl: AnalyseCtrl): void {
  ctrl.userJump(treePath.root);
}

export function enterVariation(ctrl: AnalyseCtrl): void {
  const child = ctrl.node.children[1];
  if (child) ctrl.userJump(ctrl.path + child.id);
}

export function exitVariation(ctrl: AnalyseCtrl): void {
  if (ctrl.onMainline) return;
  let found,
    path = treePath.root;
  ctrl.nodeList.slice(1, -1).forEach(function (n: Tree.Node) {
    path += n.id;
    if (n.children[1]) found = path;
  });
  if (found) ctrl.userJump(found);
}
