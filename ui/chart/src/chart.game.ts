import acpl, { type AcplChart } from './acpl';
import movetime from './movetime';

export type { AcplChart };

export interface ChartGame {
  acpl(el: HTMLCanvasElement, data: any, mainline: Tree.Node[], trans: Trans): AcplChart;
  movetime(el: HTMLCanvasElement, data: any, trans: Trans): void;
}

(window as any).PlayStrategyChartGame = { acpl, movetime } satisfies ChartGame;
