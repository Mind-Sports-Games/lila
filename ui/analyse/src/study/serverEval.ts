import AnalyseCtrl from '../ctrl';
import { h, VNode } from 'snabbdom';
import { Prop, prop } from 'common';
import { spinner, bind, onInsert } from '../util';

interface AcplChart {
  selectPly(ply: number): void;
  updateData(data: any, mainline: Tree.Node[]): void;
}

export interface ServerEvalCtrl {
  requested: Prop<boolean>;
  root: AnalyseCtrl;
  chapterId(): string;
  request(): void;
  onMergeAnalysisData(): void;
  chartEl: Prop<HTMLCanvasElement | null>;
  reset(): void;
  lastPly: Prop<number | false>;
}

export function ctrl(root: AnalyseCtrl, chapterId: () => string): ServerEvalCtrl {
  const requested = prop(false),
    lastPly = prop<number | false>(false),
    chartEl = prop<HTMLCanvasElement | null>(null);

  playstrategy.pubsub.on('analysis.change', (_fen: string, _path: string, mainlinePly: number | false) => {
    const lp = lastPly(typeof mainlinePly === 'undefined' ? lastPly() : mainlinePly);
    const chart: AcplChart | undefined = (chartEl() as any)?.__acplChart;
    if (chart) {
      chart.selectPly(lp === false ? root.tree.root.ply : (lp as number));
    } else lastPly(false);
  });

  return {
    root,
    reset() {
      requested(false);
      lastPly(false);
    },
    chapterId,
    onMergeAnalysisData() {
      const chart: AcplChart | undefined = (chartEl() as any)?.__acplChart;
      if (chart) chart.updateData(root.data, root.mainline);
    },
    request() {
      root.socket.send('requestAnalysis', chapterId());
      requested(true);
    },
    requested,
    lastPly,
    chartEl,
  };
}

export function view(ctrl: ServerEvalCtrl): VNode {
  const analysis = ctrl.root.data.analysis;

  if (!ctrl.root.showComputer()) return disabled();
  if (!analysis) return ctrl.requested() ? requested() : requestButton(ctrl);

  return h(
    'div.study__server-eval.ready.' + analysis.id,
    {
      hook: onInsert(container => {
        ctrl.lastPly(false);
        const canvas = document.createElement('canvas');
        canvas.id = 'acpl-chart';
        container.appendChild(canvas);
        playstrategy.requestIdleCallback(
          () =>
            playstrategy.loadModule('chart.game').then(() => {
              const chart = (window as any).PlayStrategyChartGame.acpl(
                canvas,
                ctrl.root.data,
                ctrl.root.mainline,
                ctrl.root.trans,
              );
              (canvas as any).__acplChart = chart;
              ctrl.chartEl(canvas);
            }),
          800,
        );
      }),
    },
    [h('div.study__message', spinner())],
  );
}

function disabled(): VNode {
  return h('div.study__server-eval.disabled.padded', 'You disabled computer analysis.');
}

function requested(): VNode {
  return h('div.study__server-eval.requested.padded', spinner());
}

function requestButton(ctrl: ServerEvalCtrl) {
  const root = ctrl.root;
  return h(
    'div.study__message',
    root.mainline.length < 5
      ? h('p', root.trans.noarg('theChapterIsTooShortToBeAnalysed'))
      : !root.study!.members.canContribute()
        ? [root.trans.noarg('onlyContributorsCanRequestAnalysis')]
        : [
            h('p', [
              root.trans.noarg('getAFullComputerAnalysis'),
              h('br'),
              root.trans.noarg('makeSureTheChapterIsComplete'),
            ]),
            h(
              'a.button.text',
              {
                attrs: {
                  'data-icon': '',
                  disabled: root.mainline.length < 5,
                },
                hook: bind('click', ctrl.request, root.redraw),
              },
              root.trans.noarg('requestAComputerAnalysis'),
            ),
          ],
  );
}
