import { h } from 'snabbdom';
import type AnalyseCtrl from '../../ctrl';
import { fiveInARow } from 'stratutils';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.showDropDestsInDropMode = () => false;

  ctrl.controlConfig.cgHooks = {
    onCancelDropMode: () => ctrl.redraw(),
  };

  // the colour of the next stone alternates, and in the opening one seat drops both colours; in renju
  // white drops black's fifth on one of the offered stones
  ctrl.controlConfig.dropModePiece = node => fiveInARow.dropPiece(node.fen, node.playerIndex);

  const offered = (action: fiveInARow.OpeningAction): boolean =>
    !ctrl.outcome() && fiveInARow.offers(action, ctrl.node.fen);

  ctrl.controlConfig.handleControlAction = action => {
    if (action !== 'swap' && action !== 'swap2' && action !== 'offer10') return false;
    if (offered(action)) ctrl.sendSwap(action);
    return true;
  };

  const swapButton = (action: fiveInARow.OpeningAction, title: string, label: string) =>
    h(
      'button.fbt.swap',
      { attrs: { title: ctrl.trans.noarg(title), 'data-act': action, disabled: !offered(action) } },
      ctrl.trans.noarg(label),
    );

  ctrl.controlConfig.renderControlActions = () =>
    ctrl.data.game.variant.key === 'renju'
      ? [swapButton('swap', 'swapColours', 'swap'), swapButton('offer10', 'offerTenFifthStones', 'offerTen')]
      : [swapButton('swap', 'swapColours', 'swap'), swapButton('swap2', 'swapColoursAndPlaceTwoStones', 'swapTwo')];
};
