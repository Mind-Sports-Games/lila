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

  type CgCubeAction = 'offer' | 'reject' | 'accept';
  const readCubeActionsFromFen = (fen: string): CgCubeAction[] => {
    const parts = fen.split(' ');
    if (parts.length < 8) return [];
    if (parts[1] !== '-' || parts[2] !== '-') return [];
    const player = parts[3];
    const cube = parts[6];
    if (cube === '-') return [];
    if (cube === '0') return ['offer'];
    if (cube.length < 2) return [];
    const letter = cube[1];
    if (letter === 'w' || letter === 'b') {
      if (cube.endsWith('x')) return [];
      return player !== letter ? ['reject', 'accept'] : [];
    }
    const ownerLetter = letter.toLowerCase();
    return player === ownerLetter ? ['offer'] : [];
  };

  const doSendRoll = (dice: number[]) => {
    const sortedValues = [...dice].sort((a, b) => a - b).join('/');
    const existingMatch = ctrl.node.children.find(c => {
      if (!diceRollUci.test(c.uci ?? '')) return false;
      const childSorted = (c.uci ?? '').split('/').map(Number).sort((a, b) => a - b).join('/');
      return childSorted === sortedValues;
    });
    if (existingMatch) {
      ctrl.userJumpIfCan(ctrl.path + existingMatch.id);
      ctrl.redraw();
      return;
    }
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

  const triggerRoll = (fromUserRoll = false) => {
    if (rollPending) return;
    if (!fromUserRoll && ctrl.node.uci !== 'cubey' && readCubeActionsFromFen(ctrl.node.fen).length > 0) return;
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
    ctrl.reset();
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
      let d1 = die1Pick !== null ? (die1Pick as number) : Math.ceil(Math.random() * 6);
      let d2 = die2Pick !== null ? (die2Pick as number) : Math.ceil(Math.random() * 6);
      // Opening roll cannot be a double — mirrors the Study page server-side fallback
      if (ctrl.path === treePath.root && d1 === d2) {
        do {
          d1 = Math.ceil(Math.random() * 6);
          d2 = Math.ceil(Math.random() * 6);
        } while (d1 === d2);
      }
      doSendRoll([d1, d2].sort((a, b) => b - a));
    } else {
      ctrl.redraw();
    }
  };

  const sendCubeAction = (uci: 'cubeo' | 'cubey' | 'cuben') => {
    const existing = ctrl.node.children.find(c => c.uci === uci);
    if (existing) {
      ctrl.userJumpIfCan(ctrl.path + existing.id);
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
    if (node.uci === 'cubey') {
      if (!ctrl.study && !ctrl.outcome(node)) triggerRoll();
      return;
    }
    if (node.uci === 'cubeo' || node.uci === 'cuben') return;
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
    const wasDicePickerActive = dicePickerActive;
    dicePickerActive = false;
    die1Pick = undefined;
    die2Pick = undefined;
    diceWasPreFilled = false;
    pendingNoMovesCheck = false;

    // If we landed on a roll node, the roll is resolved regardless of whether addNode
    // called onAfterAddNode (it skips it for duplicate nodes, leaving rollPending dirty).
    if (diceRollUci.test(ctrl.node.uci ?? '')) rollPending = false;

    if (rollPending) return;

    // Always show picker at any pre-roll position (root or endturn node with - - FEN).
    // This makes afterJump idempotent: study re-jumps and "last"/"first" buttons all
    // correctly restore the picker without relying on endTurnSent sequencing.
    const fenParts = ctrl.node.fen.split(' ');
    if (fenParts.length < 3 || fenParts[1] !== '-' || fenParts[2] !== '-') return;
    if (ctrl.outcome()) return;
    if (ctrl.node.uci !== 'cubey' && readCubeActionsFromFen(ctrl.node.fen).length > 0) {
      if (wasDicePickerActive) ctrl.reset();
      return;
    }
    triggerRoll();
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
    (config as Record<string, unknown>).cubeActions = dicePickerActive ? [] : readCubeActionsFromFen(node.fen);
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
      if (button === 'roll') triggerRoll(true);
      else if (button === 'double') sendCubeAction('cubeo');
      else if (button === 'take') sendCubeAction('cubey');
      else if (button === 'drop') sendCubeAction('cuben');
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
    if (!dicePickerActive || ctrl.embed) return null;
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

  ctrl.controlConfig.getActivePath = () => {
    if (dicePickerActive) {
      const existingRoll = ctrl.node.children.find(c => diceRollUci.test(c.uci ?? ''));
      if (existingRoll) return ctrl.path + existingRoll.id;
      return null;
    }
    if (['cubey', 'cuben'].includes(ctrl.node.uci ?? '')) return undefined;
    const cubeActions = readCubeActionsFromFen(ctrl.node.fen);
    if (cubeActions.includes('offer')) {
      const cubeoChild = ctrl.node.children.find(c => c.uci === 'cubeo');
      if (cubeoChild) return ctrl.path + cubeoChild.id;
      const rollChild = ctrl.node.children.find(c => diceRollUci.test(c.uci ?? ''));
      if (rollChild) return ctrl.path + rollChild.id;
      return null;
    }
    if (cubeActions.includes('accept') || cubeActions.includes('reject')) {
      const takeChild = ctrl.node.children.find(c => c.uci === 'cubey');
      const dropChild = ctrl.node.children.find(c => c.uci === 'cuben');
      if (takeChild && !dropChild) return ctrl.path + takeChild.id;
      if (dropChild && !takeChild) return ctrl.path + dropChild.id;
      return null;
    }
    return undefined;
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
