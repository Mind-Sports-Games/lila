import { Chart, Filler, LineElement, PointElement, RadarController, RadialLinearScale } from 'chart.js';
import { chartPalette, fontColor, fontFamily, maybeChart, withAlpha } from 'chart';

Chart.register(RadarController, RadialLinearScale, PointElement, LineElement, Filler);

const radarColor = chartPalette[7];

interface RadarData {
  radar: {
    labels: string[];
    datasets: {
      label: 'Performance';
      data: number[];
    }[];
  };
}

export function PlayStrategyPuzzleDashboard(data: RadarData) {
  const canvas = document.querySelector('.puzzle-dashboard__radar') as HTMLCanvasElement;
  if (!canvas || maybeChart(canvas)) return; // Defend against missing canvas
  const d = data.radar;
  d.datasets[0] = {
    ...d.datasets[0],
    ...{
      backgroundColor: withAlpha(radarColor, 0.2),
      borderColor: radarColor,
      pointBackgroundColor: radarColor,
    },
  };
  const lineColor = 'rgba(127, 127, 127, .3)';

  new Chart(canvas, {
    type: 'radar',
    data: d,
    options: {
      plugins: {
        legend: { display: false },
      },
      scales: {
        r: {
          beginAtZero: false,
          suggestedMin: Math.min(...d.datasets[0].data) - 100,
          ticks: {
            color: fontColor,
            showLabelBackdrop: false, // hide square behind text
          },
          pointLabels: {
            font: fontFamily(16),
            color: fontColor,
          },
          grid: {
            color: lineColor,
          },
          angleLines: {
            color: lineColor,
          },
        },
      },
    },
  });
}

(window as any).PlayStrategyPuzzleDashboard = PlayStrategyPuzzleDashboard; // esbuild
