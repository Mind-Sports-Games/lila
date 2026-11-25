import { VNode } from 'snabbdom';
import { getScore, score } from 'stratutils';
import { Controller } from '../interfaces';

export default function renderScore(ctrl: Controller): (VNode | undefined)[] {
  const game = ctrl.getData().game;
  if (!game.variant.hasGameScore) return [];
  const variantKey = game.variant.key,
    fen = ctrl.vm.node.fen,
    bottom = ctrl.vm.pov,
    top = bottom === 'p1' ? 'p2' : 'p1',
    capturesTitle = ctrl.trans.noarg('captures');
  const scoreOf = (playerIndex: PlayerIndex): number => getScore(variantKey, fen, playerIndex) ?? 0;
  const scorePlayer = (playerIndex: PlayerIndex): score.ScorePlayer => {
    const playerColor = ctrl.vm.playerColors[playerIndex === 'p1' ? 0 : 1];
    return { name: playerColor.charAt(0).toUpperCase() + playerColor.slice(1) };
  };
  return [
    score.renderPlayerScore(scoreOf(top), 'top', top, variantKey, fen, capturesTitle),
    score.renderPlayerScoreNames(scorePlayer('p1'), scorePlayer('p2'), bottom === 'p2'),
    score.renderPlayerScore(scoreOf(bottom), 'bottom', bottom, variantKey, fen, capturesTitle),
  ];
}
