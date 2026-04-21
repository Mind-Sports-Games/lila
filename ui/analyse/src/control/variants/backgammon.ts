import type AnalyseCtrl from '../../ctrl';
import { path as treePath } from 'tree';
import { backgammonPosDiff, orientationForBackgammon, oppositeOrientationForBackgammon } from 'chessground/util';
import * as cg from 'chessground/types';
import * as stratUtils from 'stratutils';
import type { AnaRoll, AnaEndTurn } from '../../study/interfaces';

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

  ctrl.controlConfig.getOrientation = () => {
    const c = ctrl.data.player.playerIndex as cg.PlayerIndex;
    return ctrl.flipped ? oppositeOrientationForBackgammon(c) : orientationForBackgammon(c);
  };

  let areDiceDescending = true;
  let lastRollPath = '';
  let rollPending = false;
  let rollSent = false;
  let actionSent = false;
  let endTurnSent = false;

  const diceRollUci = /^[1-6](\/[1-6])+$/;

  const sendRollDice = () => {
    if (rollPending) return;
    // fix 2 successive rolls: If another session already rolled, navigate to the node instead of creating a duplicate
    const existingRoll = ctrl.node.children.find(c => diceRollUci.test(c.uci ?? ''));
    if (existingRoll) {
      ctrl.userJumpIfCan(ctrl.path + existingRoll.id);
      return;
    }
    const roll: AnaRoll = {
      variant: ctrl.data.game.variant.key,
      lib: ctrl.data.game.variant.lib,
      fen: ctrl.node.fen,
      path: ctrl.path,
    };
    if (ctrl.practice) ctrl.practice.onUserMove();
    rollPending = true;
    rollSent = true;
    ctrl.socket.sendAnaRoll(roll);
    ctrl.redraw();
  };

  const sendEndTurn = () => {
    const existingEndTurn = ctrl.node.children.find(c => c.uci === 'endturn');
    if (existingEndTurn) {
      ctrl.userJumpIfCan(ctrl.path + existingEndTurn.id);
      sendRollDice();
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
    // Auto-roll only at chapter root with no existing moves and no dice in FEN.
    if (ctrl.path !== treePath.root || ctrl.node.children.length > 0) return;
    const fenParts = ctrl.node.fen.split(' ');
    if (fenParts.length >= 3 && fenParts[1] === '-' && fenParts[2] === '-') sendRollDice();
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
        sendRollDice();
      }
      return;
    }
    const allDiceConsumed = unusedDice === '-' && usedDice !== '-';
    const noMovesAfterRoll =
      unusedDice !== '-' &&
      (!node.dests || node.dests === '') &&
      (!node.dropsByRole || node.dropsByRole === '') &&
      (!node.lifts || node.lifts === '');
    if (allDiceConsumed) {
      if (!ctrl.study || actionSent) {
        actionSent = false;
        sendEndTurn();
      }
    } else if (noMovesAfterRoll) {
      if (!ctrl.study || rollSent || actionSent) {
        rollSent = false;
        actionSent = false;
        sendEndTurn();
      }
    } else if (isRollNode) {
      rollSent = false;
    }
  };

  ctrl.controlConfig.suppressPremove = () => true;

  ctrl.controlConfig.onInit = () => setTimeout(() => maybeAutoRollOnLoad(), 0);
  ctrl.controlConfig.onAfterAddNode = (node: Tree.Node) => maybeAutoEndTurn(node);
  ctrl.controlConfig.onUserAction = () => {
    actionSent = true;
  };

  ctrl.controlConfig.mutateCgOpts = (node, config) => {
    const playerIndex = ctrl.turnPlayerIndex() as cg.PlayerIndex;
    const variantKey = ctrl.data.game.variant.key;

    config.myPlayerIndex = playerIndex;

    if (diceRollUci.test(node.uci ?? '') && ctrl.path !== lastRollPath) {
      areDiceDescending = true;
      lastRollPath = ctrl.path;
    }

    const dice = stratUtils.readDice(node.fen, variantKey, false, areDiceDescending);
    config.dice = dice;

    const activeDiceValue = (dice as { value: number; isAvailable: boolean }[]).find(d => d.isAvailable)?.value;
    if (activeDiceValue !== undefined && config.movable?.dests) {
      const sorted = new Map<string, string[]>();
      (config.movable.dests as Map<string, string[]>).forEach((destKeys, orig) => {
        sorted.set(
          orig,
          [...destKeys].sort(
            (a, b) =>
              Math.abs(backgammonPosDiff(orig as cg.Key, a as cg.Key) - activeDiceValue) -
              Math.abs(backgammonPosDiff(orig as cg.Key, b as cg.Key) - activeDiceValue),
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
      if (button === 'roll') sendRollDice();
    },
    onUserLift: (dest: string) => {
      playstrategy.sound.play('move');
      ctrl.sendAnaLift(dest as cg.Key);
    },
  };

  ctrl.controlConfig.needsDestsRefetch = (node: Tree.Node) => node.lifts == null;
  ctrl.controlConfig.needsFullRedrawAfterGround = () => true;

  ctrl.controlConfig.showDropDestsInDropMode = () => false;

  ctrl.controlConfig.nodeSoundOverride = (node: Tree.Node) => {
    if (diceRollUci.test(node.uci ?? '')) return 'diceRoll';
    if (node.uci === 'endturn') return false;
    return undefined;
  };
};
