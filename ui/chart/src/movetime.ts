import {
  BarController,
  BarElement,
  Chart,
  type ChartDataset,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';
import { GameFamily as BackgammonFamily } from 'stratops/variants/backgammon/GameFamily';
import {
  animation,
  axisOpts,
  blackFill,
  fontColor,
  fontFamily,
  maybeChart,
  oppositeColorVariants,
  plyLine,
  selectPly,
  tooltipBgColor,
  whiteFill,
} from './index';
import division from './division';

Chart.register(LineController, LinearScale, PointElement, LineElement, Tooltip, BarElement, BarController);

interface MovePoint {
  x: number;
  y: number;
}

interface AnalyseData {
  game: {
    variant: { key: string };
    division?: { middle?: number; end?: number };
    plyCentis?: number[];
    status?: { name: string };
  };
  clock?: {
    running: boolean;
    initial: number;
    increment: number;
    delay?: number;
    delayType?: 'bronstein' | 'usdelay';
    byoyomi?: number;
    periods?: number;
  };
  player: { playerIndex: PlayerIndex; blurs?: { bits?: string } };
  opponent: { blurs?: { bits?: string } };
  treeParts: Tree.Node[];
}

const toBlurArray = (player: { blurs?: { bits?: string } }) => player.blurs?.bits?.split('') ?? [];

function formatClock(centis: number): string {
  let result = '';
  if (centis >= 60 * 60 * 100) result += Math.floor(centis / 60 / 6000) + ':';
  result +=
    Math.floor((centis % (60 * 6000)) / 6000)
      .toString()
      .padStart(2, '0') + ':';
  const secs = (centis % 6000) / 100;
  result += centis < 6000 ? secs.toFixed(2).padStart(5, '0') : Math.floor(secs).toString().padStart(2, '0');
  return result;
}

export default function movetime(el: HTMLCanvasElement, data: AnalyseData, trans: Trans): void {
  if (maybeChart(el)) return;
  const plyCentis = data.game.plyCentis;
  if (!plyCentis) return; // imported games

  const isOppositeColor = oppositeColorVariants.includes(data.game.variant.key);
  const p1Fill = isOppositeColor ? blackFill : whiteFill;
  const p2Fill = isOppositeColor ? whiteFill : blackFill;

  const isBackgammon = (BackgammonFamily.getVariantKeys() as string[]).includes(data.game.variant.key);

  const blurs = [toBlurArray(data.player), toBlurArray(data.opponent)];
  if (data.player.playerIndex === 'p1') blurs.reverse();

  const tree = data.treeParts;
  const firstPly = tree[0]?.ply ?? 0;

  const moveSeries: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };
  const totalSeries: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };
  const labels: string[] = [];
  for (let i = 0; i <= firstPly; i++) labels.push('');

  const blurPoints: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };

  const logC = Math.pow(Math.log(3), 2);
  let bgBlurPending: { key: 'p1' | 'p2'; point: MovePoint } | undefined;
  let lastBgKey: 'p1' | 'p2' | undefined;
  let bgTurnCentis = 0;
  let bgTurnNotations: string[] = [];
  // For backgammon: use a per-turn index as x so doubles (4 checkers) don't shift subsequent bars.
  let bgTurnX = firstPly;
  const bgPlyToTurnX = new Map<number, number>(); // any ply → its turn's x (for selectPly)
  const bgTurnXToPly = new Map<number, number>(); // turn x → last-checker ply (for click navigation)
  const bgLabelByX = new Map<number, string>(); // turn x → tooltip label

  const flushBgBlur = () => {
    if (!bgBlurPending) return;
    blurPoints[bgBlurPending.key].push(bgBlurPending.point);
    const x = bgBlurPending.point.x;
    if (isBackgammon) {
      const existing = bgLabelByX.get(x) ?? '';
      const nl = existing.indexOf('\n');
      bgLabelByX.set(x, nl >= 0 ? existing.slice(0, nl) + ' [blur]' + existing.slice(nl) : existing + ' [blur]');
    } else {
      const nl = labels[x].indexOf('\n');
      labels[x] = nl >= 0 ? labels[x].slice(0, nl) + ' [blur]' + labels[x].slice(nl) : labels[x] + ' [blur]';
    }
    bgBlurPending = undefined;
  };

  plyCentis.forEach((centis, i) => {
    const node = tree[i + 1];
    if (!tree[i]) return;
    const ply = node ? node.ply : tree[i].ply + 1;
    const isP1 = node ? node.playedPlayerIndex === 'p1' : (ply & 1) === 1;
    const key: 'p1' | 'p2' = isP1 ? 'p1' : 'p2';
    const parentNode = tree[i];
    const turn = parentNode ? Math.floor((parentNode.turnCount ?? 0) / 2) + 1 : (ply + 1) >> 1;
    const dots = isP1 ? '.' : '...';
    const san = node ? (node.san === 'NOSAN' ? (node.uci ?? '-') : (node.san ?? '-')) : '-';

    if (isBackgammon) {
      // Handle endturn before isNewTurn: endturn's playedPlayerIndex may differ from the checker
      // player, which would incorrectly trigger a new turn and shift bgTurnX prematurely.
      if (node?.uci === 'endturn') {
        bgTurnCentis += centis;
        bgPlyToTurnX.set(node.ply, bgTurnX);
        return;
      }

      const isNewTurn = key !== lastBgKey;
      if (isNewTurn) {
        flushBgBlur();
        lastBgKey = key;
        bgTurnX++;
        bgTurnCentis = 0;
        bgTurnNotations = [];
        bgBlurPending = blurs[isP1 ? 1 : 0].shift() === '1' ? { key, point: { x: bgTurnX, y: 0 } } : undefined;
      }
      bgTurnCentis += centis;
      bgPlyToTurnX.set(node ? node.ply : ply, bgTurnX);

      // Roll node (isNewTurn) contributes time but not move notation; checker moves accumulate notation
      if (!isNewTurn && node) {
        bgTurnNotations.push(
          BackgammonFamily.computeMoveNotation({
            san: node.san ?? '',
            uci: node.uci ?? '',
            fen: node.fen ?? '',
            prevFen: tree[i].fen ?? '',
          }),
        );
      }

      // Emit only on the last checker move of the turn (next node is endTurn or player changes)
      const nextNode = tree[i + 2];
      const isLastChecker = !nextNode || nextNode.uci === 'endturn' || nextNode.playedPlayerIndex !== key;
      if (!isLastChecker) return;

      const y = Math.pow(Math.log(0.005 * Math.min(bgTurnCentis, 12e4) + 3), 2) - logC;
      const movePoint: MovePoint = { x: bgTurnX, y: isP1 ? y : -y };
      bgTurnXToPly.set(bgTurnX, node ? node.ply : ply);
      if (bgBlurPending) bgBlurPending.point = movePoint;

      const moveSan = bgTurnNotations.length > 0 ? BackgammonFamily.combinedNotation(bgTurnNotations) : san;
      const seconds = (bgTurnCentis / 100).toFixed(bgTurnCentis >= 200 ? 1 : 2);
      bgLabelByX.set(bgTurnX, turn + dots + ' ' + moveSan + '\n' + trans.plural('nbSeconds', Number(seconds)));
      moveSeries[key].push(movePoint);

      const clock = node?.clock;
      if (clock) totalSeries[key].push({ x: bgTurnX, y: isP1 ? clock : -clock });
      return;
    }

    // Chess / non-backgammon
    const y = Math.pow(Math.log(0.005 * Math.min(centis, 12e4) + 3), 2) - logC;
    const movePoint: MovePoint = { x: node ? node.ply : ply, y: isP1 ? y : -y };

    const isBlur = blurs[isP1 ? 1 : 0].shift() === '1';
    if (isBlur) blurPoints[key].push(movePoint);

    let label = turn + dots + ' ' + san;
    if (isBlur) label += ' [blur]';
    const seconds = (centis / 100).toFixed(centis >= 200 ? 1 : 2);
    label += '\n' + trans.plural('nbSeconds', Number(seconds));
    moveSeries[key].push(movePoint);
    labels.push(label);

    let clock = node ? node.clock : undefined;
    if (clock === undefined) {
      if (data.game.status?.name === 'outoftime') clock = 0;
      else if (data.clock && !data.clock.delayType) {
        // Fischer/Byoyomi only: approximate remaining time from previous clock value.
        // Bronstein/SimpleDelay are excluded because node.clock values from the backend
        // are incorrect for delay clocks (known backend limitation, Game.scala TODO).
        const prevClock = tree[i - 1]?.clock;
        if (prevClock) clock = prevClock + data.clock.increment - centis;
      }
    }
    if (clock) {
      label += '\n' + formatClock(clock);
      totalSeries[key].push({ x: movePoint.x, y: isP1 ? clock : -clock });
    }
  });
  flushBgBlur();

  const maxMove = Math.max(...moveSeries.p1.map(p => Math.abs(p.y)), ...moveSeries.p2.map(p => Math.abs(p.y)), 0.001);
  const maxTotal = Math.max(
    ...totalSeries.p1.map(p => Math.abs(p.y)),
    ...totalSeries.p2.map(p => Math.abs(p.y)),
    0.001,
  );

  const blueLineColor = '#3893e8';

  const moveBarDatasets = (['p1', 'p2'] as const).map(key => ({
    type: 'bar' as const,
    data: moveSeries[key].map(p => ({ x: p.x, y: p.y / maxMove })),
    backgroundColor: key === 'p1' ? p1Fill : p2Fill,
    grouped: false,
    categoryPercentage: 2,
    barPercentage: 1,
    order: 2,
    borderColor: key === 'p1' ? '#838383' : '#616161',
    borderWidth: 1,
    datalabels: { display: false },
  }));

  const totalDatasets = (['p1', 'p2'] as const).map(key => ({
    type: 'line' as const,
    data: totalSeries[key].map(p => ({ x: p.x, y: p.y / maxTotal })),
    backgroundColor: key,
    borderColor: blueLineColor,
    borderWidth: 1.5,
    pointHitRadius: 200,
    pointHoverBorderColor: blueLineColor,
    pointRadius: 0,
    pointHoverRadius: 5,
    fill: { target: 'origin', above: 'rgba(153,153,153,.3)', below: 'rgba(0,0,0,0.3)' },
    order: 1,
    datalabels: { display: false },
  }));

  const blurDatasets = (['p1', 'p2'] as const)
    .filter(key => blurPoints[key].length > 0)
    .map(key => {
      const blurXSet = new Set(blurPoints[key].map(p => p.x));
      return {
        type: 'line' as const,
        data: moveSeries[key].map(p => ({ x: p.x, y: p.y / maxMove })),
        borderWidth: 0,
        pointRadius: moveSeries[key].map(p => (blurXSet.has(p.x) ? 4.5 : 0)),
        pointHoverRadius: 5,
        pointStyle: 'rect' as const,
        pointBackgroundColor: key === 'p1' ? '#555555' : '#bbbbbb',
        pointBorderColor: key === 'p1' ? '#aaaaaa' : '#444444',
        pointBorderWidth: 1.5,
        order: 0,
        datalabels: { display: false },
      };
    });

  const divLines = division(data.game.division, { noarg: (k: string) => k } as Trans);
  const datasets: ChartDataset[] = [
    ...moveBarDatasets,
    ...totalDatasets,
    ...blurDatasets,
    plyLine(firstPly),
    ...divLines,
  ];

  const chart = new Chart(el, {
    type: 'line',
    data: { labels, datasets },
    options: {
      maintainAspectRatio: false,
      responsive: true,
      animations: animation(800 / Math.max(1, isBackgammon ? bgTurnX - firstPly : labels.length - 1)),
      scales: isBackgammon
        ? axisOpts(firstPly + 1, bgTurnX)
        : axisOpts(firstPly + 1, tree[tree.length - 1]?.ply ?? firstPly + plyCentis.length),
      plugins: {
        tooltip: {
          borderColor: fontColor,
          borderWidth: 1,
          backgroundColor: tooltipBgColor,
          caretPadding: 15,
          titleColor: fontColor,
          titleFont: fontFamily(13),
          displayColors: false,
          callbacks: {
            title: items =>
              (isBackgammon ? bgLabelByX.get(items[0].parsed.x) : labels[items[0].parsed.x]) ?? '',
            label: () => '',
          },
        },
      },
      onClick(_event, elements, chart) {
        if (elements[0]) {
          const pt = (chart.data.datasets[elements[0].datasetIndex]?.data as { x: number }[] | undefined)?.[
            elements[0].index
          ];
          if (pt?.x !== undefined) {
            playstrategy.pubsub.emit(
              'analysis.chart.click',
              isBackgammon ? (bgTurnXToPly.get(pt.x) ?? pt.x) : pt.x,
            );
          }
        }
      },
    },
  }) as Chart & { selectPly(ply: number): void };

  chart.selectPly = selectPly.bind(chart);

  playstrategy.pubsub.on('analysis.change', (_fen: string, _path: string, ply: Ply | false) => {
    const x = ply === false ? firstPly : isBackgammon ? (bgPlyToTurnX.get(ply) ?? firstPly) : ply;
    chart.selectPly(x);
  });
  playstrategy.pubsub.emit('analysis.change.trigger');

  // Game duration label
  const duration = plyCentis.reduce((s, v) => s + v, 0);
  const label = document.createElement('div');
  label.className = 'game-duration';
  label.textContent = trans.noarg('duration') + ' ' + formatClock(duration);
  el.parentElement?.appendChild(label);
}
