import type AnalyseCtrl from './ctrl';
import { baseUrl } from './util';
import { allowFishnetForVariant } from 'stratutils';
import modal from 'common/modal';
import { formToXhr } from 'common/xhr';
import { AnalyseData } from './interfaces';
import bgWinChart from './bgWinChart';

interface AcplChart {
  data: { datasets: any[] };
  setActiveElements(elements: { datasetIndex: number; index: number }[]): void;
  update(mode?: string): void;
  updateData(data: AnalyseData, mainline: Tree.Node[]): void;
}

export default function (element: HTMLElement, ctrl: AnalyseCtrl) {
  $(element).replaceWith(ctrl.opts.$underboard!);

  $('#adv-chart').attr('id', 'acpl-chart');

  const data = ctrl.data,
    $panels = $('.analyse__underboard__panels > div'),
    $menu = $('.analyse__underboard__menu'),
    inputFen = document.querySelector('.analyse__underboard__fen') as HTMLInputElement;
  let lastFen: string;
  let advChart: AcplChart | undefined;
  let timeChartLoaded = false;

  const isBackgammon = data.game.variant.key === 'backgammon';
  let bgLastIdx = -2;

  if (!playstrategy.AnalyseNVUI) {
    playstrategy.pubsub.on('analysis.comp.toggle', (v: boolean) => {
      setTimeout(function () {
        (v ? $menu.find('[data-panel="computer-analysis"]') : $menu.find('span:eq(1)')).trigger('mousedown');
      }, 50);
    });

    playstrategy.pubsub.on('analysis.change', (fen: Fen) => {
      if (fen && fen !== lastFen) {
        inputFen.value = fen;
        lastFen = fen;
      }
      if (isBackgammon && advChart) {
        // The backgammon chart has one point per roll and one per last-checker-move,
        // not one per mainline node. Find the chart point by ply; fall back to the
        // nearest preceding point for intermediate checker moves.
        let idx = -1;
        if (ctrl.onMainline) {
          const ply = ctrl.node.ply;
          const pts = advChart.data.datasets[0]?.data as { x: number }[] | undefined;
          if (pts) {
            idx = pts.findIndex(p => p.x === ply);
            if (idx < 0) {
              for (let i = pts.length - 1; i >= 0; i--) {
                if (pts[i].x < ply) {
                  idx = i;
                  break;
                }
              }
            }
          }
        }
        if (idx !== bgLastIdx) {
          advChart.setActiveElements(idx >= 0 ? [{ datasetIndex: 0, index: idx }] : []);
          advChart.update('none');
          bgLastIdx = idx;
        }
      }
    });

    playstrategy.pubsub.on('analysis.server.progress', (d: AnalyseData) => {
      if (!advChart) startAdvantageChart();
      else advChart.updateData(d, ctrl.mainline);
      if (d.analysis && !d.analysis.partial) $('#acpl-chart-container-loader').remove();
    });

    if (isBackgammon) {
      playstrategy.pubsub.on('analysis.bg.progress', drawBgWinChart);
    }
  }

  function chartLoader() {
    return `<div id="acpl-chart-container-loader"><span>${
      isBackgammon ? 'gnubg analysis' : 'Stockfish 13+<br>server analysis'
    }</span>${playstrategy.spinnerHtml}</div>`;
  }

  function startAdvantageChart() {
    if (advChart || playstrategy.AnalyseNVUI) return;
    const loading = !data.treeParts[0].eval || !Object.keys(data.treeParts[0].eval).length;
    const $panel = $panels.filter('.computer-analysis');
    if (!$('#acpl-chart-container').length) {
      $panel.html(
        '<div id="acpl-chart-container"><canvas id="acpl-chart"></canvas></div>' + (loading ? chartLoader() : ''),
      );
    } else if (loading && !$('#acpl-chart-container-loader').length) {
      $panel.append(chartLoader());
    }
    const el = document.querySelector<HTMLCanvasElement>('#acpl-chart');
    if (!el) return;
    playstrategy.loadModule('chart.game').then(() => {
      advChart = (window as any).PlayStrategyChartGame.acpl(el, data, ctrl.mainline, ctrl.trans);
    });
  }

  function showBgAnalysing() {
    $panels
      .filter('.computer-analysis')
      .html(`<div id="acpl-chart-container"><canvas id="acpl-chart"></canvas></div>${chartLoader()}`);
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
    if ((panel == 'move-times' || ctrl.opts.hunter) && !timeChartLoaded) {
      const el = document.querySelector<HTMLCanvasElement>('#movetimes-chart');
      if (el) {
        timeChartLoaded = true;
        playstrategy.loadModule('chart.game').then(() => {
          (window as any).PlayStrategyChartGame.movetime(el, data, ctrl.trans);
        });
      }
    }
    if (panel == 'computer-analysis' || ctrl.opts.hunter) {
      if (isBackgammon) setTimeout(drawBgWinChart, 200);
      else if ($('#acpl-chart-container').length) setTimeout(startAdvantageChart, 200);
    }
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
          '<a class="text" data-icon="" href="/developers#embed-game">Read more about embedding games</a>',
      ),
    );
  });
}
