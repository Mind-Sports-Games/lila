import { Chart, type ChartDataset, type ChartOptions } from 'chart.js';

export const chartYMax = 1.05;
export const chartYMin: number = -chartYMax;

const lightTheme = document.body.classList.contains('light');
export const orangeAccent = '#d85000';
export const whiteFill: string = lightTheme ? 'rgba(255,255,255,0.7)' : 'rgba(255,255,255,0.3)';
export const blackFill: string = lightTheme ? 'rgba(0,0,0,0.2)' : 'rgba(0,0,0,1)';
export const fontColor: string = lightTheme ? '#2F2F2F' : 'hsl(0, 0%, 73%)';
export const gridColor: string = lightTheme ? '#ccc' : '#404040';
export const hoverBorderColor: string = lightTheme ? gridColor : 'white';
// ColorBrewer "Paired", strong shades first so a chart with few series gets
// distinct hues; the pale partner of each follows six places later.
export const chartPalette = [
  '#e31a1c',
  '#33a02c',
  '#1f78b4',
  '#ff7f00',
  '#6a3d9a',
  '#b15928',
  '#fb9a99',
  '#b2df8a',
  '#a6cee3',
  '#fdbf6f',
  '#cab2d6',
  '#e7298a',
];

// Colour varies faster than line style
const seriesDashes: number[][] = [[], [8, 4], [2, 3], [12, 3, 2, 3]];

export const seriesColor = (i: number): string => chartPalette[i % chartPalette.length];
export const seriesDash = (i: number): number[] =>
  seriesDashes[Math.floor(i / chartPalette.length) % seriesDashes.length];

function rgb(hex: string): [number, number, number] {
  const c = hex.replace('#', '');
  const full = c.length === 3 ? c.replace(/./g, m => m + m) : c;
  const n = parseInt(full, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

// Measuring contrast
export function contrastText(hex: string): string {
  const lin = (v: number) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  const [r, g, b] = rgb(hex);
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b) > 0.4 ? '#1a1a1a' : '#fff';
}

export function withAlpha(hex: string, alpha: number): string {
  const [r, g, b] = rgb(hex);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
// Move quality palette. The values live once, as $c-inaccuracy & friends in
// ui/common/css/theme/_default.scss, and reach us through the custom properties that
// base/_elements.scss sets on body — so charts, the move tree and the advice summary
// cannot drift apart. Read lazily and memoised: getComputedStyle forces a style
// recalculation, and a chart asks for these once per annotated point.
const cssColorCache = new Map<string, string>();
const cssColor = (name: string): string => {
  let value = cssColorCache.get(name);
  if (value === undefined) {
    value = getComputedStyle(document.body).getPropertyValue(name).trim();
    cssColorCache.set(name, value);
  }
  return value;
};
export const moveQuality = {
  get inaccuracy() {
    return cssColor('--c-inaccuracy');
  },
  get mistake() {
    return cssColor('--c-mistake');
  },
  get blunder() {
    return cssColor('--c-blunder');
  },
  get brilliant() {
    return cssColor('--c-brilliant');
  },
  get lucky() {
    return cssColor('--c-lucky');
  },
  get unlucky() {
    return cssColor('--c-unlucky');
  },
};
// A stacked bar's ply separators are stroked in the same path as its outline, so a
// single colour has to read against both the page and the bar's own fill. On dark,
// whiteFill composites down to a mid grey that the light theme's #838383 vanished
// into, which hid the ply boundaries entirely.
export const whiteFillBorder: string = lightTheme ? '#838383' : '#c0c0c0';
export const blackFillBorder = '#616161';

export const tooltipBgColor: string = lightTheme ? 'rgba(255, 255, 255, 0.8)' : 'rgba(22, 21, 18, 0.7)';

// ChartOptions is already a deep partial, so indexing into it gives the shape
// chart.js actually accepts for a tooltip override.
export type TooltipOpts = NonNullable<NonNullable<ChartOptions['plugins']>['tooltip']>;

export function tooltipOpts(overrides: TooltipOpts = {}): TooltipOpts {
  return {
    borderColor: fontColor,
    borderWidth: 1,
    backgroundColor: tooltipBgColor,
    bodyColor: fontColor,
    titleColor: fontColor,
    titleFont: fontFamily(13),
    bodyFont: fontFamily(13),
    caretPadding: 10,
    displayColors: false,
    ...overrides,
  };
}

export function fontFamily(size?: number, weight?: 'bold') {
  return {
    family: "'Noto Sans', 'Lucida Grande', 'Lucida Sans Unicode', Verdana, Arial, Helvetica, sans-serif",
    size: size ?? 12,
    weight,
  };
}

// The acpl and movetime charts sit on top of each other and must keep one x axis. Chart.js
// would otherwise pad the plot for whatever overflows its edge — a blur marker on the last
// ply — on one chart and not the other. Reserve the marker's room here, once, for both.
export const layoutOpts: ChartOptions<'line'>['layout'] = { autoPadding: false, padding: { right: 7 } };
// Lets an edge marker use that room and the axis strip on the left, and nothing more.
export const markerClip = { left: 8, right: 7, top: 0, bottom: 0 };

export const axisOpts = (xmin: number, xmax: number): ChartOptions<'line'>['scales'] => ({
  x: {
    display: false,
    type: 'linear',
    min: xmin,
    max: xmax,
    offset: false,
  },
  y: {
    min: chartYMin,
    max: chartYMax,
    border: { display: false },
    ticks: { display: false },
    grid: {
      color: ctx => (ctx.tick.value === 0 ? (lightTheme ? '#959595' : '#676664') : undefined),
    },
  },
});

export function maybeChart(el: HTMLCanvasElement): Chart | undefined {
  const ctx = el.getContext('2d');
  if (ctx) return Chart.getChart(ctx);
  return undefined;
}

export function plyLine(ply: number): ChartDataset<'line'> {
  return {
    xAxisID: 'x',
    type: 'line',
    label: 'ply',
    data: [
      { x: ply, y: chartYMin },
      { x: ply, y: chartYMax },
    ],
    borderColor: orangeAccent,
    pointRadius: 0,
    pointHoverRadius: 0,
    borderWidth: 1,
    animation: false,
    order: 0,
    datalabels: { display: false },
  };
}

export function selectPly(this: Chart, ply: number): void {
  const index = this.data.datasets.findIndex(d => d.label === 'ply');
  if (index !== -1) this.data.datasets[index] = plyLine(ply);
  this.update('none');
}

export function animation(duration: number): ChartOptions<'line'>['animations'] {
  return {
    x: {
      type: 'number',
      easing: 'easeOutQuad',
      duration,
      from: NaN,
      delay: ctx => (ctx.mode === 'resize' ? 0 : ctx.dataIndex * duration),
    },
    y: {
      type: 'number',
      easing: 'easeOutQuad',
      duration,
      from: ctx =>
        !ctx.dataIndex
          ? ctx.chart.scales.y.getPixelForValue(100)
          : ctx.chart.getDatasetMeta(ctx.datasetIndex).data[ctx.dataIndex - 1].getProps(['y'], true).y,
      delay: ctx => (ctx.mode === 'resize' ? 0 : ctx.dataIndex * duration),
    },
  };
}

// Opposite-color variants show p2 winning at the top (fill/color swapped).
export const oppositeColorVariants = [
  'flipello',
  'flipello10',
  'antiflipello',
  'octagonflipello',
  'shogi',
  'minishogi',
  'abalone',
  'grandabalone',
  'linesOfAction',
  'go9x9',
  'go13x13',
  'go19x19',
];
