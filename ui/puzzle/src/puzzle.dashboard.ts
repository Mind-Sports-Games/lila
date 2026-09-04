import { Chart, Filler, LineElement, PointElement, RadarController, RadialLinearScale } from 'chart.js';
import { fontColor, fontFamily, maybeChart } from 'chart';

Chart.register(RadarController, RadialLinearScale, PointElement, LineElement, Filler);

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
      backgroundColor: 'rgba(189,130,35,0.2)',
      borderColor: 'rgba(189,130,35,1)',
      pointBackgroundColor: 'rgb(189,130,35,1)',
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
