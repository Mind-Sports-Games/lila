import { h, VNode } from 'snabbdom';
import { ops as treeOps } from 'tree';
import { TagArray } from './interfaces';
import renderClocks from '../clocks';
import AnalyseCtrl from '../ctrl';
import { isFinished, findTag, resultOf } from './studyChapters';

interface PlayerNames {
  p1: string;
  p2: string;
}

export default function (ctrl: AnalyseCtrl): VNode[] | undefined {
  const study = ctrl.study;
  if (!study || ctrl.embed) return;
  const tags = study.data.chapter.tags,
    playerNames = {
      p1: findTag(tags, 'p1')!,
      p2: findTag(tags, 'p2')!,
    };
  if (!playerNames.p1 && !playerNames.p2 && !treeOps.findInMainline(ctrl.tree.root, n => !!n.clock)) return;
  const clocks = renderClocks(ctrl),
    ticking = !isFinished(study.data.chapter) && ctrl.turnPlayerIndex();
  return (['p1', 'p2'] as PlayerIndex[]).map(playerIndex =>
    renderPlayer(
      tags,
      clocks,
      playerNames,
      playerIndex,
      ticking === playerIndex,
      ctrl.bottomPlayerIndex() !== playerIndex
    )
  );
}

function renderPlayer(
  tags: TagArray[],
  clocks: [VNode, VNode] | undefined,
  playerNames: PlayerNames,
  playerIndex: PlayerIndex,
  ticking: boolean,
  top: boolean
): VNode {
  const title = findTag(tags, `${playerIndex}title`),
    elo = findTag(tags, `${playerIndex}elo`),
    result = resultOf(tags, playerIndex === 'p1');
  return h(
    `div.study__player.study__player-${top ? 'top' : 'bot'}`,
    {
      class: { ticking },
    },
    [
      h('div.left', [
        result && h('span.result', result),
        h('span.info', [
          title && h('span.utitle', title == 'BOT' ? { attrs: { 'data-bot': true } } : {}, title + ' '),
          h('span.name', playerNames[playerIndex]),
          elo && h('span.elo', elo),
        ]),
      ]),
      clocks && clocks[playerIndex === 'p1' ? 0 : 1],
    ]
  );
}
