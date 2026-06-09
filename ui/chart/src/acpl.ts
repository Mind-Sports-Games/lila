import {
  Chart,
  Filler,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  type ChartConfiguration,
  type ChartDataset,
  type PointStyle,
  Tooltip,
} from 'chart.js';
import {
  animation,
  axisOpts,
  blackFill,
  fontColor,
  fontFamily,
  maybeChart,
  orangeAccent,
  oppositeColorVariants,
  plyLine,
  selectPly,
  tooltipBgColor,
  whiteFill,
} from './index';
import { GameFamily as BackgammonFamily } from 'stratops/variants/backgammon/GameFamily';
import division, { ChartDataLabels } from './division';

Chart.register(LineController, LinearScale, PointElement, LineElement, Tooltip, Filler, ChartDataLabels);

// Plugin that draws locked category dots directly on the canvas so they survive any Chart.js update.
Chart.register({
  id: 'lockedDots',
  afterDatasetsDraw(chart: Chart) {
    const dots = (chart as any)._lockedDots as
      | Array<{ x: number; y: number; color: string; rect?: boolean }>
      | undefined;
    if (!dots?.length) return;
    const xScale = chart.scales['x'];
    const yScale = chart.scales['y'];
    if (!xScale || !yScale) return;
    const ctx = chart.ctx;
    ctx.save();
    for (const dot of dots) {
      if (isNaN(dot.y)) continue;
      const cx = xScale.getPixelForValue(dot.x);
      const cy = yScale.getPixelForValue(dot.y);
      const r = 5;
      ctx.beginPath();
      if (dot.rect) ctx.rect(cx - r, cy - r, r * 2, r * 2);
      else ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = dot.color;
      ctx.fill();
    }
    ctx.restore();
  },
});

export interface AcplChart extends Chart {
  selectPly(ply: number): void;
  updateData(data: AnalyseData, mainline: Tree.Node[]): void;
}

interface AnalyseData {
  game: { variant: { key: string }; division?: { middle?: number; end?: number }; startedAtTurn?: number };
  player: { playerIndex: PlayerIndex; blurs?: { bits?: string } };
  opponent: { blurs?: { bits?: string } };
  analysis?: { partial?: boolean };
  treeParts: Tree.Node[];
}

type GlyphAdvice = 'blunder' | 'mistake' | 'inaccuracy' | 'best' | 'lucky' | 'unlucky';

const glyphProperties = (node: Tree.Node): { advice?: GlyphAdvice; color?: string } => {
  if (node.glyphs?.some(g => g.id === 4)) return { advice: 'blunder', color: '#db3031' };
  if (node.glyphs?.some(g => g.id === 2)) return { advice: 'mistake', color: '#d96420' };
  if (node.glyphs?.some(g => g.id === 6)) return { advice: 'inaccuracy', color: '#4da3d5' };
  if (node.glyphs?.some(g => g.id === 3)) return { advice: 'best', color: '#5ebe5e' };
  if (node.glyphs?.some(g => g.id === 51)) return { advice: 'lucky', color: '#8855cc' };
  if (node.glyphs?.some(g => g.id === 52)) return { advice: 'unlucky', color: '#d45090' };
  return {};
};

const toBlurArray = (player: { blurs?: { bits?: string } }) => player.blurs?.bits?.split('') ?? [];

function moveLabel(node: Tree.Node): string {
  const turn = Math.floor((node.turnCount ?? 0) / 2) + 1;
  const dots = node.playedPlayerIndex === 'p1' ? '.' : '...';
  const san = node.san === 'NOSAN' ? (node.uci ?? '') : (node.san ?? '');
  return turn + dots + ' ' + san;
}

function winToCp(win: number): number {
  const x = Math.min(0.995, Math.max(0.005, win));
  return Math.round(250 * Math.log(x / (1 - x)));
}

