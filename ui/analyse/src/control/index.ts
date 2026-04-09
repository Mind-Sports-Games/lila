import type AnalyseCtrl from '../ctrl';

import { path as treePath } from 'tree';

export interface ControlConfig {
  canGoForward?: (ctrl: AnalyseCtrl) => boolean;
  next?: (ctrl: AnalyseCtrl) => void;
  prev?: (ctrl: AnalyseCtrl) => void;
  last?: (ctrl: AnalyseCtrl) => void;
  first?: (ctrl: AnalyseCtrl) => void;
  enterVariation?: (ctrl: AnalyseCtrl) => void;
  exitVariation?: (ctrl: AnalyseCtrl) => void;
}

export function canGoForward(ctrl: AnalyseCtrl): boolean {
  return ctrl.controlConfig.canGoForward ? ctrl.controlConfig.canGoForward(ctrl) : ctrl.node.children.length > 0;
}

export function next(ctrl: AnalyseCtrl): void {
  if (ctrl.controlConfig.next) {
    ctrl.controlConfig.next(ctrl);
    return;
  }
  const child = ctrl.node.children[0];
  if (child) ctrl.userJumpIfCan(ctrl.path + child.id);
}

export function prev(ctrl: AnalyseCtrl): void {
  if (ctrl.controlConfig.prev) {
    ctrl.controlConfig.prev(ctrl);
    return;
  }
  ctrl.userJumpIfCan(treePath.init(ctrl.path));
}

export function last(ctrl: AnalyseCtrl): void {
  if (ctrl.controlConfig.last) {
    ctrl.controlConfig.last(ctrl);
    return;
  }
  ctrl.userJumpIfCan(treePath.fromNodeList(ctrl.mainline));
}

export function first(ctrl: AnalyseCtrl): void {
  if (ctrl.controlConfig.first) {
    ctrl.controlConfig.first(ctrl);
    return;
  }
  ctrl.userJump(treePath.root);
}

export function enterVariation(ctrl: AnalyseCtrl): void {
  if (ctrl.controlConfig.enterVariation) {
    ctrl.controlConfig.enterVariation(ctrl);
    return;
  }
  const child = ctrl.node.children[1];
  if (child) ctrl.userJump(ctrl.path + child.id);
}

export function exitVariation(ctrl: AnalyseCtrl): void {
  if (ctrl.controlConfig.exitVariation) {
    ctrl.controlConfig.exitVariation(ctrl);
    return;
  }
  if (ctrl.onMainline) return;
  let found,
    path = treePath.root;
  ctrl.nodeList.slice(1, -1).forEach(function (n: Tree.Node) {
    path += n.id;
    if (n.children[1]) found = path;
  });
  if (found) ctrl.userJump(found);
}
