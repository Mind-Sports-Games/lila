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
  turn?: number;
}

// One action of a backgammon turn. Every action of a turn carries the turn's full bar height so
// that hovering anywhere in its column hits the bar; `seg` is the action's share of that height,
// used to draw the dividers inside the bar.
interface ActionPoint {
  x: number;
  y: number;
  ply: number;
  turn: number;
  seg: [number, number];
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

// Zero-time turns still get a visible stub, so a fast turn reads as "played, ~0s" and not as a gap.
const bgMinBarPx = 2;
// Turns the backend recorded no time for are marked rather than measured, so they get a low band
// of their own. A whole run of them can go unrecorded, which full height would turn into a wall.
const bgUntimedPx = 6;
const bgUntimedShare = 0.12;

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
  const actionSeries: { p1: ActionPoint[]; p2: ActionPoint[] } = { p1: [], p2: [] };
  const totalSeries: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };
  const labels: string[] = [];
  for (let i = 0; i <= firstPly; i++) labels.push('');

  const blurPoints: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };

  const logC = Math.pow(Math.log(3), 2);
  let bgBlurPending: { key: 'p1' | 'p2'; turn: number; point: MovePoint } | undefined;
  let lastBgKey: 'p1' | 'p2' | undefined;
  let bgTurnCentis = 0;
  // Every action of the turn, in order: the dice roll, each checker move, then the end-turn.
  let bgTurnActions: { ply: number; centis: number; san: string }[] = [];
  let bgTurnNotations: string[] = [];
  let bgRollSan = '-'; // san from roll node, used as fallback label when no checker moves (e.g. dance)
  let bgTurnIdx = -1;
  let bgTurnTimed = false; // did any action of this turn have a recorded ply time?
  const bgLabelByTurn = new Map<number, string>(); // turn index → tooltip label
  const bgUntimedTurns = new Set<number>(); // turns the backend recorded no time for at all
  // For delay clocks: track remaining time per-turn on the frontend so the tooltip and clock line
  // are both correct (delay is applied once per full backgammon turn, not per individual action).
  const bgDelayCentis = (data.clock?.delay ?? 0) * 100;
  const bgInitialCentis = (data.clock?.initial ?? 0) * 100;
  const bgIsDelayType = !!(data.clock?.delayType && bgInitialCentis > 0);
  const bgCorrectRemaining: Record<'p1' | 'p2', number> = { p1: bgInitialCentis, p2: bgInitialCentis };

  const flushBgBlur = () => {
    if (!bgBlurPending) return;
    blurPoints[bgBlurPending.key].push(bgBlurPending.point);
    const existing = bgLabelByTurn.get(bgBlurPending.turn) ?? '';
    const nl = existing.indexOf('\n');
    bgLabelByTurn.set(
      bgBlurPending.turn,
      nl >= 0 ? existing.slice(0, nl) + ' [blur]' + existing.slice(nl) : existing + ' [blur]',
    );
    bgBlurPending = undefined;
  };

  const bgActionPoints = (isP1: boolean, top: number): ActionPoint[] => {
    const n = bgTurnActions.length;
    if (!n) return [];
    let acc = 0;
    return bgTurnActions.map((action, i) => {
      const from = acc;
      // Pin the last chunk to the bar's top so rounding never leaves a sliver under the blur marker.
      acc = i === n - 1 ? 1 : acc + (bgTurnCentis > 0 ? action.centis / bgTurnCentis : 1 / n);
      const seconds = (action.centis / 100).toFixed(action.centis >= 200 ? 1 : 2);
      return {
        x: action.ply,
        y: isP1 ? top : -top,
        ply: action.ply,
        turn: bgTurnIdx,
        seg: [from, acc] as [number, number],
        actionLabel: bgTurnTimed ? action.san + ' ' + trans.plural('nbSeconds', Number(seconds)) : action.san,
      };
    });
  };

  const emitBgTurn = (key: 'p1' | 'p2', isP1: boolean, heading: string, clock: number | undefined) => {
    const y = Math.pow(Math.log(0.005 * Math.min(bgTurnCentis, 12e4) + 3), 2) - logC;
    // The bar spans every ply of the turn, so its centre is the midpoint of that ply range.
    const startPly = bgTurnActions[0]?.ply ?? firstPly;
    const endPly = bgTurnActions[bgTurnActions.length - 1]?.ply ?? startPly;
    const movePoint: MovePoint = { x: (startPly + endPly) / 2, y: isP1 ? y : -y, turn: bgTurnIdx };
    if (bgBlurPending) bgBlurPending.point = movePoint;
    const seconds = (bgTurnCentis / 100).toFixed(bgTurnCentis >= 200 ? 1 : 2);
    if (bgIsDelayType)
      bgCorrectRemaining[key] = Math.max(0, bgCorrectRemaining[key] - Math.max(0, bgTurnCentis - bgDelayCentis));
    const displayClock = bgIsDelayType ? bgCorrectRemaining[key] : clock;
    if (!bgTurnTimed) bgUntimedTurns.add(bgTurnIdx);
    let label = heading + (bgTurnTimed ? '\n' + trans.plural('nbSeconds', Number(seconds)) : '');
    if (displayClock) label += '\n' + formatClock(displayClock);
    bgLabelByTurn.set(bgTurnIdx, label);
    moveSeries[key].push(movePoint);
    actionSeries[key].push(...bgActionPoints(isP1, y));
    if (displayClock)
      totalSeries[key].push({ x: movePoint.x, y: isP1 ? displayClock : -displayClock, turn: bgTurnIdx });
  };

  // Drive the loop off the tree rather than plyCentis: the backend stops recording clock times
  // once a player's clock history runs out, and the final turn of a game that ends on a bearing-off
  // move has no entries at all. Iterating plyCentis silently dropped those turns from the chart.
  const actionCount = Math.max(plyCentis.length, tree.length - 1);
  for (let i = 0; i < actionCount; i++) {
    const centis = plyCentis[i] ?? 0;
    const node = tree[i + 1];
    if (!tree[i]) continue;
    const ply = node ? node.ply : tree[i].ply + 1;
    const isP1 = node ? node.playedPlayerIndex === 'p1' : (ply & 1) === 1;
    const key: 'p1' | 'p2' = isP1 ? 'p1' : 'p2';
    const parentNode = tree[i];
    const turn = parentNode ? Math.floor((parentNode.turnCount ?? 0) / 2) + 1 : (ply + 1) >> 1;
    const dots = isP1 ? '.' : '...';
    const san = node ? (node.san === 'NOSAN' ? (node.uci ?? '-') : (node.san ?? '-')) : '-';

    if (isBackgammon) {
      // Handle endturn before isNewTurn: it closes the turn its checker moves belong to.
      if (node?.uci === 'endturn') {
        bgTurnCentis += centis;
        bgTurnTimed ||= i < plyCentis.length;
        bgTurnActions.push({ ply: node.ply, centis, san: 'end' });
        const moveSan = bgTurnNotations.length > 0 ? BackgammonFamily.combinedNotation(bgTurnNotations) : bgRollSan;
        emitBgTurn(key, isP1, turn + dots + ' ' + moveSan, node.clock);
        continue;
      }

      const isNewTurn = key !== lastBgKey;
      if (isNewTurn) {
        flushBgBlur();
        lastBgKey = key;
        bgTurnIdx++;
        bgTurnCentis = 0;
        bgTurnTimed = false;
        bgTurnActions = [];
        bgTurnNotations = [];
        bgRollSan = san; // save the roll's notation for use in the endturn label (e.g. for dances with no checkers)
        bgBlurPending =
          blurs[isP1 ? 1 : 0].shift() === '1'
            ? { key, turn: bgTurnIdx, point: { x: ply, y: 0, turn: bgTurnIdx } }
            : undefined;
      }
      bgTurnCentis += centis;
      bgTurnTimed ||= i < plyCentis.length;

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
      if (!isLastChecker) continue;

      const moveSan = bgTurnNotations.length > 0 ? BackgammonFamily.combinedNotation(bgTurnNotations) : san;
      emitBgTurn(key, isP1, turn + dots + ' ' + moveSan, node?.clock);
      continue;
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
    // After the clock line, not before: label is a string, so pushing it earlier stored a copy
    // taken before that line existed. One push per iteration keeps labels indexed by ply.
    labels.push(label);
  }
  flushBgBlur();

  // A single very long think flattens every other turn to a few pixels. When the longest turn is
  // a clear outlier (> 1.5x the 95th percentile), normalise on that percentile instead and let the
  // outlier run off the top of the scale — where it draws with no top edge, reading as off-scale.
  //
  // The percentile is taken over both players' turns pooled, so a side that answers near-instantly
  // drags it down: against a bot, half the sample sits at zero. The 95th keeps the cutoff inside
  // the slower side's own range, where the 90th fell to roughly their 80th and clipped honest
  // turns along with the outlier.
  const turnHeights = [...moveSeries.p1, ...moveSeries.p2].map(p => Math.abs(p.y)).sort((a, b) => a - b);
  const peakMove = turnHeights[turnHeights.length - 1] ?? 0;
  const p95Move = turnHeights[Math.min(Math.floor(turnHeights.length * 0.95), turnHeights.length - 1)] ?? 0;
  const moveScale = isBackgammon && p95Move > 0 ? Math.min(peakMove, 1.5 * p95Move) : peakMove;
  const maxMove = Math.max(moveScale, 0.001); // maxMove divides every bar, so it can never be zero
  const maxTotal = Math.max(
    ...totalSeries.p1.map(p => Math.abs(p.y)),
    ...totalSeries.p2.map(p => Math.abs(p.y)),
    0.001,
  );

  const blueLineColor = '#3893e8';
  const barBorderColor = (key: 'p1' | 'p2') => (key === 'p1' ? '#838383' : '#616161');

  // A turn's bar reaches half way to the neighbouring turn of the same player, so each side reads
  // as one continuous band. That is the geometry the old per-turn axis produced with
  // categoryPercentage 2, kept here on the ply axis so the two charts still share a scale.
  const bgTurnSpan: Record<'p1' | 'p2', Map<number, [number, number]>> = { p1: new Map(), p2: new Map() };
  (['p1', 'p2'] as const).forEach(key =>
    moveSeries[key].forEach((p, i) => {
      const prev = moveSeries[key][i - 1]?.x;
      const next = moveSeries[key][i + 1]?.x;
      // the outermost bar mirrors the width of its only neighbour
      const edge = (next === undefined ? p.x - (prev ?? p.x - 2) : (next ?? p.x + 2) - p.x) / 2;
      bgTurnSpan[key].set(p.turn ?? i, [
        prev === undefined ? p.x - edge : (prev + p.x) / 2,
        next === undefined ? p.x + edge : (p.x + next) / 2,
      ]);
    }),
  );

  const moveBarDatasets = (['p1', 'p2'] as const).map(key => ({
    type: 'bar' as const,
    data: isBackgammon
      ? actionSeries[key].map(p => ({ ...p, y: p.y / maxMove }))
      : moveSeries[key].map(p => ({ x: p.x, y: p.y / maxMove })),
    backgroundColor: isBackgammon ? 'transparent' : key === 'p1' ? p1Fill : p2Fill,
    grouped: false,
    // Backgammon: one (invisible) bar per action ply, carrying that action's tooltip. bgTurnBars
    // paints the visible bar; hit testing is by nearest ply, so it covers the painted width.
    categoryPercentage: isBackgammon ? 1 : 2,
    barPercentage: 1,
    order: 2,
    borderColor: barBorderColor(key),
    borderWidth: isBackgammon ? 0 : 1,
    bgSegmentKey: isBackgammon ? key : undefined,
    datalabels: { display: false },
  })) as unknown as ChartDataset[];

  // Blur markers are placed on the bar edge the plugin actually draws, not on the turn's raw
  // value: a floored, off-scale or untimed turn draws its edge elsewhere, and a marker left on
  // the raw value would float free of its bar. Collected while drawing, painted on top after.
  const bgBlurMarks: { x: number; y: number; key: 'p1' | 'p2' }[] = [];
  const bgBlurRadius = 4.5;
  const bgBlurTurns = {
    p1: new Set(blurPoints.p1.map(p => p.turn)),
    p2: new Set(blurPoints.p2.map(p => p.turn)),
  };

  const bgTurnBars = {
    id: 'bgTurnBars',
    beforeDatasetsDraw(chart: Chart) {
      bgBlurMarks.length = 0;
      const zero = chart.scales.y?.getPixelForValue(0);
      const xScale = chart.scales.x;
      if (zero === undefined || !xScale) return;
      const ctx = chart.ctx;
      const dpr = chart.currentDevicePixelRatio || 1;
      const weight = Math.max(1, Math.round(dpr)); // line thickness
      const dev = (v: number) => Math.round(v * dpr); // nearest device pixel
      const css = (devicePx: number) => devicePx / dpr;
      const axis = dev(zero);
      const area = chart.chartArea;
      ctx.save();
      ctx.beginPath();
      ctx.rect(area.left, area.top, area.right - area.left, area.bottom - area.top);
      ctx.clip();
      ctx.lineWidth = css(weight);
      chart.data.datasets.forEach((dataset, i) => {
        const key = (dataset as { bgSegmentKey?: 'p1' | 'p2' }).bgSegmentKey;
        const meta = chart.getDatasetMeta(i);
        if (!key || meta.hidden) return;
        const bars = meta.data as unknown as { y: number }[];
        const points = actionSeries[key];
        const dir = key === 'p1' ? -1 : 1; // p1 bars grow towards smaller pixel y
        const inset = -dir * (weight / 2); // keep the border stroke inside the bar
        const gap = 2 * weight; // closest two lines may sit before we drop one
        const minHeight = Math.max(dev(bgMinBarPx), weight);
        const halfPlot = Math.abs(dev(key === 'p1' ? area.top : area.bottom) - axis);
        const untimedEdge = axis + dir * Math.max(dev(bgUntimedPx), Math.round(halfPlot * bgUntimedShare));
        const untimed: [number, number][] = [];
        const blurTurns = bgBlurTurns[key];
        const markY = (t: number) =>
          Math.min(Math.max(t, dev(area.top + bgBlurRadius)), dev(area.bottom - bgBlurRadius));
        ctx.fillStyle = key === 'p1' ? p1Fill : p2Fill;
        ctx.strokeStyle = barBorderColor(key);
        ctx.beginPath();
        for (let start = 0; start < bars.length;) {
          let end = start;
          if (!points[start]) break;
          while (end + 1 < bars.length && points[end + 1]?.turn === points[start].turn) end++;
          const span = bgTurnSpan[key].get(points[start].turn);
          const left = dev(xScale.getPixelForValue(span?.[0] ?? points[start].x - 0.5));
          const right = dev(xScale.getPixelForValue(span?.[1] ?? points[end].x + 0.5));
          let top = dev(bars[end].y);
          if (bgUntimedTurns.has(points[start].turn)) {
            if (isFinite(left) && isFinite(right)) {
              untimed.push([left, right]);
              if (blurTurns.has(points[start].turn))
                bgBlurMarks.push({ x: css((left + right) / 2), y: css(markY(untimedEdge)), key });
            }
            start = end + 1;
            continue;
          }
          if (isFinite(left) && isFinite(right) && isFinite(top)) {
            if (Math.abs(top - axis) < minHeight) top = axis + dir * minHeight;
            if (blurTurns.has(points[start].turn))
              bgBlurMarks.push({ x: css((left + right) / 2), y: css(markY(top)), key });
            ctx.fillRect(css(left), css(Math.min(top, axis)), css(right - left), css(Math.abs(top - axis)));
            ctx.moveTo(css(left + weight / 2), css(axis));
            ctx.lineTo(css(left + weight / 2), css(top + inset));
            ctx.lineTo(css(right - weight / 2), css(top + inset));
            ctx.lineTo(css(right - weight / 2), css(axis));
            const height = top - axis;
            let last = axis;
            for (let j = start; j < end; j++) {
              const edge = axis + Math.round(height * points[j].seg[1]);
              if (Math.abs(edge - last) < gap || Math.abs(top - edge) < gap) continue;
              last = edge;
              ctx.moveTo(css(left + weight / 2), css(edge + inset));
              ctx.lineTo(css(right - weight / 2), css(edge + inset));
            }
          }
          start = end + 1;
        }
        ctx.stroke();
        // Turns the backend recorded no time for: a low dashed band, so the chart still reaches the
        // last move instead of flatlining, while reading as "not measured" rather than as a duration.
        if (untimed.length) {
          ctx.save();
          ctx.setLineDash([css(2 * weight), css(2 * weight)]);
          for (const [left, right] of untimed) {
            ctx.globalAlpha = 0.35;
            ctx.fillRect(
              css(left),
              css(Math.min(untimedEdge, axis)),
              css(right - left),
              css(Math.abs(untimedEdge - axis)),
            );
            ctx.globalAlpha = 1;
            ctx.beginPath();
            ctx.moveTo(css(left + weight / 2), css(axis));
            ctx.lineTo(css(left + weight / 2), css(untimedEdge + inset));
            ctx.lineTo(css(right - weight / 2), css(untimedEdge + inset));
            ctx.lineTo(css(right - weight / 2), css(axis));
            ctx.stroke();
          }
          ctx.restore();
        }
      });
      ctx.restore();
    },
    afterDatasetsDraw(chart: Chart) {
      if (!bgBlurMarks.length) return;
      const ctx = chart.ctx;
      ctx.save();
      ctx.lineWidth = 1.5;
      for (const mark of bgBlurMarks) {
        ctx.fillStyle = mark.key === 'p1' ? '#555555' : '#bbbbbb';
        ctx.strokeStyle = mark.key === 'p1' ? '#aaaaaa' : '#444444';
        ctx.beginPath();
        ctx.rect(mark.x - bgBlurRadius, mark.y - bgBlurRadius, bgBlurRadius * 2, bgBlurRadius * 2);
        ctx.fill();
        ctx.stroke();
      }
      ctx.restore();
    },
  };

  const totalDatasets = (['p1', 'p2'] as const).map(key => ({
    type: 'line' as const,
    data: totalSeries[key].map(p => ({ ...p, y: p.y / maxTotal })),
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

  // Backgammon draws its blur markers in bgTurnBars, on the bar edge it actually traced.
  const blurDatasets = (isBackgammon ? [] : (['p1', 'p2'] as ('p1' | 'p2')[]))
    .filter(key => blurPoints[key].length > 0)
    .map(key => {
      const blurXSet = new Set(blurPoints[key].map(p => p.x));
      return {
        type: 'line' as const,
        data: moveSeries[key].map(p => ({ ...p, y: p.y / maxMove })),
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

  // Backgammon shares the acpl chart's ply axis so both charts' ply lines line up. The acpl chart
  // (fed by bgWinChart) has no point for endturn nodes, hence the last non-endturn ply as max.
  const lastPly = tree[tree.length - 1]?.ply ?? firstPly + plyCentis.length;
  const bgLastPly = (() => {
    for (let i = tree.length - 1; i > 0; i--) if (tree[i].uci !== 'endturn') return tree[i].ply;
    return lastPly;
  })();

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
    plugins: isBackgammon ? [bgTurnBars] : [],
    options: {
      maintainAspectRatio: false,
      responsive: true,
      // The painted bar is wider than the one-ply action bars behind it, so hit test on the
      // nearest ply rather than requiring the pointer to land inside one, as the acpl chart does.
      ...(isBackgammon ? { interaction: { mode: 'nearest' as const, axis: 'x' as const, intersect: false } } : {}),
      animations: animation(
        800 / Math.max(1, isBackgammon ? Math.max(actionSeries.p1.length, actionSeries.p2.length) : labels.length - 1),
      ),
      scales: axisOpts(firstPly + 1, isBackgammon ? bgLastPly : lastPly),
      plugins: {
        tooltip: {
          borderColor: fontColor,
          borderWidth: 1,
          backgroundColor: tooltipBgColor,
          caretPadding: 15,
          titleColor: fontColor,
          titleFont: fontFamily(13),
          bodyColor: fontColor,
          bodyFont: fontFamily(13),
          displayColors: false,
          callbacks: {
            title: items =>
              (isBackgammon
                ? bgLabelByTurn.get((items[0].raw as Partial<ActionPoint>)?.turn ?? -1)
                : labels[items[0].parsed.x]) ?? '',
            label: ctx => (ctx.raw as Partial<ActionPoint>)?.actionLabel ?? '',
          },
        },
      },
      onClick(_event, elements, chart) {
        if (elements[0]) {
          const pt = (
            chart.data.datasets[elements[0].datasetIndex]?.data as { x: number; ply?: number }[] | undefined
          )?.[elements[0].index];
          if (pt?.x !== undefined)
            playstrategy.pubsub.emit('analysis.chart.click', isBackgammon ? (pt.ply ?? Math.round(pt.x)) : pt.x);
        }
      },
    },
  }) as Chart & { selectPly(ply: number): void };

  chart.selectPly = selectPly.bind(chart);

  playstrategy.pubsub.on('analysis.change', (_fen: string, _path: string, ply: Ply | false) => {
    chart.selectPly(ply === false ? firstPly : ply);
  });
  playstrategy.pubsub.emit('analysis.change.trigger');

  // Game duration label
  const duration = plyCentis.reduce((s, v) => s + v, 0);
  const label = document.createElement('div');
  label.className = 'game-duration';
  label.textContent = trans.noarg('duration') + ' ' + formatClock(duration);
  el.parentElement?.appendChild(label);
}
