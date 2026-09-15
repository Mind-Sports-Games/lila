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
import { GameFamilyKey } from 'stratops/variants/types';
import { variantClassFromKey } from 'stratops/variants/util';
import {
  animation,
  axisOpts,
  blackFill,
  fontColor,
  fontFamily,
  maybeChart,
  oppositeColorVariants,
  orangeAccent,
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

// One ply. Every ply of a turn carries the turn's full bar height so that hovering anywhere in its
// column hits the bar; `seg` is the ply's share of that height, used to draw the dividers inside it.
interface ActionPoint {
  x: number;
  y: number;
  ply: number;
  turn: number;
  seg: [number, number];
  actionLabel: string;
  blur: boolean;
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
const minBarPx = 2;
// Turns the backend recorded no time for are marked rather than measured, so they get a low band
// of their own. A whole run of them can go unrecorded, which full height would turn into a wall.
const untimedPx = 6;
const untimedShare = 0.12;

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

  const variantKey = data.game.variant.key;
  const isOppositeColor = oppositeColorVariants.includes(variantKey);
  const p1Fill = isOppositeColor ? blackFill : whiteFill;
  const p2Fill = isOppositeColor ? whiteFill : blackFill;

  const variantClass = variantClassFromKey(variantKey);
  const isBackgammon = variantClass.family === GameFamilyKey.backgammon;
  const combinedNotation = (notations: string[]): string => variantClass.combinedNotation(notations);
  const actionNotation = (node: Tree.Node, prevFen: string): string =>
    variantClass.computeMoveNotation({ san: node.san ?? '', uci: node.uci ?? '', fen: node.fen ?? '', prevFen });

  const blurs = [toBlurArray(data.player), toBlurArray(data.opponent)];
  if (data.player.playerIndex === 'p1') blurs.reverse();

  const tree = data.treeParts;
  const firstPly = tree[0]?.ply ?? 0;
  // A turn spanning several plies — the same player acting twice in a row — is drawn as a single
  // bar split by a divider per action, rather than as one bar per ply.
  const multiAction = tree.some((n, i) => i > 1 && n.playedPlayerIndex === tree[i - 1]?.playedPlayerIndex);
  const stacked = isBackgammon || multiAction;

  const moveSeries: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };
  const actionSeries: { p1: ActionPoint[]; p2: ActionPoint[] } = { p1: [], p2: [] };
  const totalSeries: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };
  const labels: string[] = [];
  for (let i = 0; i <= firstPly; i++) labels.push('');

  const blurPoints: { p1: MovePoint[]; p2: MovePoint[] } = { p1: [], p2: [] };

  const logC = Math.pow(Math.log(3), 2);
  let blurPending: { key: 'p1' | 'p2'; turn: number; point: MovePoint } | undefined;
  // The backend indexes blur bits by Game.playerMoves, which sums actionStrs turn sizes — one bit
  // per action, not per turn, so a backgammon turn spans a roll, its checker moves and an endturn.
  const blurAt: Record<'p1' | 'p2', number> = { p1: 0, p2: 0 };
  let lastTurnKey: 'p1' | 'p2' | undefined;
  let turnCentis = 0;
  // Every action of the turn, in order (in backgammon: the dice roll, each checker move, the end-turn).
  let turnActions: { ply: number; centis: number; san: string; blur: boolean }[] = [];
  const blurCountByTurn = new Map<number, number>(); // turn index → how many of its actions were blurred
  let turnNotations: string[] = [];
  let bgRollSan = '-'; // san from roll node, used as fallback label when no checker moves (e.g. dance)
  let turnIdx = -1;
  let turnTimed = false; // did any action of this turn have a recorded ply time?
  const labelByTurn = new Map<number, string>(); // turn index → tooltip label
  const untimedTurns = new Set<number>(); // turns the backend recorded no time for at all
  const landingPlyByTurn = new Map<number, number>(); // turn index → ply a click on its bar jumps to
  const turnByPly = new Map<number, number>(); // ply → the turn it belongs to, for the selection
  let selectedTurn = -1; // turn under the board's current ply; its bar is outlined
  let atPly = firstPly; // last ply the board reported; its slice of the bar is tinted
  let showPinned: () => void = () => {}; // set once the chart exists; pins the selected tooltip
  // Fallback for delay clocks
  const delayCentis = (data.clock?.delay ?? 0) * 100;
  const initialCentis = (data.clock?.initial ?? 0) * 100;
  const isDelayType = !!(data.clock?.delayType && initialCentis > 0);
  const correctRemaining: Record<'p1' | 'p2', number> = { p1: initialCentis, p2: initialCentis };

  const flushBlur = () => {
    if (!blurPending) return;
    blurPoints[blurPending.key].push(blurPending.point);
    const existing = labelByTurn.get(blurPending.turn) ?? '';
    const nl = existing.indexOf('\n');
    // The heading counts them; which actions they were is spelled out on the action lines below it.
    const nb = blurCountByTurn.get(blurPending.turn) ?? 1;
    const tag = nb > 1 ? ` [${nb} blurs]` : ' [blur]';
    labelByTurn.set(blurPending.turn, nl >= 0 ? existing.slice(0, nl) + tag + existing.slice(nl) : existing + tag);
    blurPending = undefined;
  };

  const turnActionPoints = (isP1: boolean, top: number): ActionPoint[] => {
    const n = turnActions.length;
    if (!n) return [];
    let acc = 0;
    return turnActions.map((action, i) => {
      const from = acc;
      // Pin the last chunk to the bar's top so rounding never leaves a sliver under the blur marker.
      acc = i === n - 1 ? 1 : acc + (turnCentis > 0 ? action.centis / turnCentis : 1 / n);
      const seconds = (action.centis / 100).toFixed(action.centis >= 200 ? 1 : 2);
      return {
        x: action.ply,
        y: isP1 ? top : -top,
        ply: action.ply,
        turn: turnIdx,
        seg: [from, acc] as [number, number],
        blur: action.blur,
        // A turn of one action is already spelled out by the tooltip's heading and total, blur tag
        // included. Past that, each action says for itself whether it was the blurred one.
        actionLabel:
          n === 1
            ? ''
            : (turnTimed ? action.san + ' ' + trans.plural('nbSeconds', Number(seconds)) : action.san) +
              (action.blur ? ' [blur]' : ''),
      };
    });
  };

  const emitTurn = (key: 'p1' | 'p2', isP1: boolean, heading: string, clock: number | undefined) => {
    const y = Math.pow(Math.log(0.005 * Math.min(turnCentis, 12e4) + 3), 2) - logC;
    // The bar spans every ply of the turn, so its centre is the midpoint of that ply range.
    const startPly = turnActions[0]?.ply ?? firstPly;
    const endPly = turnActions[turnActions.length - 1]?.ply ?? startPly;
    const movePoint: MovePoint = { x: (startPly + endPly) / 2, y: isP1 ? y : -y, turn: turnIdx };
    blurCountByTurn.set(turnIdx, turnActions.filter(a => a.blur).length);
    if (blurPending) blurPending.point = movePoint;
    const seconds = (turnCentis / 100).toFixed(turnCentis >= 200 ? 1 : 2);
    // node.clock already spends the delay once per turn (Game.bothClockStates) and takes the last
    // turn from the stored clock, so it wins wherever the backend emits one.
    if (isDelayType)
      correctRemaining[key] = clock ?? Math.max(0, correctRemaining[key] - Math.max(0, turnCentis - delayCentis));
    const displayClock = isDelayType ? correctRemaining[key] : clock;
    // The clock only starts once both sides have acted, so the first turn of each is never
    // measured — every variant records zeros there, and a multi-action turn spans several plies,
    // which turns two invisible half-moves into two bars sitting at the axis. Read them as
    // unmeasured instead. Guarded on the recorded total, so a genuine instant turn later in the
    // game still reads as one, and a backend that ever times these takes over.
    if (turnIdx < 2 && turnCentis === 0) turnTimed = false;
    if (!turnTimed) untimedTurns.add(turnIdx);
    // Two durations sit in one tooltip — the turn's total here, the pointed action's below — so
    // name them. Without it neither reads as belonging to one rather than the other.
    let label = heading + (turnTimed ? '\nTurn: ' + trans.plural('nbSeconds', Number(seconds)) : '');
    if (displayClock) label += '\nClock: ' + formatClock(displayClock);
    labelByTurn.set(turnIdx, label);
    // Skip the endturn: it leaves the board untouched, and a path pointing at one gets no active
    // node in the move tree (hence autoScroll's fallback below).
    const landing = [...turnActions].reverse().find(a => a.san !== 'end') ?? turnActions[turnActions.length - 1];
    landingPlyByTurn.set(turnIdx, landing?.ply ?? endPly);
    for (const action of turnActions) turnByPly.set(action.ply, turnIdx);
    moveSeries[key].push(movePoint);
    actionSeries[key].push(...turnActionPoints(isP1, y));
    if (displayClock) totalSeries[key].push({ x: movePoint.x, y: isP1 ? displayClock : -displayClock, turn: turnIdx });
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
    // Consumed for every action, ahead of any branch that skips the rest of the body.
    const isBlur = blurs[isP1 ? 1 : 0][blurAt[key]++] === '1';
    // A turn carries one marker however many of its actions were blurred: the marker sits on the
    // bar, and the bar is the turn.
    const markBlur = () => {
      if (!blurPending) blurPending = { key, turn: turnIdx, point: { x: ply, y: 0, turn: turnIdx } };
    };

    if (stacked) {
      if (node?.uci === 'endturn') {
        turnCentis += centis;
        turnTimed ||= i < plyCentis.length;
        turnActions.push({ ply: node.ply, centis, san: 'end', blur: isBlur });
        if (isBlur) markBlur();
        const moveSan = turnNotations.length > 0 ? combinedNotation(turnNotations) : bgRollSan;
        emitTurn(key, isP1, turn + dots + ' ' + moveSan, node.clock);
        continue;
      }

      const isNewTurn = key !== lastTurnKey;
      if (isNewTurn) {
        flushBlur();
        lastTurnKey = key;
        turnIdx++;
        turnCentis = 0;
        turnTimed = false;
        turnActions = [];
        turnNotations = [];
        bgRollSan = san; // save the roll's notation for use in the endturn label (e.g. for dances with no checkers)
      }
      if (isBlur) markBlur();
      turnCentis += centis;
      turnTimed ||= i < plyCentis.length;

      let actionSan = san;
      if (node && !(isBackgammon && isNewTurn)) {
        actionSan = actionNotation(node, tree[i].fen ?? '');
        turnNotations.push(actionSan);
      }
      turnActions.push({ ply: node ? node.ply : ply, centis, san: actionSan, blur: isBlur });

      // For turns with an explicit endturn node, the bar is emitted in the endturn branch above.
      // Here we only emit for turns that end WITHOUT one (every other multi-action variant, plus
      // backgammon turns cut short by bearing off the last piece or by the game ending mid-turn) —
      // detected by a player change in nextNode.
      const nextNode = tree[i + 2];
      if (nextNode && nextNode.playedPlayerIndex === key) continue;

      const moveSan = turnNotations.length > 0 ? combinedNotation(turnNotations) : san;
      emitTurn(key, isP1, turn + dots + ' ' + moveSan, node?.clock);
      continue;
    }

    // Single-action variants
    const y = Math.pow(Math.log(0.005 * Math.min(centis, 12e4) + 3), 2) - logC;
    const movePoint: MovePoint = { x: node ? node.ply : ply, y: isP1 ? y : -y };

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
  flushBlur();

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
  const moveScale = stacked && p95Move > 0 ? Math.min(peakMove, 1.5 * p95Move) : peakMove;
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
  const turnSpan: Record<'p1' | 'p2', Map<number, [number, number]>> = { p1: new Map(), p2: new Map() };
  (['p1', 'p2'] as const).forEach(key =>
    moveSeries[key].forEach((p, i) => {
      const prev = moveSeries[key][i - 1]?.x;
      const next = moveSeries[key][i + 1]?.x;
      // the outermost bar mirrors the width of its only neighbour
      const edge = (next === undefined ? p.x - (prev ?? p.x - 2) : (next ?? p.x + 2) - p.x) / 2;
      turnSpan[key].set(p.turn ?? i, [
        prev === undefined ? p.x - edge : (prev + p.x) / 2,
        next === undefined ? p.x + edge : (p.x + next) / 2,
      ]);
    }),
  );

  const moveBarDatasets = (['p1', 'p2'] as const).map(key => ({
    type: 'bar' as const,
    data: stacked
      ? actionSeries[key].map(p => ({ ...p, y: p.y / maxMove }))
      : moveSeries[key].map(p => ({ x: p.x, y: p.y / maxMove })),
    backgroundColor: stacked ? 'transparent' : key === 'p1' ? p1Fill : p2Fill,
    grouped: false,
    categoryPercentage: stacked ? 1 : 2,
    barPercentage: 1,
    order: 2,
    borderColor: barBorderColor(key),
    borderWidth: stacked ? 0 : 1,
    segmentKey: stacked ? key : undefined,
    datalabels: { display: false },
  })) as unknown as ChartDataset[];

  // Blur markers are placed on the bar edge the plugin actually draws, not on the turn's raw
  // value: a floored, off-scale or untimed turn draws its edge elsewhere, and a marker left on
  // the raw value would float free of its bar. Collected while drawing, painted on top after.
  const blurMarks: { x: number; y: number; r: number; key: 'p1' | 'p2' }[] = [];
  const blurRadius = 4.5;
  // The per-action markers are the detail under the turn's own marker, so they read as smaller
  // notes on it rather than competing with it for the eye.
  const blurActionRadius = 2.75;
  const blurTurns = {
    p1: new Set(blurPoints.p1.map(p => p.turn)),
    p2: new Set(blurPoints.p2.map(p => p.turn)),
  };

  const pinKeeper = {
    id: 'movetimePin',
    afterEvent(chart: Chart, args: { inChartArea: boolean; changed?: boolean }) {
      if (pinnedPly() === undefined) return;
      if (args.inChartArea && (chart.tooltip?.getActiveElements()?.length ?? 0) > 0) return;
      showPinned();
      args.changed = true;
    },
  };

  const turnBars = {
    id: 'turnBars',
    beforeDatasetsDraw(chart: Chart) {
      blurMarks.length = 0;
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
        const key = (dataset as { segmentKey?: 'p1' | 'p2' }).segmentKey;
        const meta = chart.getDatasetMeta(i);
        if (!key || meta.hidden) return;
        const bars = meta.data as unknown as { y: number }[];
        const points = actionSeries[key];
        const dir = key === 'p1' ? -1 : 1; // p1 bars grow towards smaller pixel y
        const inset = -dir * (weight / 2); // keep the border stroke inside the bar
        const gap = 2 * weight; // closest two lines may sit before we drop one
        const minHeight = Math.max(dev(minBarPx), weight);
        const halfPlot = Math.abs(dev(key === 'p1' ? area.top : area.bottom) - axis);
        const untimedEdge = axis + dir * Math.max(dev(untimedPx), Math.round(halfPlot * untimedShare));
        const untimed: [number, number][] = [];
        const offScale: [number, number][] = [];
        let selected: [number, number, number] | undefined; // left, right, top of the selected turn
        const plotEdge = dev(key === 'p1' ? area.top : area.bottom);
        const sideBlurTurns = blurTurns[key];
        const markY = (t: number) => Math.min(Math.max(t, dev(area.top + blurRadius)), dev(area.bottom - blurRadius));
        ctx.fillStyle = key === 'p1' ? p1Fill : p2Fill;
        ctx.strokeStyle = barBorderColor(key);
        ctx.beginPath();
        for (let start = 0; start < bars.length;) {
          let end = start;
          if (!points[start]) break;
          while (end + 1 < bars.length && points[end + 1]?.turn === points[start].turn) end++;
          const span = turnSpan[key].get(points[start].turn);
          const left = dev(xScale.getPixelForValue(span?.[0] ?? points[start].x - 0.5));
          const right = dev(xScale.getPixelForValue(span?.[1] ?? points[end].x + 0.5));
          let top = dev(bars[end].y);
          if (untimedTurns.has(points[start].turn)) {
            if (isFinite(left) && isFinite(right)) {
              untimed.push([left, right]);
              if (points[start].turn === selectedTurn) selected = [left, right, untimedEdge];
              if (sideBlurTurns.has(points[start].turn))
                blurMarks.push({ x: css((left + right) / 2), y: css(markY(untimedEdge)), r: blurRadius, key });
            }
            start = end + 1;
            continue;
          }
          if (isFinite(left) && isFinite(right) && isFinite(top)) {
            if (Math.abs(top - axis) < minHeight) top = axis + dir * minHeight;
            const band = (seg: [number, number]): [number, number] => {
              const from = axis + (top - axis) * seg[0];
              const to = axis + (top - axis) * seg[1];
              const grow = Math.max(0, minHeight - Math.abs(to - from)) / 2;
              return [
                Math.max(Math.min(top, axis), Math.min(from, to) - grow),
                Math.min(Math.max(top, axis), Math.max(from, to) + grow),
              ];
            };
            if (sideBlurTurns.has(points[start].turn))
              blurMarks.push({ x: css((left + right) / 2), y: css(markY(top)), r: blurRadius, key });
            const actionHalf = dev(blurActionRadius);
            const markX = (px: number) => {
              const lo = left + actionHalf;
              const hi = right - actionHalf;
              return css(lo < hi ? Math.min(Math.max(px, lo), hi) : (left + right) / 2);
            };
            if (end > start)
              for (let j = start; j <= end; j++) {
                if (!points[j]?.blur) continue;
                const [y0, y1] = band(points[j].seg);
                blurMarks.push({
                  x: markX(dev(xScale.getPixelForValue(points[j].x))),
                  y: css(markY((y0 + y1) / 2)),
                  r: blurActionRadius,
                  key,
                });
              }
            ctx.fillRect(css(left), css(Math.min(top, axis)), css(right - left), css(Math.abs(top - axis)));
            if (dir * (top - plotEdge) > 0) offScale.push([left, right]);
            if (points[start].turn === selectedTurn) {
              selected = [left, right, top];
              // Tint the slice of the bar the board is on, so stepping through a turn shows which
              // share of its time is being read. A brief action would be a hairline, so give it the
              // same floor as a whole bar, grown about its centre and kept inside the bar.
              const cur = points.slice(start, end + 1).find(pt => pt.ply === atPly);
              if (cur) {
                const [y0, y1] = band(cur.seg);
                ctx.save();
                ctx.fillStyle = orangeAccent;
                ctx.globalAlpha = 0.45;
                ctx.fillRect(css(left), css(y0), css(right - left), css(y1 - y0));
                ctx.restore();
              }
            }
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
        // The whole turn is what a click selects, so outline the whole bar rather than leave the
        // ply line to mark one action inside it. Same accent as that line, so both read as "here".
        if (selected) {
          const [left, right, top] = selected;
          ctx.save();
          ctx.strokeStyle = orangeAccent;
          ctx.lineWidth = css(2 * weight);
          ctx.strokeRect(
            css(left + weight),
            css(Math.min(top, axis)),
            css(right - left - 2 * weight),
            css(Math.abs(top - axis)),
          );
          ctx.restore();
        }
        // A turn far longer than the rest runs past the top of the scale, and the clip takes the
        // segment that would close its outline — leaving two sides rising to nothing. Cap it at the
        // boundary in the font colour, a shade brighter than the bar borders: the bar then reads as
        // capped rather than merely cut off. The tooltip still gives the turn's real duration.
        if (offScale.length) {
          ctx.save();
          ctx.strokeStyle = fontColor;
          ctx.lineWidth = css(2 * weight);
          ctx.beginPath();
          const capY = plotEdge - dir * weight; // inset by half the stroke, or the clip halves it
          for (const [left, right] of offScale) {
            ctx.moveTo(css(left + weight / 2), css(capY));
            ctx.lineTo(css(right - weight / 2), css(capY));
          }
          ctx.stroke();
          ctx.restore();
        }
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
      if (!blurMarks.length) return;
      const ctx = chart.ctx;
      ctx.save();
      ctx.lineWidth = 1.5;
      for (const mark of blurMarks) {
        ctx.fillStyle = mark.key === 'p1' ? '#555555' : '#bbbbbb';
        ctx.strokeStyle = mark.key === 'p1' ? '#aaaaaa' : '#444444';
        ctx.beginPath();
        ctx.rect(mark.x - mark.r, mark.y - mark.r, mark.r * 2, mark.r * 2);
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

  const blurDatasets = (stacked ? [] : (['p1', 'p2'] as ('p1' | 'p2')[]))
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
    plugins: stacked ? [turnBars, pinKeeper] : [pinKeeper],
    options: {
      maintainAspectRatio: false,
      responsive: true,
      // The painted bar is wider than the one-ply action bars behind it, so hit test on the
      // nearest ply rather than requiring the pointer to land inside one, as the acpl chart does.
      ...(stacked ? { interaction: { mode: 'nearest' as const, axis: 'x' as const, intersect: false } } : {}),
      animations: animation(
        800 / Math.max(1, stacked ? Math.max(actionSeries.p1.length, actionSeries.p2.length) : labels.length - 1),
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
              (stacked
                ? labelByTurn.get((items[0].raw as Partial<ActionPoint>)?.turn ?? -1)
                : labels[items[0].parsed.x]) ?? '',
            label: ctx => (ctx.raw as Partial<ActionPoint>)?.actionLabel ?? '',
          },
        },
      },
      onHover(_event, elements, chart) {
        chart.canvas.style.cursor = elements.length ? 'pointer' : 'default';
      },
      onClick(_event, elements, chart) {
        if (elements[0]) {
          const pt = (
            chart.data.datasets[elements[0].datasetIndex]?.data as
              { x: number; ply?: number; turn?: number }[] | undefined
          )?.[elements[0].index];
          if (pt?.x === undefined) return;
          if (!stacked) return playstrategy.pubsub.emit('analysis.chart.click', pt.x);
          // Land after the whole turn wherever in the bar the click fell, matching single-action
          // variants: there a turn is one ply and clicking its bar shows the position it produced.
          // Selecting the pointed action instead would land mid-turn, and the actions are spread
          // horizontally while the bar's dividers read vertically, so it never matches the segment
          // the pointer is over anyway.
          if (pt.turn !== undefined && pt.turn === selectedTurn) {
            // Clicking the selected bar again dismisses the marker and its pinned tooltip. The
            // board stays where it is: this undoes the highlight, not the navigation that set it.
            selectedTurn = -1;
            showPinned();
            chart.update('none');
            return;
          }
          const landing = pt.turn === undefined ? undefined : landingPlyByTurn.get(pt.turn);
          const target = landing ?? pt.ply ?? Math.round(pt.x);
          playstrategy.pubsub.emit('analysis.chart.click', target);
        }
      },
    },
  }) as Chart & { selectPly(ply: number): void };

  chart.selectPly = selectPly.bind(chart);

  let bgDicePicker = false;

  const pinnedPly = (): number | undefined => {
    if (!stacked) return atPly;
    // Off the selected turn — the dice picker marks the next one, where the board is not — there
    // is no current action, so fall back to the one a click would land on.
    return turnByPly.get(atPly) === selectedTurn ? atPly : landingPlyByTurn.get(selectedTurn);
  };

  showPinned = () => {
    const ply = pinnedPly();
    // Both series carry the ply as x, so one lookup serves either.
    const series = stacked ? actionSeries : moveSeries;
    const at = (side: 'p1' | 'p2') => series[side].findIndex(p => p.x === ply);
    const ds = ply === undefined ? -1 : at('p1') >= 0 ? 0 : at('p2') >= 0 ? 1 : -1;
    const i = ds < 0 ? -1 : at(ds ? 'p2' : 'p1');
    if (i < 0) return chart.tooltip?.setActiveElements([], { x: 0, y: 0 });
    const el = chart.getDatasetMeta(ds).data[i] as unknown as { x: number; y: number } | undefined;
    chart.tooltip?.setActiveElements([{ datasetIndex: ds, index: i }], { x: el?.x ?? 0, y: el?.y ?? 0 });
  };

  const applySelection = () => {
    const base = turnByPly.get(atPly) ?? -1;
    // With the picker open the board still sits at the end of a turn, but the dice being chosen
    // belong to the next one, so that is the bar to mark. At the root there is no current turn,
    // and -1 + 1 lands on the first — which is exactly what the opening roll picks for.
    const turn = bgDicePicker ? base + 1 : base;
    selectedTurn = landingPlyByTurn.has(turn) ? turn : -1;
  };

  playstrategy.pubsub.on('analysis.change', (_fen: string, _path: string, ply: Ply | false) => {
    const at = ply === false ? firstPly : ply;
    // jump() emits this from showGround, at its start, while afterJump only recomputes the picker
    // at its end. Carrying the flag over would shift the selection a turn ahead for the position
    // we just left. Take the picker as closed on arrival; afterJump re-announces it if it reopens.
    if (at !== atPly) bgDicePicker = false;
    atPly = at;
    applySelection();
    chart.selectPly(atPly);
    showPinned();
    chart.update('none');
  });

  playstrategy.pubsub.on('analysis.bg.dicepicker', (active: boolean, ply?: number) => {
    if (!isBackgammon) return;
    bgDicePicker = active;
    if (ply !== undefined) atPly = ply; // analysis.change may still be throttled
    applySelection();
    showPinned();
    chart.update('none');
  });
  playstrategy.pubsub.emit('analysis.change.trigger');

  // Game duration label
  const duration = plyCentis.reduce((s, v) => s + v, 0);
  const label = document.createElement('div');
  label.className = 'game-duration';
  label.textContent = trans.noarg('duration') + ' ' + formatClock(duration);
  el.parentElement?.appendChild(label);
}
