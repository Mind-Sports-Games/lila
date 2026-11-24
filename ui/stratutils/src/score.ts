import { h, VNode } from 'snabbdom';
import { seatColor } from './fiveInARow';

export type ScorePosition = 'top' | 'bottom';

export interface ScorePlayer {
  name: string;
  userId?: string;
}

export function renderPlayerScore(
  score: number,
  position: ScorePosition,
  playerIndex: string,
  variantKey: VariantKey,
  fen: string,
  capturesTitle: string,
): VNode | undefined {
  const defaultMancalaRole = 's';
  const children: VNode[] = [];
  if (variantKey === 'togyzkumalak' || variantKey === 'bestemshe') {
    let part1Score = 0;
    let part2Score = 0;
    let part2Offset = false;
    if (score <= 10) {
      part1Score = score;
      part2Score = 0;
    } else if (score <= 20) {
      part1Score = 10;
      part2Score = score - 10;
    } else {
      part1Score = Math.min((score % 20) + 10, 20);
      part2Score = Math.max(score % 20, 10);
      if (part2Score === 10) part2Offset = true;
    }

    const pieceClassPart1 = `piece.${defaultMancalaRole}${part1Score.toString()}-piece.part1.`;
    const pieceClassPart2 = `piece.${defaultMancalaRole}${part2Score.toString()}${part2Offset ? 'o' : ''}-piece.part2.`;

    children.push(h(pieceClassPart1 + playerIndex));
    if (score > 10) {
      children.push(h(pieceClassPart2 + playerIndex));
    }
    return h('div.game-score.game-score-' + position, { attrs: { 'data-score': score } }, children);
  } else if (variantKey === 'go9x9' || variantKey === 'go13x13' || variantKey === 'go19x19') {
    children.push(h('piece.p-piece.' + playerIndex, { attrs: { 'data-score': score } }));
    return h('div.game-score.game-score-top' + '.' + playerIndex, children);
  } else if (variantKey === 'pente') {
    const stone = seatColor(fen, playerIndex as PlayerIndex) === 'black' ? 'b-piece' : 'w-piece';
    children.push(h(`piece.${stone}.${playerIndex}`, { attrs: { 'data-score': score } }));
    return h(
      'div.game-score.game-score-top.' + playerIndex + '.captures',
      { attrs: { title: capturesTitle } },
      children,
    );
  } else if (variantKey === 'backgammon' || variantKey === 'hyper' || variantKey === 'nackgammon') {
    for (let i = 0; i < score; i++) {
      children.push(h('piece.side-piece.' + playerIndex + (i === 0 ? ' first' : '')));
    }
    return h('div.game-score.game-score-' + position, { attrs: { 'data-score': score } }, children);
  } else if (variantKey === 'oware') {
    const pieceClass = `piece.${defaultMancalaRole}${score.toString()}-piece.`;
    children.push(h(pieceClass + playerIndex, { attrs: { 'data-score': score } }));
    return h('div.game-score.game-score-' + position + '.' + playerIndex, children);
  } else if (variantKey === 'abalone') {
    const opp = playerIndex === 'p1' ? 'p2' : 'p1';

    children.push(h(`piece.${score > 0 ? 's-piece' : 'hole-piece'}.slot-top.${opp}`));
    children.push(h(`piece.${score > 1 ? 's-piece' : 'hole-piece'}.slot-mid-left.${opp}`));
    children.push(h(`piece.${score > 2 ? 's-piece' : 'hole-piece'}.slot-mid-right.${opp}`));
    children.push(h(`piece.${score > 3 ? 's-piece' : 'hole-piece'}.slot-bot-left.${opp}`));
    children.push(h(`piece.${score > 4 ? 's-piece' : 'hole-piece'}.slot-bot-mid.${opp}`));
    children.push(h(`piece.${score > 5 ? 's-piece' : 'hole-piece'}.slot-bot-right.${opp}`));

    return h('div.game-score.game-score-top' + '.' + playerIndex, { attrs: { 'data-score': score } }, children);
  } else if (variantKey === 'grandabalone') {
    const opp = playerIndex === 'p1' ? 'p2' : 'p1';

    children.push(h(`piece.${score > 0 ? 's-piece' : 'hole-piece'}.slot-top.${opp}`));
    children.push(h(`piece.${score > 1 ? 's-piece' : 'hole-piece'}.slot-top-left.${opp}`));
    children.push(h(`piece.${score > 2 ? 's-piece' : 'hole-piece'}.slot-top-right.${opp}`));
    children.push(h(`piece.${score > 3 ? 's-piece' : 'hole-piece'}.slot-mid-left.${opp}`));
    children.push(h(`piece.${score > 4 ? 's-piece' : 'hole-piece'}.slot-mid-mid.${opp}`));
    children.push(h(`piece.${score > 5 ? 's-piece' : 'hole-piece'}.slot-mid-right.${opp}`));
    children.push(h(`piece.${score > 6 ? 's-piece' : 'hole-piece'}.slot-bot-left.${opp}`));
    children.push(h(`piece.${score > 7 ? 's-piece' : 'hole-piece'}.slot-bot-mid-left.${opp}`));
    children.push(h(`piece.${score > 8 ? 's-piece' : 'hole-piece'}.slot-bot-mid-right.${opp}`));
    children.push(h(`piece.${score > 9 ? 's-piece' : 'hole-piece'}.slot-bot-right.${opp}`));

    return h('div.game-score.game-score-top' + '.' + playerIndex, { attrs: { 'data-score': score } }, children);
  } else if (variantKey === 'threeCheck' || variantKey === 'fiveCheck') {
    children.push(h('piece.k-piece.' + playerIndex, { attrs: { 'data-score': score } }));
    return h('div.game-score.game-score-top' + '.' + playerIndex, children);
  } else {
    //flipello variants
    const pieceClass = 'piece.p-piece.';
    children.push(h(pieceClass + playerIndex, { attrs: { 'data-score': score } }));
    return h('div.game-score.game-score-top' + '.' + playerIndex, children);
  }
}

export function renderPlayerScoreNames(p1: ScorePlayer, p2: ScorePlayer, flipped: boolean): VNode {
  const flippedCss = flipped ? '.flipped' : '';
  const nameBox = (playerIndex: PlayerIndex, player: ScorePlayer): VNode =>
    player.userId
      ? h(
          `div.game-score-name.${playerIndex}.text.player${flippedCss}`,
          h('a.user-link.ulpt', { attrs: { href: `/@/${player.userId}` } }, player.name),
        )
      : h(`div.game-score-name.${playerIndex}.text${flippedCss}`, player.name);
  return h('div.game-score-names', [nameBox('p1', p1), h('div.game-score-name.vs.text', 'vs'), nameBox('p2', p2)]);
}
