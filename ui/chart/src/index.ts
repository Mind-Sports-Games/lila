import { Chart, type ChartDataset, type ChartOptions } from 'chart.js';

export const chartYMax = 1.05;
export const chartYMin: number = -chartYMax;

const lightTheme = document.body.classList.contains('light');
export const orangeAccent = '#d85000';
export const whiteFill: string = lightTheme ? 'rgba(255,255,255,0.7)' : 'rgba(255,255,255,0.3)';
export const blackFill: string = lightTheme ? 'rgba(0,0,0,0.2)' : 'rgba(0,0,0,1)';
export const fontColor: string = lightTheme ? '#2F2F2F' : 'hsl(0, 0%, 73%)';
export const tooltipBgColor: string = lightTheme ? 'rgba(255, 255, 255, 0.8)' : 'rgba(22, 21, 18, 0.7)';

export function fontFamily(size?: number, weight?: 'bold') {
  return {
    family: "'Noto Sans', 'Lucida Grande', 'Lucida Sans Unicode', Verdana, Arial, Helvetica, sans-serif",
    size: size ?? 12,
    weight,
  };
}

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
