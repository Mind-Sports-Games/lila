import {
  Chart,
  Filler,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
  type ChartDataset,
} from 'chart.js';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import { colorSeries, fontColor, fontFamily, gridColor, maybeChart, tooltipOpts } from './index';

Chart.register(LineController, LinearScale, PointElement, LineElement, Tooltip, Filler, ChartDataLabels);

interface Data {
  freq: number[];
  myRating?: number;
  i18n: I18nDict;
}

const ratingAt = (i: number) => 600 + i * 25;

function marker(rating: number, label: string, max: number): ChartDataset<'line'> {
  return {
    type: 'line',
    label,
    data: [
      { x: rating, y: 0 },
      { x: rating, y: max },
    ],
    borderColor: colorSeries[2],
    borderWidth: 3,
    pointRadius: 0,
    pointHoverRadius: 0,
    segment: { borderDash: () => [10] },
    datalabels: {
      display: 'auto',
      align: 'top',
      color: colorSeries[2],
      font: fontFamily(12, 'bold'),
      formatter: (v: { y: number }) => (v.y === 0 ? '' : label),
    },
  } as ChartDataset<'line'>;
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
  gradient.addColorStop(0, colorSeries[1]);
  gradient.addColorStop(1, 'rgba(119,152,191,0)');

  const datasets: ChartDataset<'line'>[] = [
    {
      type: 'line',
      label: trans.noarg('players'),
      data: freq.map((nb, i) => ({ x: ratingAt(i), y: nb })),
      borderColor: colorSeries[1],
      backgroundColor: gradient,
      borderWidth: 4,
      fill: true,
      pointRadius: 4,
      pointHoverRadius: 6,
      pointHitRadius: 200,
      datalabels: { display: false },
    },
    {
      type: 'line',
      label: trans.noarg('cumulative'),
      yAxisID: 'y2',
      data: cumulative.map((p, i) => ({ x: ratingAt(i), y: p })),
      borderColor: colorSeries[10],
      borderWidth: 2,
      pointRadius: 1,
      pointHitRadius: 200,
      datalabels: { display: false },
    },
  ];

  if (data.myRating) datasets.push(marker(data.myRating, trans.noarg('yourRating'), Math.max(...freq)));

  new Chart(el, {
    type: 'line',
    data: { datasets },
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