function makeDataset(
  data: AnalyseData,
  mainline: Tree.Node[],
): { acpl: ChartDataset<'line'>; moveLabels: string[]; hoverColors: string[] } {
  const isOppositeColor = oppositeColorVariants.includes(data.game.variant.key);
  const p1Fill = isOppositeColor ? blackFill : whiteFill;
  const p2Fill = isOppositeColor ? whiteFill : blackFill;
  const defaultHoverColor = (BackgammonFamily.getVariantKeys() as string[]).includes(data.game.variant.key)
    ? '#4da3d5'
    : orangeAccent;

  const blurs = [toBlurArray(data.player), toBlurArray(data.opponent)];
  if (data.player.playerIndex === 'p1') blurs.reverse();

  const winChances: { x: number; y: number }[] = [];
  const moveLabels: string[] = [];
  const hoverColors: string[] = [];
  const pointStyles: PointStyle[] = [];
  const pointSizes: number[] = [];
  const pointColors: string[] = [];
  const partial = !data.analysis || data.analysis.partial;

  mainline.slice(1).forEach(node => {
    const isP1 = node.playedPlayerIndex === 'p1';
    const blurBit = blurs[isP1 ? 1 : 0].shift();
    const isBlur = !partial && blurBit === '1';

    let cp: number | undefined;
    if (node.eval?.mate) {
      cp = node.eval.mate > 0 ? Infinity : -Infinity;
    } else if (node.san?.includes('#')) {
      cp = isP1 ? Infinity : -Infinity;
      if (data.game.variant.key === 'antichess') cp = -cp!;
    } else if (node.eval?.win !== undefined) {
      // backgammon: eval.win is p1's win probability (already normalised by bgWinChart.ts), no flip needed
      cp = winToCp(node.eval.win);
    } else if (node.eval?.cp !== undefined) {
      cp = node.eval.cp;
    }

    const y = cp !== undefined ? 2 / (1 + Math.exp(-0.004 * cp)) - 1 : NaN;
    winChances.push({ x: node.ply, y });

    const { advice, color: glyphColor } = glyphProperties(node);
    let label = moveLabel(node);
    if (advice) label += ` [${advice}]`;
    if (isBlur) label += ' [blur]';
    moveLabels.push(label);

    hoverColors.push(glyphColor ?? defaultHoverColor);
    const isDiceRoll = node.glyphs?.some(g => g.id === 98) ?? false;
    pointStyles.push(isBlur || isDiceRoll ? 'rect' : 'circle');
    pointSizes.push(isBlur ? 5 : 0);
    pointColors.push(isBlur ? (isP1 ? '#ffffff' : '#333333') : orangeAccent);
  });

  const hasBlurs = data.player.blurs || data.opponent.blurs;
  return {
    acpl: {
      label: 'advantage',
      data: winChances,
      borderWidth: 1,
      fill: {
        target: 'origin',
        below: p2Fill,
        above: p1Fill,
      },
      pointRadius: hasBlurs ? pointSizes : 0,
      pointHoverRadius: 5,
      pointHitRadius: 100,
      borderColor: orangeAccent,
      pointBackgroundColor: pointColors,
      pointHoverBackgroundColor: hoverColors,
      pointStyle: pointStyles,
      hoverBackgroundColor: orangeAccent,
      order: 5,
      datalabels: { display: false },
    },
    moveLabels,
    hoverColors,
  };
}

