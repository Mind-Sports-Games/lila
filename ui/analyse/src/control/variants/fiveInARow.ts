import { h } from 'snabbdom';
import type AnalyseCtrl from '../../ctrl';
import { fiveInARow } from 'stratutils';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.showDropDestsInDropMode = () => false;

  // renju: white picks one of the stones black offered by clicking it
  ctrl.controlConfig.cgHooks = {
    onCancelDropMode: () => ctrl.redraw(),
    onSelect: key => {
      if (picking() && ctrl.chessground.state.pieces.get(key as Key)?.role === 'o-piece') ctrl.sendPick(key as Key);
    },
  };

  const picking = (): boolean => !ctrl.embed && !ctrl.outcome() && fiveInARow.canPick(ctrl.node.fen);

  ctrl.controlConfig.mutateCgOpts = (_node, config) => {
    config.selectOnly = picking();
  };

  // the colour of the next stone alternates, and in the opening one seat drops both colours
  ctrl.controlConfig.dropModePiece = node => fiveInARow.dropPiece(node.fen, node.playerIndex);

  const offered = (action: fiveInARow.OpeningAction): boolean =>
    !ctrl.outcome() && fiveInARow.offers(action, ctrl.node.fen);

  ctrl.controlConfig.handleControlAction = action => {
    if (action !== 'swap' && action !== 'swap2' && action !== 'offer10') return false;
    if (offered(action)) ctrl.sendSwap(action);
    return true;
  };

  const swapButton = (action: fiveInARow.OpeningAction, title: string, label: string) =>
    h('button.fbt.swap', { attrs: { title, 'data-act': action, disabled: !offered(action) } }, label);

  ctrl.controlConfig.renderControlActions = () =>
    ctrl.data.game.variant.key === 'renju'
      ? [
          swapButton('swap', 'Swap colours', 'Swap'),
          swapButton('offer10', 'Offer 10 fifth stones for white to pick from', 'Offer 10'),
        ]
      : [swapButton('swap', 'Swap colours', 'Swap'), swapButton('swap2', 'Swap colours and place 2 stones', 'Swap 2')];
};
