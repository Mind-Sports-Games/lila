import 'chartjs-adapter-dayjs-4';
import {
  Chart,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  TimeScale,
  Tooltip,
  type ChartConfiguration,
  type ChartDataset,
  type Plugin,
  type PointStyle,
} from 'chart.js';
import zoomPlugin from 'chartjs-plugin-zoom';
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import { PipsMode, create as createSlider, type API as NoUiSlider } from 'nouislider';
import { fontColor, fontFamily, gridColor, hoverBorderColor, maybeChart, tooltipBgColor, tooltipOpts } from './index';

Chart.register(LineController, LinearScale, TimeScale, PointElement, LineElement, Tooltip, zoomPlugin);
Chart.defaults.font = fontFamily();
dayjs.extend(utc);

interface Serie {
  name: string;
  points: [number, number, number, number][];
}

const oneDay = 86400000;
const dashStyles: number[][] = [[], [6, 3], [2, 2], [10, 5]];
const pointStyles: PointStyle[] = ['circle', 'triangle', 'rectRot', 'rect', 'rectRounded'];
const seriesColor = (i: number) => `hsl(${Math.round((i * 360) / 30) % 360}, 80%, 50%)`;

const dateFormat = (() => {
  let formatter: (ts: number) => string;
  return () =>
    (formatter ??= (() => {
      try {
        const df = new Intl.DateTimeFormat(document.documentElement.lang, {
          month: 'short',
          day: '2-digit',
          year: 'numeric',
          timeZone: 'UTC',
        });
        return (ts: number) => df.format(new Date(ts));
      } catch {
        return (ts: number) => new Date(ts).toLocaleDateString();
      }
    })());
})();

// Every series is sampled on one shared grid of dates, so index N means the same
// day in all of them — that is what lets a single hover report every variant at
// once, the way Highstock's shared tooltip did. Outside a variant's own span the
// value is null, so each line still starts and stops where its games do.
function fill(
  points: [number, number, number, number][],
  step: number,
  gridStart: number,
  gridEnd: number,
): { x: number; y: number | null }[] {
  const stamped = points.map(p => ({ x: Date.UTC(p[0], p[1], p[2]), y: p[3] }));
  const lastPlayed = stamped.length ? stamped[stamped.length - 1].x : -Infinity;
  const out: { x: number; y: number | null }[] = [];
  let i = 0;
  let last: number | null = null;
  for (let d = gridStart; d <= gridEnd; d += oneDay * step) {
    while (i < stamped.length && stamped[i].x <= d) last = stamped[i++].y;
    out.push({ x: d, y: d > lastPlayed ? null : last });
  }
  return out;
}

function datasets(
  data: Serie[],
  singlePerfName: string | undefined,
  step: number,
  gridStart: number,
  gridEnd: number,
): ChartDataset<'line'>[] {
  return data
    .map((serie, i) => ({ serie, i }))
    .filter(({ serie }) => !singlePerfName || serie.name === singlePerfName)
    .map(({ serie, i }) => ({
      type: 'line' as const,
      label: serie.name,
      // chart.js renders a null y as a gap, but its Point type does not admit one.
      data: fill(serie.points, step, gridStart, gridEnd) as unknown as ChartDataset<'line'>['data'],
      borderColor: seriesColor(i),
      backgroundColor: seriesColor(i),
      borderDash: dashStyles[i % dashStyles.length],
      borderCapStyle: 'round',
      borderJoinStyle: 'round',
      pointStyle: pointStyles[i % pointStyles.length],
      borderWidth: 2,
      pointRadius: 0,
      pointHoverRadius: 5,
      pointHoverBorderWidth: 2,
      pointHoverBackgroundColor: '#fff',
      pointHoverBorderColor: seriesColor(i),
      normalized: true,
    }));
}

