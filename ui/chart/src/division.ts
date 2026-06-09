import { chartYMax, chartYMin } from './index';
import type { ChartDataset } from 'chart.js';
import ChartDataLabels from 'chartjs-plugin-datalabels';

interface Division {
  middle?: number;
  end?: number;
}

export default function (div: Division | undefined, trans: Trans): ChartDataset<'line'>[] {
  if (!div) return [];
  const lines: { label: string; loc: number }[] = [];
  if (div.middle) {
    if (div.middle > 1) lines.push({ label: trans.noarg('opening'), loc: 1 });
    lines.push({ label: trans.noarg('middlegame'), loc: div.middle });
  }
  if (div.end) {
    if (div.end > 1 && !div.middle) lines.push({ label: trans.noarg('middlegame'), loc: 0 });
    lines.push({ label: trans.noarg('endgame'), loc: div.end });
  }
  const annotationColor = '#707070';
  return lines.map(line => ({
    type: 'line',
    xAxisID: 'x',
    yAxisID: 'y',
    label: line.label,
    data: [
      { x: line.loc, y: chartYMin },
      { x: line.loc, y: chartYMax },
    ],
    pointHoverRadius: 0,
    borderWidth: 1,
    borderColor: annotationColor,
    pointRadius: 0,
    order: 1,
    datalabels: {
      // chartjs-plugin-datalabels type — cast needed since ChartDataset doesn't know about plugins
      offset: -5,
      align: 45 as any,
      rotation: 90,
      formatter: (val: { y: number }) => (val.y > 0 ? line.label : ''),
      color: annotationColor,
    },
  }));
}

// Re-export plugin so callers can register it once
export { ChartDataLabels };
