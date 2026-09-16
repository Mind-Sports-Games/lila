import { h } from 'snabbdom';
import type AnalyseCtrl from '../../ctrl';
import { entropy } from 'stratutils';

export const configure = (ctrl: AnalyseCtrl): void => {
  // Chaos drops the counter it drew; the pocket shows the bag until it has drawn one
  ctrl.controlConfig.dropModePiece = node => {
    const role = entropy.counterInPocket(node.fen);
    return role ? { playerIndex: node.playerIndex, role } : undefined;
  };

  ctrl.controlConfig.cgHooks = { onCancelDropMode: () => ctrl.redraw() };

  // Order may pass instead of moving
  ctrl.controlConfig.renderControlActions = () =>
    entropy.isOrderTurn(ctrl.node.fen) && !ctrl.outcome()
      ? h('button.fbt', {
          attrs: { title: 'Pass', 'data-act': 'pass', 'data-icon': '\ue91b' },
        })
      : null;
};
