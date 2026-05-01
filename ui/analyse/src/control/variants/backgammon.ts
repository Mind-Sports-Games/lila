import { h } from 'snabbdom';
import type { Key as CgKey, Piece as CgPiece, PlayerIndex as CgPlayerIndex } from 'chessground/types';
import {
  backgammonPosDiff as CgBackgammonPosDiff,
  orientationForBackgammon as CgOrientationForBackgammon,
  oppositeOrientationForBackgammon as CgOppositeOrientationForBackgammon,
} from 'chessground/util';

import type AnalyseCtrl from '../../ctrl';
import type { AnaRoll, AnaEndTurn } from '../../study/interfaces';
import { readDice } from 'stratutils';
import { path as treePath } from 'tree';
import { bind } from '../../util';

const diceNames = ['one', 'two', 'three', 'four', 'five', 'six'];

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.getOrientation = () => {
    const c = ctrl.data.player.playerIndex as CgPlayerIndex;
    return ctrl.flipped ? CgOppositeOrientationForBackgammon(c) : CgOrientationForBackgammon(c);
  };

  let areDiceDescending = true;
  let lastRollPath = '';
  let rollPending = false;
  let rollSent = false;
  let actionSent = false;
  let endTurnSent = false;
  let pendingNoMovesCheck = false;

  // Dice picker state
  let dicePickerActive = false;
  let die1Pick: number | null | undefined = undefined; // undefined=not chosen, null=random(?)
  let die2Pick: number | null | undefined = undefined;
  let diceWasPreFilled = false;

  const diceRollUci = /^[1-6](\/[1-6])+$/;

  const doSendRoll = (dice: number[]) => {
    rollPending = true;
    rollSent = true;
    const roll: AnaRoll = {
      variant: ctrl.data.game.variant.key,
      lib: ctrl.data.game.variant.lib,
      fen: ctrl.node.fen,
      path: ctrl.path,
      dice,
    };
    if (ctrl.practice) ctrl.practice.onUserMove();
    ctrl.socket.sendAnaRoll(roll);
    ctrl.redraw();
  };

  const triggerRoll = () => {
    if (rollPending) return;
    const existingRoll = ctrl.node.children.find(c => diceRollUci.test(c.uci ?? ''));
    if (existingRoll) {
      const parts = (existingRoll.uci ?? '').split('/').map(Number);
      die1Pick = parts[0] ?? undefined;
      die2Pick = parts[1] ?? undefined;
      diceWasPreFilled = true;
    } else {
      die1Pick = undefined;
      die2Pick = undefined;
      diceWasPreFilled = false;
    }
    dicePickerActive = true;
    ctrl.redraw();
  };

  const onDiePick = (die: 1 | 2, value: number | null) => {
    if (diceWasPreFilled) {
      diceWasPreFilled = false;
      if (die === 1) die2Pick = undefined;
      else die1Pick = undefined;
    }
    if (die === 1) die1Pick = die1Pick === value ? undefined : value;
    else die2Pick = die2Pick === value ? undefined : value;

    if (die1Pick !== undefined && die2Pick !== undefined) {
      dicePickerActive = false;
      const d1 = die1Pick !== null ? (die1Pick as number) : Math.ceil(Math.random() * 6);
      const d2 = die2Pick !== null ? (die2Pick as number) : Math.ceil(Math.random() * 6);
      doSendRoll([d1, d2]);
    } else {
      ctrl.redraw();
    }
  };

  const findEquivalentEndTurn = (): { endTurnPath: string; branchRootPath: string | null } | null => {
    const directEndTurn = ctrl.node.children.find(c => c.uci === 'endturn');
    if (directEndTurn) return { endTurnPath: ctrl.path + directEndTurn.id, branchRootPath: null };

    const targetBoard = ctrl.node.fen.split(' ')[0];
    const nodeList = ctrl.nodeList;
    let rollIdx = -1;
    for (let i = nodeList.length - 2; i >= 0; i--) {
      if (diceRollUci.test(nodeList[i].uci ?? '')) {
        rollIdx = i;
        break;
      }
    }
    if (rollIdx < 0) return null;

    const rollPath = ctrl.path.slice(0, rollIdx * 2);
    const rollNode = nodeList[rollIdx];
    const currentBranchId = nodeList[rollIdx + 1]?.id;

    const dfs = (node: Tree.Node, path: string): string | null => {
      const et = node.children.find(c => c.uci === 'endturn');
      if (et) return node.fen.split(' ')[0] === targetBoard ? path + et.id : null;
      for (const child of node.children) {
        const result = dfs(child, path + child.id);
        if (result) return result;
      }
      return null;
    };

    for (const child of rollNode.children) {
      if (child.id === currentBranchId) continue;
      const result = dfs(child, rollPath + child.id);
      if (result) return { endTurnPath: result, branchRootPath: rollPath + currentBranchId! };
    }
    return null;
  };

  const sendEndTurn = (deleteNodeIfSameTurnPlayed = false) => {
    const sameTurnPlayed = findEquivalentEndTurn();
    if (sameTurnPlayed) {
      const pathToDelete =
        deleteNodeIfSameTurnPlayed && sameTurnPlayed.branchRootPath !== null
          ? sameTurnPlayed.branchRootPath
          : undefined;
      ctrl.userJumpIfCan(sameTurnPlayed.endTurnPath);
      if (pathToDelete !== undefined) {
        ctrl.tree.deleteNodeAt(pathToDelete);
        if (ctrl.study) ctrl.study.deleteNode(pathToDelete);
        ctrl.redraw();
      }
      triggerRoll();
      return;
    }
    const endTurn: AnaEndTurn = {
      variant: ctrl.data.game.variant.key,
      lib: ctrl.data.game.variant.lib,
      fen: ctrl.node.fen,
      path: ctrl.path,
    };
    if (ctrl.practice) ctrl.practice.onUserMove();
    endTurnSent = true;
    ctrl.socket.sendAnaEndTurn(endTurn);
    ctrl.redraw();
  };

  const maybeAutoRollOnLoad = (): void => {
    if (ctrl.path !== treePath.root) return;
    const fenParts = ctrl.node.fen.split(' ');
    if (fenParts.length >= 3 && fenParts[1] === '-' && fenParts[2] === '-') triggerRoll();
  };

  const maybeAutoEndTurn = (node: Tree.Node): void => {
    const isRollNode = diceRollUci.test(node.uci ?? '');
    if (isRollNode) rollPending = false;

    const fenParts = node.fen.split(' ');
    if (fenParts.length < 3) return;
    const unusedDice = fenParts[1];
    const usedDice = fenParts[2];

    if (node.uci === 'endturn') {
      if (endTurnSent || !ctrl.study) {
        endTurnSent = false;
        if (!ctrl.outcome(node)) triggerRoll();
      }
      return;
    }
    const allDiceConsumed = unusedDice === '-' && usedDice !== '-';
    const noMovesAfterRoll =
      unusedDice !== '-' &&
      node.lifts != null &&
      (!node.dests || node.dests === '') &&
      (!node.dropsByRole || node.dropsByRole === '') &&
      (!node.lifts || node.lifts === '');
    if (allDiceConsumed) {
      if (!ctrl.study || actionSent) {
        actionSent = false;
        sendEndTurn(true);
      }
    } else if (noMovesAfterRoll) {
      if (!ctrl.study || rollSent || actionSent) {
        rollSent = false;
        actionSent = false;
        sendEndTurn();
      }
    } else if (isRollNode) {
      if (node.lifts == null) pendingNoMovesCheck = true;
      rollSent = false;
    }
  };

  ctrl.controlConfig.suppressPremove = () => true;

  ctrl.controlConfig.onInit = () => setTimeout(() => maybeAutoRollOnLoad(), 0);
  ctrl.controlConfig.onAfterAddNode = (node: Tree.Node) => maybeAutoEndTurn(node);
  ctrl.controlConfig.onAfterAddDests = () => {
    if (!pendingNoMovesCheck) return;
    pendingNoMovesCheck = false;
    const node = ctrl.node;
    const fenParts = node.fen.split(' ');
    if (fenParts.length < 3 || fenParts[1] === '-') return;
    const noMovesAfterRoll =
      node.lifts != null &&
      (!node.dests || node.dests === '') &&
      (!node.dropsByRole || node.dropsByRole === '') &&
      (!node.lifts || node.lifts === '');
    if (noMovesAfterRoll) sendEndTurn();
  };
  ctrl.controlConfig.onUserAction = () => {
    actionSent = true;
  };

  ctrl.controlConfig.afterJump = () => {
    dicePickerActive = false;
    die1Pick = undefined;
    die2Pick = undefined;
    diceWasPreFilled = false;
    pendingNoMovesCheck = false;

    if (rollPending) return;

    // Always show picker at any pre-roll position (root or endturn node with - - FEN).
    // This makes afterJump idempotent: study re-jumps and "last"/"first" buttons all
    // correctly restore the picker without relying on endTurnSent sequencing.
    const fenParts = ctrl.node.fen.split(' ');
    if (fenParts.length < 3 || fenParts[1] !== '-' || fenParts[2] !== '-') return;
    if (ctrl.outcome()) return;
    const existingRoll = ctrl.node.children.find(c => diceRollUci.test(c.uci ?? ''));
    if (existingRoll) {
      const parts = (existingRoll.uci ?? '').split('/').map(Number);
      die1Pick = parts[0] ?? undefined;
      die2Pick = parts[1] ?? undefined;
      diceWasPreFilled = true;
    }
    dicePickerActive = true;
  };

  ctrl.controlConfig.onStepFailure = () => {
    rollPending = false;
    rollSent = false;
    // Only show picker if we're at a pre-roll state (both dice fields empty).
    // If there are dice already (roll node), the endturn failed but pieces can still move.
    const fenParts = ctrl.node.fen.split(' ');
    if (fenParts.length >= 3 && fenParts[1] === '-' && fenParts[2] === '-') triggerRoll();
  };

  ctrl.controlConfig.mutateCgOpts = (node, config) => {
    const playerIndex = ctrl.turnPlayerIndex() as CgPlayerIndex;
    const variantKey = ctrl.data.game.variant.key;

    config.myPlayerIndex = playerIndex;

    if (diceRollUci.test(node.uci ?? '') && ctrl.path !== lastRollPath) {
      areDiceDescending = true;
      lastRollPath = ctrl.path;
    }

    const rawDice = readDice(node.fen, variantKey, false, areDiceDescending) as {
      value: number;
      isAvailable: boolean;
    }[];
    const dice = ctrl.outcome(node) ? rawDice.map(d => ({ ...d, isAvailable: false })) : rawDice;
    config.dice = dice;

    const activeDiceValue = dice.find(d => d.isAvailable)?.value;
    if (activeDiceValue !== undefined && config.movable?.dests) {
      const sorted = new Map<string, string[]>();
      (config.movable.dests as Map<string, string[]>).forEach((destKeys, orig) => {
        sorted.set(
          orig,
          [...destKeys].sort(
            (a, b) =>
              Math.abs(CgBackgammonPosDiff(orig as CgKey, a as CgKey) - activeDiceValue) -
              Math.abs(CgBackgammonPosDiff(orig as CgKey, b as CgKey) - activeDiceValue),
          ),
        );
      });
      config.movable.dests = sorted as unknown as typeof config.movable.dests;
    }

    const rawLifts: string[] = (node.lifts?.match(/[a-l][1-2]/g) as string[]) ?? [];
    (config as Record<string, unknown>).liftable = { liftDests: rawLifts };
  };

  ctrl.controlConfig.cgHooks = {
    onSelectDice: (dice: unknown[]) => {
      const typedDice = dice as { value: number; isAvailable: boolean }[];
      if (typedDice.length > 0) {
        const allConsumed = !typedDice.some(d => d.isAvailable);
        const noMovesAvailable =
          (!ctrl.node.dests || ctrl.node.dests === '') &&
          (!ctrl.node.dropsByRole || ctrl.node.dropsByRole === '') &&
          (!ctrl.node.lifts || ctrl.node.lifts === '');
        if (allConsumed || noMovesAvailable) {
          sendEndTurn();
          return;
        }
      }
      areDiceDescending = !areDiceDescending;
      ctrl.reset();
    },
    onButtonClick: (button: string) => {
      if (button === 'roll') triggerRoll();
    },
    onUserLift: (dest: string) => {
      playstrategy.sound.play('move');
      ctrl.sendAnaLift(dest as CgKey);
    },
  };

  ctrl.controlConfig.needsDestsRefetch = (node: Tree.Node) => node.lifts == null;
  ctrl.controlConfig.needsFullRedrawAfterGround = () => true;

  ctrl.controlConfig.showDropDestsInDropMode = () => false;

  ctrl.controlConfig.dropSoundOverride = (_piece: CgPiece, _pos: CgKey, captured?: CgPiece) =>
    captured ? 'capture' : undefined;

  ctrl.controlConfig.nodeSoundOverride = (node: Tree.Node) => {
    if (diceRollUci.test(node.uci ?? '')) {
      const fenParts = node.fen.split(' ');
      const noPlay =
        fenParts.length >= 3 &&
        fenParts[1] !== '-' &&
        (!node.dests || node.dests === '') &&
        (!node.dropsByRole || node.dropsByRole === '') &&
        (!node.lifts || node.lifts === '');
      return noPlay ? false : 'diceRoll';
    }
    if (node.uci === 'endturn') return false;
    return undefined;
  };

  ctrl.controlConfig.renderBoardOverlay = () => {
    if (!dicePickerActive) return null;
    const playerIndex = ctrl.turnPlayerIndex() as CgPlayerIndex;

    const renderGroup = (die: 1 | 2, currentPick: number | null | undefined) =>
      h(`cg-dice.${playerIndex}.die-${die}`, [
        h(`dice.random${currentPick === null ? '.selected' : ''}`, {
          hook: bind('click', e => {
            e.stopPropagation();
            onDiePick(die, null);
          }),
        }),
        ...[1, 2, 3].map(val =>
          h(`dice.${diceNames[val - 1]}${currentPick === val ? '.selected' : ''}`, {
            hook: bind('click', e => {
              e.stopPropagation();
              onDiePick(die, val);
            }),
          }),
        ),
        h('div.dice-picker__spacer'),
        ...[4, 5, 6].map(val =>
          h(`dice.${diceNames[val - 1]}${currentPick === val ? '.selected' : ''}`, {
            hook: bind('click', e => {
              e.stopPropagation();
              onDiePick(die, val);
            }),
          }),
        ),
      ]);

    return h('div.dice-picker', [renderGroup(1, die1Pick), renderGroup(2, die2Pick)]);
  };

  ctrl.controlConfig.isNodeCommentable = (node: Tree.Node) => diceRollUci.test(node.uci ?? '');
  ctrl.controlConfig.isNodeAnnotatable = (node: Tree.Node) =>
    !diceRollUci.test(node.uci ?? '') && node.uci !== 'endturn';

  ctrl.controlConfig.redirectJumpPath = (path: string) => {
    const node = ctrl.tree.nodeAtPath(path);
    if (!node || !diceRollUci.test(node.uci ?? '')) return path;
    // Advance to the last piece-move node before endTurn
    let advancedPath = path;
    let current: Tree.Node = node;
    while (current.children.length > 0) {
      const firstChild = current.children[0];
      if (firstChild.uci === 'endturn') break;
      advancedPath += firstChild.id;
      current = firstChild;
    }
    return advancedPath;
  };
};
