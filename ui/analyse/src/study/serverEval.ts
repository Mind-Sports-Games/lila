import AnalyseCtrl from '../ctrl';
import { h, VNode } from 'snabbdom';
import { Prop, prop } from 'common';
import { spinner, bind } from '../util';

interface AcplChart {
  selectPly(ply: number): void;
  updateData(data: any, mainline: Tree.Node[]): void;
  destroy(): void;
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

  return h('div.study__server-eval.ready.' + analysis.id, [
    ctrl.chartEl() ? null : h('div.study__message', spinner()),
    h('canvas#acpl-chart', {
      hook: {
        insert: vnode => {
          const canvas = vnode.elm as HTMLCanvasElement;
          ctrl.lastPly(false);
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
                ctrl.root.redraw();
              }),
            800,
          );
        },
        destroy: vnode => {
          const canvas = vnode.elm as HTMLCanvasElement;
          const chart: AcplChart | undefined = (canvas as any).__acplChart;
          chart?.destroy();
          if (ctrl.chartEl() === canvas) ctrl.chartEl(null);
        },
      },
    }),
  ]);
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
