import { ArcElement, Chart, DoughnutController, type ChartConfiguration, type ChartType } from 'chart.js';
import { fontColor, fontFamily, gridColor, maybeChart } from './index';

Chart.register(DoughnutController, ArcElement);

// chart.js has no gauge type, so a half doughnut carries the coloured bands and a
// plugin draws the needle over it.
declare module 'chart.js' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface PluginOptionsByType<TType extends ChartType> {
    needle?: { value: number; label: string };
  }
}

const maxLag = 750;
const bands: [number, string][] = [
  [500, '#55BF3B'],
  [650, '#DDDF0D'],
  [maxLag, '#DF5353'],
];

const needle = {
  id: 'needle',
  afterDatasetDraw(chart: Chart) {
    const opts = chart.options.plugins?.needle;
    const arc = chart.getDatasetMeta(0).data[0] as unknown as {
      x: number;
      y: number;
      outerRadius: number;
      innerRadius: number;
    };
    if (!opts || !arc) return;
    const { ctx } = chart;
    const value = Math.max(0, Math.min(maxLag, opts.value));
    const angle = Math.PI * (1 + value / maxLag);

    ctx.save();
    ctx.fillStyle = fontColor;
    ctx.translate(arc.x, arc.y);
    ctx.rotate(angle);
    ctx.beginPath();
    ctx.moveTo(0, -3);
    ctx.lineTo(arc.outerRadius * 0.88, 0);
    ctx.lineTo(0, 3);
    ctx.closePath();
    ctx.fill();
    ctx.restore();

    ctx.save();
    ctx.fillStyle = fontColor;
    ctx.beginPath();
    ctx.arc(arc.x, arc.y, 5, 0, 2 * Math.PI);
    ctx.fill();

    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    const font = fontFamily(13, 'bold');
    ctx.font = `${font.weight} ${font.size}px ${font.family}`;
    ctx.fillText(opts.label, arc.x, arc.y + 12);
    const value_ = fontFamily(16, 'bold');
    ctx.font = `${value_.weight} ${value_.size}px ${value_.family}`;
    ctx.fillText(`${Math.round(opts.value)} ms`, arc.x, arc.y + 28);
    ctx.restore();
  },
};

function gauge(el: HTMLCanvasElement, label: string): Chart {
  const existing = maybeChart(el);
  if (existing) return existing;
  const config: ChartConfiguration<'doughnut'> = {
    type: 'doughnut',
    data: {
      labels: bands.map(([to]) => `< ${to} ms`),
      datasets: [
        {
          data: bands.map(([to], i) => to - (i ? bands[i - 1][0] : 0)),
          backgroundColor: bands.map(([, color]) => color),
          borderColor: gridColor,
          borderWidth: 1,
        },
      ],
    },
    options: {
      rotation: 270,
      circumference: 180,
      cutout: '70%',
      maintainAspectRatio: false,
      responsive: true,
      plugins: {
        legend: { display: false },
        tooltip: { enabled: false },
        needle: { value: 0, label },
      },
    },
    plugins: [needle],
  };
  return new Chart(el, config);
}

export function initModule(): void {
  const server = document.querySelector('.server .meter canvas') as HTMLCanvasElement | null;
  const network = document.querySelector('.network .meter canvas') as HTMLCanvasElement | null;
  if (!server || !network) return;

  const charts = {
    server: gauge(server, 'SERVER'),
    network: gauge(network, 'PING'),
  };
  const values = { server: -1, network: -1 };

  const set = (key: 'server' | 'network', value: number) => {
    values[key] = value;
    const chart = charts[key];
    chart.options.plugins!.needle!.value = value;
    chart.update('none');
    if (values.server === -1 || values.network === -1) return;
    const answer =
      values.server <= 100 && values.network <= 500 ? 'nope-nope' : values.server <= 100 ? 'nope-yep' : 'yep';
    $('.lag .answer span')
      .addClass('none')
      .parent()
      .find('.' + answer)
      .removeClass('none');
  };

  playstrategy.StrongSocket.firstConnect.then(() => playstrategy.socket.send('moveLat', true));
  playstrategy.pubsub.on('socket.in.mlat', (d: string) => set('server', parseInt(d)));
  setInterval(() => set('network', Math.round(playstrategy.socket.averageLag)), 1000);
}

playstrategy.load.then(initModule);
