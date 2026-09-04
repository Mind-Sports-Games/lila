import {
  CategoryScale,
  Chart,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
  type ChartDataset,
} from 'chart.js';
import { colorSeries, fontColor, fontFamily, gridColor, maybeChart, tooltipOpts } from './index';

Chart.register(LineController, CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend);

interface Data {
  freq: [string, string, number][];
  i18n: I18nDict;
  variantNames?: Record<string, string>;
  gameGroupNames?: Record<string, string>;
}

const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

// One tick per year, at the middle month of that year.
function yearTicks(allMonths: string[]): Map<number, string> {
  const byYear = new Map<string, number[]>();
  allMonths.forEach((ym, i) => {
    const year = ym.split('-')[0];
    if (!byYear.has(year)) byYear.set(year, []);
    byYear.get(year)!.push(i);
  });
  const ticks = new Map<number, string>();
  byYear.forEach((indexes, year) => ticks.set(indexes[Math.floor(indexes.length / 2)], year));
  return ticks;
}

export function libraryChart(el: HTMLCanvasElement, data: Data, allowedVariants?: string[]): void {
  // The page re-invokes this on every filter toggle, and the series count changes,
  // so the previous chart has to go rather than be updated in place.
  maybeChart(el)?.destroy();

  const trans = playstrategy.trans(data.i18n);
  const freq = allowedVariants ? data.freq.filter(row => allowedVariants.includes(row[1])) : data.freq;
  const allMonths = Array.from(new Set(freq.map(row => row[0]))).sort();
  const names = { ...(data.variantNames ?? {}), ...(data.gameGroupNames ?? {}) };

  // Running total per month for each game family, plus its grand total.
  const series = new Map<string, Map<string, number>>();
  const totals = new Map<string, number>();
  freq.forEach(([month, type, count]) => {
    const sum = (totals.get(type) ?? 0) + count;
    totals.set(type, sum);
    if (!series.has(type)) series.set(type, new Map());
    series.get(type)!.set(month, sum);
  });
  // Most played first, so the busiest families are the ones nearest to hand when you
  // click them off the legend to see the rest.
  const types = [...totals.keys()].sort((a, b) => totals.get(b)! - totals.get(a)!);

  const datasets: ChartDataset<'line'>[] = types.map((type, idx) => {
    const cumulative = series.get(type)!;
    return {
      type: 'line',
      label: names[type] || type,
      data: allMonths.map(m => {
        const found = cumulative.get(m);
        return found === undefined ? null : Math.max(found, 1);
      }),
      borderColor: colorSeries[idx % colorSeries.length],
      backgroundColor: colorSeries[idx % colorSeries.length],
      borderWidth: 4,
      pointRadius: idx < colorSeries.length ? 0 : 5,
      pointHoverRadius: 5,
    } as ChartDataset<'line'>;
  });

  const years = yearTicks(allMonths);

  new Chart(el, {
    type: 'line',
    data: { labels: allMonths, datasets },
    options: {
      maintainAspectRatio: false,
      responsive: true,
      interaction: { mode: 'nearest', axis: 'x', intersect: false },
      scales: {
        x: {
          type: 'category',
          title: { display: true, text: trans.noarg('Date'), color: fontColor, font: fontFamily() },
          ticks: {
            color: fontColor,
            font: fontFamily(),
            maxRotation: 30,
            minRotation: 30,
            callback(_v, i) {
              const month = parseInt(allMonths[i]?.split('-')[1] ?? '', 10);
              return months[month - 1] ?? '';
            },
          },
          grid: { color: gridColor },
        },
        x2: {
          type: 'category',
          labels: allMonths,
          position: 'bottom',
          offset: false,
          grid: { display: false, drawTicks: false },
          border: { display: false },
          ticks: {
            color: '#888',
            font: fontFamily(11, 'bold'),
            autoSkip: false,
            callback: (_v, i) => years.get(i) ?? '',
          },
        },
        y: {
          min: 1,
          title: { display: true, text: trans.noarg('Total Games'), color: fontColor, font: fontFamily() },
          ticks: { color: fontColor, font: fontFamily() },
          grid: { color: gridColor },
        },
      },
      plugins: {
        legend: {
          display: true,
          position: 'bottom',
          onHover: (_e, _item, legend) => (legend.chart.canvas.style.cursor = 'pointer'),
          onLeave: (_e, _item, legend) => (legend.chart.canvas.style.cursor = 'default'),
          labels: { color: fontColor, font: fontFamily(), boxWidth: 20 },
        },
        tooltip: tooltipOpts({
          displayColors: true,
          callbacks: { label: item => `${item.dataset.label}: ${item.parsed.y}` },
        }),
      },
    },
  });
}

playstrategy.libraryChart = (data: Data, allowedVariants?: string[]) => {
  const el = document.querySelector('#library_chart canvas') as HTMLCanvasElement | null;
  if (el) libraryChart(el, data, allowedVariants);
};