// A vertical guide at the hovered position, so a value on one line is easy to read
// off against the others.
const crosshair: Plugin<'line'> = {
  id: 'crosshair',
  afterDraw(chart) {
    const active = chart.getActiveElements();
    if (!active.length) return;
    const { ctx, chartArea } = chart;
    const x = active[0].element.x;
    ctx.save();
    ctx.beginPath();
    ctx.setLineDash([4, 4]);
    ctx.lineWidth = 1;
    ctx.strokeStyle = hoverBorderColor;
    ctx.moveTo(x, chartArea.top);
    ctx.lineTo(x, chartArea.bottom);
    ctx.stroke();
    ctx.restore();
  },
};

function roundedRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

// hsl(h, 80%, 50%): yellow/green hues read lightest, so keep their badge text dark.
function textColorFor(hsl: string): string {
  const hue = Number(/hsl\((\d+)/.exec(hsl)?.[1] ?? 0);
  return hue > 40 && hue < 170 ? '#1a1a1a' : '#fff';
}

interface EndLabelBox {
  x: number;
  y: number;
  w: number;
  h: number;
  label: string;
  text: string;
  color: string;
}

// Per-chart state for endLabels, keyed off the instance rather than the plugin
// object, so the plugin stays reentrant if more than one chart is ever on a page.
const endLabelState = new WeakMap<Chart<'line'>, { boxes: EndLabelBox[]; hovered?: EndLabelBox }>();

// Rating badges pinned to the right edge, one per visible line, showing the value
// at the current right edge of the view — so panning updates them like a readout.
// Hovering one shows which series it is, since the badge alone is just a number.
const endLabelWidth = 44;
const idealBadgeGap = 15;
const denseGapThreshold = 13;
const maxTooltipRows = 14;

const endLabels: Plugin<'line'> = {
  id: 'endLabels',
  afterDraw(chart) {
    const { ctx, chartArea, scales } = chart;
    const xMax = scales.x.max;
    const xMin = scales.x.min;
    const entries: { y: number; text: string; color: string; label: string }[] = [];
    chart.data.datasets.forEach((ds, i) => {
      if (chart.getDatasetMeta(i).hidden) return;
      const points = ds.data as unknown as { x: number; y: number | null }[];
      // Last rating this variant actually held within the visible window, skipping
      // the nulls it carries outside its own span.
      let last: { x: number; y: number } | undefined;
      for (let j = points.length - 1; j >= 0; j--) {
        if (points[j].x <= xMax && points[j].y !== null && points[j].y !== undefined) {
          last = points[j] as { x: number; y: number };
          break;
        }
      }
      // A line that ended before this window starts is not on screen, so it gets
      // no badge either.
      if (!last || last.x < xMin) return;
      entries.push({
        y: scales.y.getPixelForValue(last.y),
        text: String(Math.round(last.y)),
        color: ds.borderColor as string,
        label: (ds.label as string) ?? '',
      });
    });

    const state = endLabelState.get(chart) ?? { boxes: [] };
    if (!entries.length) {
      state.boxes = [];
      endLabelState.set(chart, state);
      return;
    }

    entries.sort((a, b) => a.y - b.y);
    const available = chartArea.bottom - chartArea.top;
    const fitsAtValue = entries.length * idealBadgeGap <= available;

    if (fitsAtValue) {
      // Few enough lines that each badge can sit at its true rating height; only
      // nudge apart the odd local collision.
      for (let i = 1; i < entries.length; i++) {
        if (entries[i].y - entries[i - 1].y < idealBadgeGap) entries[i].y = entries[i - 1].y + idealBadgeGap;
      }
      const overflow = entries[entries.length - 1].y - chartArea.bottom;
      if (overflow > 0) entries.forEach(e => (e.y -= overflow));
      if (entries[0].y < chartArea.top) {
        const under = chartArea.top - entries[0].y;
        entries.forEach(e => (e.y += under));
      }
    } else {
      // Too many lines to place at their true value height without piling up:
      // spread them evenly instead. Relative order (so highest-to-lowest ranking)
      // survives; exact vertical position no longer maps to the rating, which is
      // why the value stays on the badge and the name is a hover away.
      const gap = available / entries.length;
      entries.forEach((e, i) => (e.y = chartArea.top + gap * (i + 0.5)));
    }

    const gap = fitsAtValue ? idealBadgeGap : available / entries.length;
    const dense = gap < denseGapThreshold;

    const boxes: EndLabelBox[] = [];
    ctx.save();
    if (dense) {
      // Below readable pill size: a dot carries the colour only, value and name
      // surface together on hover.
      const radius = Math.max(2, Math.min(4, gap / 2 - 1));
      entries.forEach(({ y, color, text, label }) => {
        const x = chartArea.right + 6 + radius;
        ctx.beginPath();
        ctx.fillStyle = color;
        ctx.arc(x, y, radius, 0, Math.PI * 2);
        ctx.fill();
        const pad = 4;
        boxes.push({
          x: x - radius - pad,
          y: y - radius - pad,
          w: radius * 2 + pad * 2,
          h: radius * 2 + pad * 2,
          label,
          text,
          color,
        });
      });
    } else {
      const pillHeight = Math.min(15, Math.max(10, gap - 2));
      const font = fontFamily(pillHeight >= 14 ? 11 : 9, 'bold');
      ctx.font = `${font.weight} ${font.size}px ${font.family}`;
      ctx.textBaseline = 'middle';
      ctx.textAlign = 'center';
      entries.forEach(({ y, text, color, label }) => {
        const pillWidth = Math.max(ctx.measureText(text).width + 10, endLabelWidth - 6);
        const x = chartArea.right + 6;
        roundedRect(ctx, x, y - pillHeight / 2, pillWidth, pillHeight, pillHeight / 2);
        ctx.fillStyle = color;
        ctx.fill();
        ctx.fillStyle = textColorFor(color);
        ctx.fillText(text, x + pillWidth / 2, y + 0.5);
        boxes.push({ x, y: y - pillHeight / 2, w: pillWidth, h: pillHeight, label, text, color });
      });
    }
    ctx.restore();

    state.boxes = boxes;
    state.hovered = boxes.find(b => b.label === state.hovered?.label);
    endLabelState.set(chart, state);

    if (state.hovered) {
      const { hovered } = state;
      const content = dense ? `${hovered.label}: ${hovered.text}` : hovered.label;
      ctx.save();
      const padding = 7;
      const height = 22;
      const labelFont = fontFamily(12, 'bold');
      ctx.font = `${labelFont.weight} ${labelFont.size}px ${labelFont.family}`;
      const width = ctx.measureText(content).width + padding * 2;
      // Anchor to the left of the badge stack so it never runs off the canvas edge.
      const bx = Math.max(chartArea.left, hovered.x - width - 8);
      const by = Math.min(Math.max(hovered.y + hovered.h / 2 - height / 2, chartArea.top), chartArea.bottom - height);
      roundedRect(ctx, bx, by, width, height, 4);
      ctx.fillStyle = tooltipBgColor;
      ctx.fill();
      ctx.strokeStyle = hovered.color;
      ctx.lineWidth = 1.5;
      ctx.stroke();
      ctx.fillStyle = fontColor;
      ctx.textAlign = 'left';
      ctx.fillText(content, bx + padding, by + height / 2 + 0.5);
      ctx.restore();
    }
  },
  afterEvent(chart, args) {
    const state = endLabelState.get(chart);
    if (!state) return;
    const { event } = args;
    if (event.type === 'mouseout') {
      if (state.hovered) {
        state.hovered = undefined;
        chart.canvas.style.cursor = 'default';
        args.changed = true;
      }
      return;
    }
    if (event.type !== 'mousemove' || event.x === null || event.y === null) return;
    const hit = state.boxes.find(
      b => event.x! >= b.x && event.x! <= b.x + b.w && event.y! >= b.y && event.y! <= b.y + b.h,
    );
    if ((hit?.label ?? null) === (state.hovered?.label ?? null)) return;
    state.hovered = hit;
    chart.canvas.style.cursor = hit ? 'pointer' : 'default';
    args.changed = true;
  },
};

const ranges: [string, (end: dayjs.Dayjs) => dayjs.Dayjs][] = [
  ['1m', end => end.subtract(1, 'month')],
  ['3m', end => end.subtract(3, 'month')],
  ['6m', end => end.subtract(6, 'month')],
  ['YTD', end => end.startOf('year')],
  ['1y', end => end.subtract(1, 'year')],
  ['all', end => end],
];

export function ratingHistoryChart(el: HTMLElement, data: Serie[], singlePerfName?: string): void {
  const canvas = el.querySelector('canvas') as HTMLCanvasElement | null;
  const sliderEl = el.querySelector('.time-range-slider') as HTMLElement | null;
  const buttonsEl = el.querySelector('.time-selector-buttons .btn-rack') as HTMLElement | null;
  if (!canvas || maybeChart(canvas)) return;

  const shown = data.filter(s => !singlePerfName || s.name === singlePerfName);
  if (!shown.length || shown.every(s => !s.points.length)) {
    el.style.display = 'none';
    return;
  }

  const all = shown.flatMap(s => s.points.map(p => Date.UTC(p[0], p[1], p[2])));
  const minDate = Math.min(...all);
  const maxDate = Math.max(...all);
  // A variant played on a single day gives a zero-width range. noUiSlider divides
  // its margin option by (max - min) internally, so a real 0 there sends that
  // division to Infinity and the slider never finishes laying out — pad the range
  // by a day so it always has a real span to work with.
  const singleDay = minDate === maxDate;
  const startDate = minDate - (singleDay ? oneDay : 0);
  const endDate = maxDate + (singleDay ? oneDay : 0);

  const steps = [1, 7, 14].map(step => datasets(data, singlePerfName, step, startDate, endDate));
  const stepFor = (span: number) => (span > 4 * 365 * oneDay ? 2 : span > 2 * 365 * oneDay ? 1 : 0);
  let currentStep = -1;

  canvas.style.touchAction = 'pan-y';

  const config: ChartConfiguration<'line'> = {
    type: 'line',
    data: { datasets: [] },
    options: {
      maintainAspectRatio: false,
      responsive: true,
      animation: false,
      normalized: true,
      parsing: false,
      layout: { padding: { left: 4, right: endLabelWidth } },
      // 'index' reports every series at the hovered date, matching the shared
      // tooltip Highstock gave us. It relies on all series sharing one x grid.
      interaction: { mode: 'index', axis: 'x', intersect: false },
      scales: {
        x: { type: 'time', display: false, min: startDate, max: endDate, grid: { display: false } },
        y: {
          ticks: { color: fontColor, font: fontFamily() },
          border: { display: false },
          grid: { color: gridColor, drawTicks: false },
        },
      },
      plugins: {
        legend: { display: false },
        tooltip: tooltipOpts({
          usePointStyle: true,
          displayColors: true,
          boxWidth: 8,
          boxHeight: 8,
          boxPadding: 2,
          rtl: document.dir === 'rtl',
          // Highest rating first, and drop variants with no games yet at this date.
          itemSort: (a, b) => (b.parsed.y ?? 0) - (a.parsed.y ?? 0),
          filter: (item, _i, all) => {
            if (item.parsed.y === null || item.parsed.y === undefined) return false;
            const rated = all.filter(t => t.parsed.y !== null && t.parsed.y !== undefined);
            if (rated.length <= maxTooltipRows) return true;
            // Past a readable height the tooltip would run off the chart, so keep
            // the top rows and let the footer account for the rest.
            const cutoff = rated.map(t => t.parsed.y).sort((a, b) => b - a)[maxTooltipRows - 1];
            return item.parsed.y >= cutoff;
          },
          callbacks: {
            title: items => dateFormat()(items[0].parsed.x),
            label: item => `${item.dataset.label}: ${item.parsed.y}`,
            footer: items => {
              const total = items[0]?.chart.data.datasets.filter((_ds, di) => {
                const p = items[0].chart.data.datasets[di].data[items[0].dataIndex] as {
                  y: number | null;
                } | null;
                return p && p.y !== null && p.y !== undefined;
              }).length;
              const hidden = (total ?? 0) - items.length;
              return hidden > 0 ? `+${hidden} more` : '';
            },
          },
        }),
        zoom: {
          limits: { x: { min: startDate, max: endDate } },
          pan: {
            enabled: true,
            mode: 'x',
            onPanStart: ({ chart }) => {
              toggleEvents(chart, true);
              return true;
            },
            onPan: ({ chart }) => syncSlider(chart),
            onPanComplete: ({ chart }) => toggleEvents(chart, false),
          },
        },
      },
    },
    plugins: [crosshair, endLabels],
  };
  const chart = new Chart(canvas, config);

  // Hovering while dragging is pure cost, so events go away for the duration.
  function toggleEvents(c: Chart, stop: boolean) {
    c.options.events = stop ? [] : undefined;
    if (stop) c.setActiveElements([]);
    c.options.plugins!.tooltip!.enabled = !stop;
  }

  function applyStep(span: number) {
    const step = stepFor(span);
    if (step === currentStep) return;
    currentStep = step;
    chart.data.datasets = steps[step];
  }

  let slider: NoUiSlider | undefined;
  let syncing = false;

  function render(min: number, max: number) {
    applyStep(max - min);
    chart.options.scales!.x!.min = min;
    chart.options.scales!.x!.max = max;
    chart.update('none');
  }

  // Both the slider and panning drive the same range, so guard against them
  // echoing each other back and forth.
  function show(min: number, max: number) {
    render(min, max);
    if (slider && !syncing) {
      syncing = true;
      slider.set([min, max], false, true);
      syncing = false;
    }
  }

  function syncSlider(c: Chart) {
    show(c.scales.x.min, c.scales.x.max);
  }

  if (sliderEl) {
    const years = new Map<number, string>();
    for (let y = dayjs.utc(startDate).year(); y <= dayjs.utc(endDate).year(); y++)
      years.set(Date.UTC(y, 0, 1), String(y));
    slider = createSlider(sliderEl, {
      start: [startDate, endDate],
      connect: true,
      behaviour: 'drag',
      range: { min: startDate, max: endDate },
      step: oneDay * 7,
      margin: oneDay * 7,
      pips: {
        mode: PipsMode.Values,
        values: [...years.keys()].filter((_, i, a) => a.length < 7 || i % 2 === 0) as unknown as number[],
        density: 100,
        format: { to: (v: number) => years.get(v) ?? '', from: Number },
      },
    });
    slider.on('update', (values: (string | number)[]) => {
      if (syncing) return;
      const [min, max] = values.map(Number);
      syncing = true;
      render(min, max);
      syncing = false;
    });
  }

  if (buttonsEl) {
    const end = dayjs.utc(endDate);
    ranges.forEach(([label, from]) => {
      const min = label === 'all' ? startDate : from(end).valueOf();
      if (label !== 'all' && min < startDate) return;
      const btn = document.createElement('button');
      btn.className = 'btn-rack__btn';
      btn.textContent = label;
      btn.addEventListener('click', () => {
        buttonsEl.querySelectorAll('.btn-rack__btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        show(Math.max(min, startDate), endDate);
      });
      buttonsEl.appendChild(btn);
    });
  }

  // Default to the last three months, as upstream does.
  show(Math.max(dayjs.utc(endDate).subtract(3, 'month').valueOf(), startDate), endDate);
  el.querySelector('.spinner')?.remove();
}

playstrategy.ratingHistoryChart = (data: Serie[], singlePerfName?: string) => {
  document
    .querySelectorAll('.rating-history-container')
    .forEach(el => ratingHistoryChart(el as HTMLElement, data, singlePerfName));
};
