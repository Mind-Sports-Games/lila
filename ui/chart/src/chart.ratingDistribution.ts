import {
  Chart,
  Filler,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
  type ChartDataset,
  type Plugin,
} from 'chart.js';
import { chartPalette, fontColor, fontFamily, gridColor, maybeChart, tooltipOpts, withAlpha } from './index';

const playersColor = chartPalette[2];
const cumulativeColor = chartPalette[4];
const myRatingColor = chartPalette[0];

Chart.register(LineController, LinearScale, PointElement, LineElement, Tooltip, Filler);

interface Data {
  freq: number[];
  myRating?: { rating: number; provisional: boolean };
  i18n: I18nDict;
}

const ratingAt = (i: number) => 600 + i * 25;

// Drawn rather than plotted, so the line never turns up in the tooltip or the
// nearest-point search.
function ratingMarker(rating: number, label: string): Plugin<'line'> {
  return {
    id: 'ratingMarker',
    afterDatasetsDraw(chart) {
      const { ctx, chartArea, scales } = chart;
      const x = scales.x.getPixelForValue(rating);
      if (x < chartArea.left || x > chartArea.right) return;
      ctx.save();
      ctx.strokeStyle = myRatingColor;
      ctx.lineWidth = 3;
      ctx.setLineDash([10]);
      ctx.beginPath();
      ctx.moveTo(x, chartArea.top);
      ctx.lineTo(x, chartArea.bottom);
      ctx.stroke();
      const font = fontFamily(12, 'bold');
      ctx.font = `${font.weight} ${font.size}px ${font.family}`;
      ctx.fillStyle = myRatingColor;
      ctx.textBaseline = 'top';
      const fitsRight = x + 6 + ctx.measureText(label).width <= chartArea.right;
      ctx.textAlign = fitsRight ? 'left' : 'right';
      ctx.fillText(label, fitsRight ? x + 6 : x - 6, chartArea.top + 4);
      ctx.restore();
    },
  };
}

export function ratingDistributionChart(el: HTMLCanvasElement, data: Data): void {
  if (maybeChart(el)) return;
  const trans = playstrategy.trans(data.i18n);
  const freq = data.freq;
  const sum = freq.reduce((a, b) => a + b, 0);

  let running = 0;
  const cumulative = freq.map(nb => {
    const pct = sum ? Math.round((running / sum) * 100) : 0;
    running += nb;
    return pct;
  });

  const ctx = el.getContext('2d')!;
  const gradient = ctx.createLinearGradient(0, 0, 0, 400);
  gradient.addColorStop(0, playersColor);
  gradient.addColorStop(1, withAlpha(playersColor, 0));

  const datasets: ChartDataset<'line'>[] = [
    {
      type: 'line',
      label: trans.noarg('players'),
      data: freq.map((nb, i) => ({ x: ratingAt(i), y: nb })),
      borderColor: playersColor,
      backgroundColor: gradient,
      borderWidth: 4,
      fill: true,
      pointRadius: 4,
      pointHoverRadius: 6,
      pointHitRadius: 200,
    },
    {
      type: 'line',
      label: trans.noarg('cumulative'),
      yAxisID: 'y2',
      data: cumulative.map((p, i) => ({ x: ratingAt(i), y: p })),
      borderColor: cumulativeColor,
      borderWidth: 2,
      pointRadius: 1,
      pointHitRadius: 200,
    },
  ];

  const plugins: Plugin<'line'>[] = [];
  if (data.myRating) {
    const { rating, provisional } = data.myRating;
    plugins.push(ratingMarker(rating, `${trans.noarg('yourRating')}: ${rating}${provisional ? '?' : ''}`));
  }

  new Chart<'line'>(el, {
    type: 'line',
    data: { datasets },
    plugins,
    options: {
      locale: document.documentElement.lang,
      maintainAspectRatio: false,
      responsive: true,
      interaction: { mode: 'nearest', axis: 'x', intersect: false },
      scales: {
        x: {
          type: 'linear',
          title: { display: true, text: trans.noarg('glicko2Rating'), color: fontColor, font: fontFamily() },
          ticks: { stepSize: 100, color: fontColor, font: fontFamily(), maxRotation: 45, minRotation: 45 },
          grid: { color: gridColor },
        },
        y: {
          title: { display: true, text: trans.noarg('players'), color: fontColor, font: fontFamily() },
          ticks: { color: fontColor, font: fontFamily() },
          grid: { color: gridColor },
        },
        y2: {
          position: 'right',
          min: 0,
          max: 100,
          title: { display: true, text: trans.noarg('cumulative'), color: fontColor, font: fontFamily() },
          ticks: { color: fontColor, font: fontFamily(), callback: v => `${v}%` },
          grid: { display: false },
        },
      },
      plugins: {
        legend: { display: false },
        tooltip: tooltipOpts({
          displayColors: true,
          callbacks: {
            title: items => `${trans.noarg('glicko2Rating')}: ${items[0].parsed.x}`,
            label: item =>
              item.datasetIndex === 1
                ? `${item.dataset.label}: ${item.parsed.y}%`
                : `${item.dataset.label}: ${item.parsed.y}`,
          },
        }),
      },
    },
  });
}

playstrategy.ratingDistributionChart = (data: Data) => {
  const el = document.querySelector('#rating_distribution_chart canvas') as HTMLCanvasElement | null;
  if (el) ratingDistributionChart(el, data);
};
