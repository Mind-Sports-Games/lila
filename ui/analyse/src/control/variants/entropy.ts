import { h } from 'snabbdom';
import type AnalyseCtrl from '../../ctrl';
import { entropy } from 'stratutils';
import { storedProp } from 'common/storage';

export const configure = (ctrl: AnalyseCtrl): void => {
  // Chaos drops the counter it drew; the pocket shows the bag until it has drawn one
  ctrl.controlConfig.dropModePiece = node => {
    const role = entropy.counterInPocket(node.fen);
    return role ? { playerIndex: node.playerIndex, role } : undefined;
  };

  ctrl.controlConfig.cgHooks = { onCancelDropMode: () => ctrl.redraw() };

  // the account preference sets the button's opening position; from then on the button decides, and
  // unlike a live board it annotates a part-filled board too, where the patterns drawn are the ones
  // the guaranteed score has already banked
  const showPatterns = storedProp('entropy.patterns', !!ctrl.data.pref.entropyPatterns);
  const patternMode = () => (showPatterns() ? 'always' : 'never');

  ctrl.controlConfig.mutateCgOpts = (_, config) => {
    config.showPatterns = patternMode();
  };

  ctrl.controlConfig.handleControlAction = action => {
    if (action !== 'patterns') return false;
    showPatterns(!showPatterns());
    ctrl.withCg(cg => cg.set({ showPatterns: patternMode() }));
    return true;
  };

  // Order may pass instead of moving
  ctrl.controlConfig.renderControlActions = () => [
    h('button.fbt', {
      attrs: { title: 'Show scoring patterns', 'data-act': 'patterns', 'data-icon': '^' },
      class: { active: showPatterns() },
    }),
    entropy.isOrderTurn(ctrl.node.fen) && !ctrl.outcome()
      ? h('button.fbt', {
          attrs: { title: 'Pass', 'data-act': 'pass', 'data-icon': '\ue91b' },
        })
      : null,
  ];
};