// Hover/click on blunder/mistake/inaccuracy counts in the advice-summary panel →
// highlight matching moves on the chart. Click locks the highlights and navigates to the
// next occurrence (cycling); clicking a graph dot clears the lock.
// When locked, pointRadius is set per-point so dots stay visible as the mouse moves elsewhere
// (Chart.js's internal hover replaces setActiveElements but never touches pointRadius).
function christmasTree(
  chart: AcplChart,
  mainline: Tree.Node[],
  hoverColors: string[],
  state: { currentPly: number; clearCategoryLock: () => void },
) {
  let lockedSymbol: string | null = null;
  let lockedPi: PlayerIndex | null = null;

  // 'luck' is a virtual symbol that expands to both '+' (lucky) and '-' (unlucky).
  const symbolsFor = (symbol: string): string[] => (symbol === 'luck' ? ['+', '-'] : [symbol]);

  const pointsFor = (symbol: string, pi: PlayerIndex) => {
    const syms = symbolsFor(symbol);
    return mainline
      .slice(1)
      .map((node, idx) => ({ node, idx }))
      .filter(({ node }) => node.glyphs?.some(g => syms.includes(g.symbol)) && node.playedPlayerIndex === pi)
      .map(({ idx }) => ({ datasetIndex: 0, index: idx }));
  };

  const applyHighlight = (symbol: string, pi: PlayerIndex) => {
    const acpl = chart.data.datasets[0] as ChartDataset<'line'>;
    acpl.pointHoverBackgroundColor = hoverColors;
    (acpl as any).pointBorderColor = hoverColors;
    chart.setActiveElements(pointsFor(symbol, pi));
    chart.update('none');
  };

  // Locked dots are drawn via the lockedDots plugin (afterDraw) so they survive any Chart.js update.
  const lockPoints = (symbol: string, pi: PlayerIndex) => {
    // Clear any lingering hover state (the tooltip sticks on mobile after a tap).
    chart.setActiveElements([]);
    chart.tooltip?.setActiveElements([], { x: 0, y: 0 });
    const acpl = chart.data.datasets[0] as ChartDataset<'line'>;
    acpl.pointHoverBackgroundColor = hoverColors;
    (acpl as any).pointBorderColor = hoverColors;
    const pts = pointsFor(symbol, pi);
    const mainData = chart.data.datasets[0].data as { x: number; y: number }[];
    (chart as any)._lockedDots = pts
      .map(({ index }) => ({
        ...(mainData[index] as { x: number; y: number }),
        color: hoverColors[index] ?? orangeAccent,
        rect: mainline.slice(1)[index]?.glyphs?.some((g: Tree.Glyph) => g.id === 98) ?? false,
      }))
      .filter(d => d.x !== undefined);
    chart.update('none');
  };

  const clearHighlight = () => {
    chart.setActiveElements([]);
    chart.tooltip?.setActiveElements([], { x: 0, y: 0 });
    const acpl = chart.data.datasets[0] as ChartDataset<'line'>;
    acpl.pointHoverBackgroundColor = hoverColors;
    (acpl as any).pointBorderColor = hoverColors;
    (chart as any)._lockedDots = [];
    chart.update('none');
  };

  // Returns the next matching ply after state.currentPly, cycling back to the first.
  const nextPlyFor = (symbol: string, pi: PlayerIndex): number | undefined => {
    const syms = symbolsFor(symbol);
    const matches = mainline
      .slice(1)
      .filter(n => n.glyphs?.some(g => syms.includes(g.symbol)) && n.playedPlayerIndex === pi)
      .map(n => n.ply);
    if (!matches.length) return undefined;
    return matches.find(p => p > state.currentPly) ?? matches[0];
  };

  // Expose a way for the chart's onClick to clear the lock without knowing internals.
  state.clearCategoryLock = () => {
    if (!lockedSymbol) return;
    $('div.advice-summary div.symbol.locked').removeClass('locked');
    lockedSymbol = null;
    lockedPi = null;
    clearHighlight();
    playstrategy.pubsub.emit('analysis.chart.category.select', null);
  };

  $('div.advice-summary').on('mouseenter', 'div.symbol', function (this: HTMLElement) {
    if (lockedSymbol) return;
    const symbol = this.getAttribute('data-symbol');
    const pi = this.getAttribute('data-playerindex') as PlayerIndex | null;
    if (symbol && pi) applyHighlight(symbol, pi);
  });

  $('div.advice-summary').on('mouseleave', 'div.symbol', function () {
    if (!lockedSymbol) clearHighlight();
  });

  $('div.advice-summary').on('click', 'div.symbol', function (this: HTMLElement) {
    const symbol = this.getAttribute('data-symbol');
    const pi = this.getAttribute('data-playerindex') as PlayerIndex | null;
    if (!symbol || !pi) return;
    // Switch category if needed, then always navigate to next match.
    if (lockedSymbol !== symbol || lockedPi !== pi) {
      $('div.advice-summary div.symbol.locked').removeClass('locked');
      lockedSymbol = symbol;
      lockedPi = pi;
      $(this).addClass('locked');
      lockPoints(symbol, pi);
      playstrategy.pubsub.emit('analysis.chart.category.select', symbol);
    }
    const ply = nextPlyFor(symbol, pi);
    if (ply !== undefined) playstrategy.pubsub.emit('analysis.chart.click', ply);
  });
}

