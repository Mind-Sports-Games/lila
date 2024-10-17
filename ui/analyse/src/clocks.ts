import { h, VNode } from 'snabbdom';
import AnalyseCtrl from './ctrl';
import { isFinished } from './study/studyChapters';
import { path as treePath } from 'tree';

export default function renderClocks(ctrl: AnalyseCtrl): [VNode, VNode] | undefined {
  if (ctrl.embed) return;

  const node = ctrl.node,
    clock = node.clock;
  if (!clock && clock !== 0) return;

  function clockOfOpponentAtPath(currentPath: Tree.Path): number | undefined {
    const playedPlayerIndex = ctrl.tree.nodeAtPath(currentPath).playedPlayerIndex;

    function findClockOfOpponent(path: Tree.Path): number | undefined {
      const parentNode = ctrl.tree.nodeAtPath(treePath.init(path));
      if (parentNode.playedPlayerIndex !== playedPlayerIndex) return parentNode.clock;
      if (treePath.size(path) <= 0) return undefined;
      return findClockOfOpponent(treePath.init(path));
    }

    return findClockOfOpponent(currentPath);
  }

  const p1Pov = ctrl.bottomIsP1();
  const isP1Turn = node.playedPlayerIndex === 'p1';
  const centis: Array<number | undefined> = [clockOfOpponentAtPath(ctrl.path), clock];

  if (isP1Turn) centis.reverse();

  const study = ctrl.study,
    relay = study && study.data.chapter.relay;
  if (relay && relay.lastMoveAt && relay.path === ctrl.path && ctrl.path !== '' && !isFinished(study!.data.chapter)) {
    const spent = (Date.now() - relay.lastMoveAt) / 10;
    const i = isP1Turn ? 0 : 1;
    if (centis[i]) centis[i] = Math.max(0, centis[i]! - spent);
  }

  const showTenths = !ctrl.study || !ctrl.study.relay;

  //fix display of starting clock for not starting in time for swiss match
  const initialTime = ctrl.data.clock ? ctrl.data.clock?.initial * 100 : centis[1];
  if (node.ply === 0) {
    centis[0] = initialTime;
    centis[1] = initialTime;
  }
  if (node.ply === 1) {
    centis[1] = initialTime;
  }

  return [
    renderClock(centis[0], isP1Turn, p1Pov ? 'bottom' : 'top', showTenths),
    renderClock(centis[1], !isP1Turn, p1Pov ? 'top' : 'bottom', showTenths),
  ];
}

function renderClock(centis: number | undefined, active: boolean, cls: string, showTenths: boolean): VNode {
  return h(
    'div.analyse__clock.' + cls,
    {
      class: { active },
    },
    clockContent(centis, showTenths),
  );
}

function clockContent(centis: number | undefined, showTenths: boolean): Array<string | VNode> {
  if (!centis && centis !== 0) return ['-'];
  const date = new Date(centis * 10),
    millis = date.getUTCMilliseconds(),
    sep = ':',
    baseStr = pad2(date.getUTCMinutes()) + sep + pad2(date.getUTCSeconds());
  if (!showTenths || centis >= 360000) return [Math.floor(centis / 360000) + sep + baseStr];
  return centis >= 6000 ? [baseStr] : [baseStr, h('tenths', '.' + Math.floor(millis / 100).toString())];
}

function pad2(num: number): string {
  return (num < 10 ? '0' : '') + num;
}
