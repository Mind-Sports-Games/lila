import type { VNode } from 'snabbdom';
import type AnalyseCtrl from '../ctrl';
import type { Config as CgConfig } from 'chessground/config';
import type { Piece as CgPiece, Key as CgKey } from 'chessground/types';
import { path as treePath } from 'tree';

export interface ControlConfig {
  // Navigation
  canGoForward?(ctrl: AnalyseCtrl): boolean;
  next?(ctrl: AnalyseCtrl): void;
  prev?(ctrl: AnalyseCtrl): void;
  last?(ctrl: AnalyseCtrl): void;
  first?(ctrl: AnalyseCtrl): void;
  enterVariation?(ctrl: AnalyseCtrl): void;
  exitVariation?(ctrl: AnalyseCtrl): void;

  // Orientation
  getOrientation?(): string;
  noRetroOnFlip?(): boolean;

  // Lifecycle
  onInit?(): void;
  onAfterAddNode?(node: Tree.Node): void;
  onAfterAddDests?(): void;
  onUserAction?(): void;
  afterJump?(): void;
  onStepFailure?(): void;

  // Chessground config
  cgFen?(fen: string): string;
  mutateCgOpts?(node: Tree.Node, config: CgConfig): void;
  suppressPremove?(): boolean;

  // Chessground hooks (registered per-variant)
  cgHooks?: {
    onCancelDropMode?(): void;
    onSelectDice?(dice: unknown[]): void;
    onButtonClick?(button: string): void;
    onUserLift?(dest: string): void;
  };

  // Dests / ground refresh
  needsDestsRefetch?(node: Tree.Node): boolean;
  needsFullRedrawAfterGround?(): boolean;

  // Drop mode
  showDropDestsInDropMode?(): boolean;
  alwaysCancelDropMode?(): boolean;

  // Sound
  nodeSoundOverride?(node: Tree.Node): string | false | undefined;
  dropSoundOverride?(piece: CgPiece, pos: CgKey, captured?: CgPiece): string | undefined;

  // Board overlay (e.g. dice picker)
  renderBoardOverlay?(): VNode | null;

  // Redirect tree-click jump paths (e.g. skip to end-of-turn for backgammon roll nodes)
  redirectJumpPath?(path: string): string;

  // Active path override for tree highlighting (e.g. while dice picker is shown)
  getActivePath?(): Tree.Path | null;

  // Study annotation permissions per node
  isNodeCommentable?(node: Tree.Node): boolean;
  isNodeAnnotatable?(node: Tree.Node): boolean;
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
  let found: string | undefined,
    path = treePath.root;
  ctrl.nodeList.slice(1, -1).forEach(function (n: Tree.Node) {
    path += n.id;
    if (n.children[1]) found = path;
  });
  if (found) ctrl.userJump(found);
}