export default function acpl(el: HTMLCanvasElement, data: AnalyseData, mainline: Tree.Node[], trans: Trans): AcplChart {
  const existing = maybeChart(el);
  if (existing) return existing as AcplChart;

  const dataset = makeDataset(data, mainline);
  const firstPly = mainline[0]?.ply ?? 0;
  const divLines = division(data.game.division, trans);

  const config: ChartConfiguration<'line'> = {
    type: 'line',
    data: {
      labels: dataset.moveLabels.map((_, i) => i),
      datasets: [dataset.acpl, plyLine(firstPly), ...divLines],
    },
    options: {
      interaction: { mode: 'nearest', axis: 'x', intersect: false },
      scales: axisOpts(firstPly + 1, mainline[mainline.length - 1]?.ply ?? mainline.length + firstPly),
      animations: animation(500 / Math.max(1, mainline.length - 1)),
      maintainAspectRatio: false,
      responsive: true,
      plugins: {
        tooltip: {
          borderColor: fontColor,
          borderWidth: 1,
          backgroundColor: tooltipBgColor,
          bodyColor: fontColor,
          titleColor: fontColor,
          titleFont: fontFamily(14, 'bold'),
          bodyFont: fontFamily(13),
          caretPadding: 10,
          displayColors: false,
          filter: item => item.datasetIndex === 0,
          callbacks: {
            label: item => {
              const node = mainline[item.dataIndex + 1];
              const ev = node?.eval;
              if (!ev) return '';
              if (ev.win !== undefined) {
                // backgammon: show leading side's win%, signed
                const white = ev.win * 100;
                const pct = white >= 50 ? white : white - 100;
                return trans('advantage') + ': ' + (pct >= 0 ? '+' : '') + pct.toFixed(1) + '%';
              }
              if (ev.mate) return trans('advantage') + ': #' + ev.mate;
              if (ev.cp !== undefined) {
                const e = Math.max(Math.min(Math.round(ev.cp / 10) / 10, 99), -99);
                return trans('advantage') + ': ' + (e > 0 ? '+' : '') + e;
              }
              return '';
            },
            title: items => (items[0] ? dataset.moveLabels[items[0].dataIndex] : ''),
          },
        },
      },
      onClick(_event, elements) {
        const hit = elements.find(e => e.datasetIndex === 0);
        if (hit) {
          const ply = (chart.data.datasets[0].data as { x: number }[])[hit.index]?.x;
          if (ply !== undefined) {
            state.clearCategoryLock();
            playstrategy.pubsub.emit('analysis.chart.click', ply);
          }
        }
      },
    },
  };

  // Shared state between the chart config (onClick) and christmasTree.
  const state = { currentPly: firstPly, clearCategoryLock: () => {} };

  const chart = new Chart(el, config) as AcplChart;
  chart.selectPly = selectPly.bind(chart);
  chart.updateData = (d: AnalyseData, ml: Tree.Node[]) => {
    const updated = makeDataset(d, ml);
    chart.data.datasets[0] = updated.acpl;
    chart.data.labels = updated.moveLabels.map((_, i) => i);
    if (!d.analysis?.partial) christmasTree(chart, ml, updated.hoverColors, state);
    chart.update('none');
  };

  playstrategy.pubsub.on('analysis.change', (_fen: string, _path: string, ply: Ply | false) => {
    state.currentPly = ply === false ? firstPly : ply;
    chart.selectPly(state.currentPly);
  });
  // Trigger initial selection
  playstrategy.pubsub.emit('analysis.change.trigger');

  if (!data.analysis?.partial) christmasTree(chart, mainline, dataset.hoverColors, state);

  return chart;
}
