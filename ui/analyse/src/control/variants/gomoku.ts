import { h } from 'snabbdom';
import type AnalyseCtrl from '../../ctrl';
import { gomoku } from 'stratutils';

export const configure = (ctrl: AnalyseCtrl): void => {
  ctrl.controlConfig.showDropDestsInDropMode = () => false;
  ctrl.controlConfig.cgHooks = { onCancelDropMode: () => ctrl.redraw() };

  // the colour of the next stone alternates, and in the opening one seat drops both colours
  ctrl.controlConfig.dropModePiece = node => gomoku.dropPiece(node.fen, node.playerIndex);

  const offered = (action: 'swap' | 'swap2'): boolean =>
    !ctrl.outcome() && (action === 'swap' ? gomoku.canSwap(ctrl.node.fen) : gomoku.canSwap2(ctrl.node.fen));

  ctrl.controlConfig.handleControlAction = action => {
    if (action !== 'swap' && action !== 'swap2') return false;
    if (offered(action)) ctrl.sendSwap(action);
    return true;
  };

  const swapButton = (action: 'swap' | 'swap2', title: string, label: string) =>
    h('button.fbt.swap', { attrs: { title, 'data-act': action, disabled: !offered(action) } }, label);

  ctrl.controlConfig.renderControlActions = () => [
    swapButton('swap', 'Swap colours', 'Swap'),
    swapButton('swap2', 'Swap colours and place 2 stones', 'Swap 2'),
  ];
};
