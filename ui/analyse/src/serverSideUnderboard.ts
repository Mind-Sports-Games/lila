import type Highcharts from 'highcharts';

import AnalyseCtrl from './ctrl';
import { baseUrl } from './util';
import { allowFishnetForVariant } from 'stratutils';
import { defined } from 'common';
import modal from 'common/modal';
import { formToXhr } from 'common/xhr';
import { AnalyseData } from './interfaces';
import bgWinChart from './bgWinChart';

interface PlyChart extends Highcharts.ChartObject {
  lastPly?: Ply | false;
}

export default function (element: HTMLElement, ctrl: AnalyseCtrl) {
  $(element).replaceWith(ctrl.opts.$underboard!);

  $('#adv-chart').attr('id', 'acpl-chart');

  const data = ctrl.data,
    $panels = $('.analyse__underboard__panels > div'),
    $menu = $('.analyse__underboard__menu'),
    $timeChart = $('#movetimes-chart'),
    inputFen = document.querySelector('.analyse__underboard__fen') as HTMLInputElement,
    unselect = (chart: Highcharts.ChartObject) => {
      chart.getSelectedPoints().forEach(function (point) {
        point.select(false);
      });
    };
  let lastFen: string;

  // TODO(bg-analysis): minimal backgammon win% wiring. The colleague owns the real
  // chart; this makes the basics work — request shows an "analysing" spinner,
  // completion (socket "bgAnalysisProgress" -> pubsub) draws the graph, and a
  // stored analysis is drawn on load.
  const isBackgammon = data.game.variant.key === 'backgammon';
  // bg-analysis: backgammon has many board actions per turn, so the chart's
  // ply-based point selection doesn't fit; track the selected dot by the current
  // node's mainline index instead.
  let bgLastIdx = -2;

  if (!playstrategy.AnalyseNVUI) {
    playstrategy.pubsub.on('analysis.comp.toggle', (v: boolean) => {
      setTimeout(function () {
        (v ? $menu.find('[data-panel="computer-analysis"]') : $menu.find('span:eq(1)')).trigger('mousedown');
      }, 50);
    });
    playstrategy.pubsub.on('analysis.change', (fen: Fen, _, mainlinePly: Ply | false) => {
      const $chart = $('#acpl-chart');
      if (fen && fen !== lastFen) {
        inputFen.value = fen;
        lastFen = fen;
      }
      if ($chart.length) {
        const chart: PlyChart = ($chart[0] as any).highcharts;
        if (chart) {
          if (isBackgammon) {
            // TODO(bg-analysis): "current action" dot. Backgammon has ~370 points (one
            // per board action), dense enough that Highcharts auto-hides markers, so
            // point.select() draws nothing. Instead force a marker onto just the current
            // point (located by mainline index — chess's ply index doesn't fit
            // multi-action turns) and clear the previously-marked one.
            const idx = ctrl.onMainline ? ctrl.mainline.indexOf(ctrl.node) - 1 : -1;
            if (idx !== bgLastIdx) {
              const pts = chart.series[0].data;
              if (bgLastIdx >= 0 && pts[bgLastIdx]) pts[bgLastIdx].update({ marker: { enabled: false } }, false);
              const point = idx >= 0 ? pts[idx] : undefined;
              if (defined(point))
                point.update({ marker: { enabled: true, radius: 4, lineWidth: 1, lineColor: '#d85000', fillColor: '#d85000' } }, true);
              else chart.redraw();
              bgLastIdx = idx;
            }
          } else if (mainlinePly != chart.lastPly) {
            if (mainlinePly === false) unselect(chart);
            else {
              //TODO fix for multiaction when analysis is supported also change /public/javascripts/chart/acpl.js
              const point = chart.series[0].data[mainlinePly - 1 - data.game.startedAtTurn];
              if (defined(point)) point.select();
              else unselect(chart);
            }
            chart.lastPly = mainlinePly;
          }
        }
      }
      if ($timeChart.length) {
        const chart: PlyChart = ($timeChart[0] as any).highcharts;
        if (chart) {
          if (mainlinePly != chart.lastPly) {
            if (mainlinePly === false) unselect(chart);
            else {
              const pointIndex =
                chart.series[0].data.map(p => p.x).indexOf(mainlinePly - 1) === -1
                  ? chart.series[1].data.map(p => p.x).indexOf(mainlinePly - 1)
                  : chart.series[0].data.map(p => p.x).indexOf(mainlinePly - 1);
              const p1 = chart.series[0].data.map(p => p.x).indexOf(mainlinePly - 1) !== -1;
              const serie = p1 ? 0 : 1;
              const point = chart.series[serie].data[pointIndex];
              if (defined(point)) point.select();
              else unselect(chart);
            }
          }
          chart.lastPly = mainlinePly;
        }
      }
    });
    playstrategy.pubsub.on('analysis.server.progress', (d: AnalyseData) => {
      if (!playstrategy.advantageChart) startAdvantageChart();
      else if (playstrategy.advantageChart.update) playstrategy.advantageChart.update(d);
      if (d.analysis && !d.analysis.partial) $('#acpl-chart-loader').remove();
    });
    // TODO(bg-analysis): draw/redraw the win% graph when the worker finishes, and
    // once on load if a stored analysis already exists (drawBgWinChart no-ops on 404).
    if (isBackgammon) {
      playstrategy.pubsub.on('analysis.bg.progress', drawBgWinChart);
      drawBgWinChart();
    }
  }

  function chartLoader() {
    return `<div id="acpl-chart-loader"><span>Stockfish 13+<br>server analysis</span>${playstrategy.spinnerHtml}</div>`;
  }
  function startAdvantageChart() {
    if (playstrategy.advantageChart || playstrategy.AnalyseNVUI) return;
    const loading = !data.treeParts[0].eval || !Object.keys(data.treeParts[0].eval).length;
    const $panel = $panels.filter('.computer-analysis');
    if (!$('#acpl-chart').length) $panel.html('<div id="acpl-chart"></div>' + (loading ? chartLoader() : ''));
    else if (loading && !$('#acpl-chart-loader').length) $panel.append(chartLoader());
    playstrategy.loadScriptCJS('javascripts/chart/acpl.js').then(function () {
      playstrategy.advantageChart!(data, ctrl.trans, $('#acpl-chart')[0] as HTMLElement);
    });
  }

  // TODO(bg-analysis): backgammon "analysing" spinner + win% draw. Colleague:
  // replace the placeholder chart (./bgWinChart) and the copy below.
  function showBgAnalysing() {
    $panels
      .filter('.computer-analysis')
      .html(`<div id="acpl-chart"></div><div id="acpl-chart-loader"><span>gnubg analysis</span>${playstrategy.spinnerHtml}</div>`);
  }
  function drawBgWinChart() {
    const panel = $panels.filter('.computer-analysis')[0];
    if (panel) bgWinChart(ctrl, panel as HTMLElement);
  }

  const storage = playstrategy.storage.make('analysis.panel');
  const setPanel = function (panel: string) {
    $menu.children('.active').removeClass('active');
    $menu.find(`[data-panel="${panel}"]`).addClass('active');
    $panels
      .removeClass('active')
      .filter('.' + panel)
      .addClass('active');
    if ((panel == 'move-times' || ctrl.opts.hunter) && !playstrategy.movetimeChart)
      playstrategy.loadScript('javascripts/chart/movetime.js').then(() => playstrategy.movetimeChart(data, ctrl.trans));
    if ((panel == 'computer-analysis' || ctrl.opts.hunter) && $('#acpl-chart').length)
      setTimeout(startAdvantageChart, 200);
  };
  $menu.on('mousedown', 'span', function (this: HTMLElement) {
    const panel = $(this).data('panel');
    storage.set(panel);
    setPanel(panel);
  });
  const stored = storage.get();
  const foundStored =
    stored &&
    $menu.children(`[data-panel="${stored}"]`).filter(function (this: HTMLElement) {
      const display = window.getComputedStyle(this).display;
      return !!display && display != 'none';
    }).length;
  if (foundStored) setPanel(stored!);
  else {
    const $menuCt = $menu.children('[data-panel="ctable"]');
    ($menuCt.length ? $menuCt : $menu.children(':first-child')).trigger('mousedown');
  }
  if (!data.analysis && allowFishnetForVariant(data.game.variant.key)) {
    $panels.find('form.future-game-analysis').on('submit', function (this: HTMLFormElement) {
      if ($(this).hasClass('must-login')) {
        if (confirm(ctrl.trans('youNeedAnAccountToDoThat'))) location.href = '/signup';
        return false;
      }
      // TODO(bg-analysis): backgammon shows its own "analysing" spinner; the win%
      // graph is drawn later when the worker finishes (socket "bgAnalysisProgress").
      formToXhr(this).then(isBackgammon ? showBgAnalysing : startAdvantageChart, playstrategy.reload);
      return false;
    });
  }

  $panels.on('click', '.pgn', function (this: HTMLElement) {
    const selection = window.getSelection(),
      range = document.createRange();
    range.selectNodeContents(this);
    selection!.removeAllRanges();
    selection!.addRange(range);
  });
  $panels.on('click', '.embed-howto', function (this: HTMLElement) {
    const url = `${baseUrl()}/embed/${data.game.id}${location.hash}`;
    const iframe = '<iframe src="' + url + '?theme=auto&bg=auto"\nwidth=600 height=397 frameborder=0></iframe>';
    modal(
      $(
        '<strong style="font-size:1.5em">' +
          $(this).html() +
          '</strong><br /><br />' +
          '<pre>' +
          playstrategy.escapeHtml(iframe) +
          '</pre><br />' +
          iframe +
          '<br /><br />' +
          '<a class="text" data-icon="" href="/developers#embed-game">Read more about embedding games</a>',
      ),
    );
  });
}
