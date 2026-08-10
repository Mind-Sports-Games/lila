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

// One chunk of a backgammon turn's bar: a floating bar spanning [from, to] on the y axis.
interface SegmentPoint {
  x: number;
  y: [number, number];
  ply: number;
  actionLabel: string;
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
  const segmentSeries: { p1: SegmentPoint[]; p2: SegmentPoint[] } = { p1: [], p2: [] };
  const totalSeries: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };
  const labels: string[] = [];
  for (let i = 0; i <= firstPly; i++) labels.push('');

  const blurPoints: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };

  const logC = Math.pow(Math.log(3), 2);
  let bgBlurPending: { key: 'p1' | 'p2'; point: MovePoint } | undefined;
  let lastBgKey: 'p1' | 'p2' | undefined;
  let bgTurnCentis = 0;
  // Every action of the turn, in order: the dice roll, each checker move, then the end-turn.
  let bgTurnActions: { ply: number; centis: number; san: string }[] = [];
  let bgTurnNotations: string[] = [];
  let bgRollSan = '-'; // san from roll node, used as fallback label when no checker moves (e.g. dance)
  // For backgammon: use a per-turn index as x so doubles (4 checkers) don't shift subsequent bars.
  let bgTurnX = firstPly;
  const bgPlyToTurnX = new Map<number, number>(); // any ply → its turn's x (for selectPly)
  const bgTurnXToPly = new Map<number, number>(); // turn x → endturn/last-checker ply (for click navigation)
  const bgLabelByX = new Map<number, string>(); // turn x → tooltip label
  // For delay clocks: track remaining time per-turn on the frontend so the tooltip and clock line
  // are both correct (delay is applied once per full backgammon turn, not per individual action).
  const bgDelayCentis = (data.clock?.delay ?? 0) * 100;
  const bgInitialCentis = (data.clock?.initial ?? 0) * 100;
  const bgIsDelayType = !!(data.clock?.delayType && bgInitialCentis > 0);
  const bgCorrectRemaining: Record<'p1' | 'p2', number> = { p1: bgInitialCentis, p2: bgInitialCentis };

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

  const bgSegmentsForTurn = (isP1: boolean, top: number): SegmentPoint[] => {
    const n = bgTurnActions.length;
    if (!n) return [];
    let acc = 0;
    return bgTurnActions.map((action, i) => {
      const from = acc;
      // Pin the last chunk to the bar's top so rounding never leaves a sliver under the blur marker.
      acc = i === n - 1 ? 1 : acc + (bgTurnCentis > 0 ? action.centis / bgTurnCentis : 1 / n);
      const seconds = (action.centis / 100).toFixed(action.centis >= 200 ? 1 : 2);
      return {
        x: bgTurnX,
        y: (isP1 ? [from * top, acc * top] : [-acc * top, -from * top]) as [number, number],
        ply: action.ply,
        actionLabel: action.san + ' ' + trans.plural('nbSeconds', Number(seconds)),
      };
    });
  };

  const emitBgTurn = (key: 'p1' | 'p2', isP1: boolean, endPly: number, heading: string, clock: number | undefined) => {
    const y = Math.pow(Math.log(0.005 * Math.min(bgTurnCentis, 12e4) + 3), 2) - logC;
    const movePoint: MovePoint = { x: bgTurnX, y: isP1 ? y : -y };
    bgTurnXToPly.set(bgTurnX, endPly);
    if (bgBlurPending) bgBlurPending.point = movePoint;
    const seconds = (bgTurnCentis / 100).toFixed(bgTurnCentis >= 200 ? 1 : 2);
    if (bgIsDelayType)
      bgCorrectRemaining[key] = Math.max(0, bgCorrectRemaining[key] - Math.max(0, bgTurnCentis - bgDelayCentis));
    const displayClock = bgIsDelayType ? bgCorrectRemaining[key] : clock;
    let label = heading + '\n' + trans.plural('nbSeconds', Number(seconds));
    if (displayClock) label += '\n' + formatClock(displayClock);
    bgLabelByX.set(bgTurnX, label);
    moveSeries[key].push(movePoint);
    segmentSeries[key].push(...bgSegmentsForTurn(isP1, y));
    if (displayClock) totalSeries[key].push({ x: bgTurnX, y: isP1 ? displayClock : -displayClock });
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
      // Handle endturn before isNewTurn: endturn's playedPlayerIndex differs from the checker player,
      // which would incorrectly trigger a new turn and shift bgTurnX prematurely.
      if (node?.uci === 'endturn') {
        bgTurnCentis += centis;
        bgTurnActions.push({ ply: node.ply, centis, san: 'end' });
        bgPlyToTurnX.set(node.ply, bgTurnX);
        const moveSan = bgTurnNotations.length > 0 ? BackgammonFamily.combinedNotation(bgTurnNotations) : bgRollSan;
        emitBgTurn(key, isP1, node.ply, turn + dots + ' ' + moveSan, node.clock);
        return;
      }

      const isNewTurn = key !== lastBgKey;
      if (isNewTurn) {
        flushBgBlur();
        lastBgKey = key;
        bgTurnX++;
        bgTurnCentis = 0;
        bgTurnActions = [];
        bgTurnNotations = [];
        bgRollSan = san; // save the roll's notation for use in the endturn label (e.g. for dances with no checkers)
        bgBlurPending = blurs[isP1 ? 1 : 0].shift() === '1' ? { key, point: { x: bgTurnX, y: 0 } } : undefined;
      }
      bgTurnCentis += centis;
      bgPlyToTurnX.set(node ? node.ply : ply, bgTurnX);

      let actionSan = san;
      if (!isNewTurn && node) {
        actionSan = BackgammonFamily.computeMoveNotation({
          san: node.san ?? '',
          uci: node.uci ?? '',
          fen: node.fen ?? '',
          prevFen: tree[i].fen ?? '',
        });
        bgTurnNotations.push(actionSan);
      }
      bgTurnActions.push({ ply: node ? node.ply : ply, centis, san: actionSan });

      // For turns with an explicit endturn node, the bar is emitted in the endturn branch above.
      // Here we only emit for turns that end WITHOUT an explicit endturn (e.g. bearing off the last
      // piece auto-ends the turn, or the game ends mid-turn) — detected by a player change in nextNode.
      const nextNode = tree[i + 2];
      const isLastChecker = !nextNode || nextNode.playedPlayerIndex !== key;
      if (!isLastChecker) return;

      const moveSan = bgTurnNotations.length > 0 ? BackgammonFamily.combinedNotation(bgTurnNotations) : san;
      emitBgTurn(key, isP1, node ? node.ply : ply, turn + dots + ' ' + moveSan, node?.clock);
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
    data: isBackgammon
      ? segmentSeries[key].map(p => ({ ...p, y: [p.y[0] / maxMove, p.y[1] / maxMove] as [number, number] }))
      : moveSeries[key].map(p => ({ x: p.x, y: p.y / maxMove })),
    backgroundColor: key === 'p1' ? p1Fill : p2Fill,
    grouped: false,
    categoryPercentage: 2,
    barPercentage: 1,
    order: 2,
    borderColor: key === 'p1' ? '#838383' : '#616161',
    borderWidth: 1,
    borderSkipped: isBackgammon ? (false as const) : undefined,
    datalabels: { display: false },
  })) as unknown as ChartDataset[];

  const totalDatasets = (['p1', 'p2'] as const).map(key => ({
    type: 'line' as const,
    data: totalSeries[key].map(p => ({ x: p.x, y: p.y / maxTotal })),
    backgroundColor: key,
    borderColor: 'rgba(56,147,232,0.5)',
    borderWidth: 1,
    borderDash: [4, 4],
    pointHitRadius: 200,
    pointHoverBorderColor: blueLineColor,
    pointRadius: 0,
    pointHoverRadius: 5,
    fill: false,
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
      animations: animation(
        800 /
          Math.max(1, isBackgammon ? Math.max(segmentSeries.p1.length, segmentSeries.p2.length) : labels.length - 1),
      ),
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
            title: items => (isBackgammon ? bgLabelByX.get(items[0].parsed.x) : labels[items[0].parsed.x]) ?? '',
            label: ctx => (ctx.raw as Partial<SegmentPoint>)?.actionLabel ?? '',
          },
        },
      },
      onClick(_event, elements, chart) {
        if (elements[0]) {
          const pt = (
            chart.data.datasets[elements[0].datasetIndex]?.data as { x: number; ply?: number }[] | undefined
          )?.[elements[0].index];
          if (pt?.x !== undefined) {
            playstrategy.pubsub.emit(
              'analysis.chart.click',
              isBackgammon ? (pt.ply ?? bgTurnXToPly.get(pt.x) ?? pt.x) : pt.x,
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
