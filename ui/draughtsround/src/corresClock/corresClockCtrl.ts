import { Seconds, Millis } from '../clock/clockCtrl';
import RoundController from '../ctrl';

export interface CorresClockData {
  daysPerTurn: number;
  increment: Seconds;
  p1: Seconds;
  p2: Seconds;
  showBar: boolean;
}

export interface CorresClockController {
  root: RoundController;
  data: CorresClockData;
  timePercent(playerIndex: PlayerIndex): number;
  update(p1: Seconds, p2: Seconds): void;
  tick(playerIndex: PlayerIndex): void;
  millisOf(playerIndex: PlayerIndex): Millis;
}

interface Times {
  p1: Millis;
  p2: Millis;
  lastUpdate: Millis;
}

export function ctrl(root: RoundController, data: CorresClockData, onFlag: () => void): CorresClockController {
  const timePercentDivisor = 0.1 / data.increment;

  const timePercent = (playerIndex: PlayerIndex): number =>
    Math.max(0, Math.min(100, times[playerIndex] * timePercentDivisor));

  let times: Times;

  function update(p1: Seconds, p2: Seconds): void {
    times = {
      p1: p1 * 1000,
      p2: p2 * 1000,
      lastUpdate: performance.now(),
    };
  }
  update(data.p1, data.p2);

  function tick(playerIndex: PlayerIndex): void {
    const now = performance.now();
    times[playerIndex] -= now - times.lastUpdate;
    times.lastUpdate = now;
    if (times[playerIndex] <= 0) onFlag();
  }

  const millisOf = (playerIndex: PlayerIndex): Millis => Math.max(0, times[playerIndex]);

  return {
    root,
    data,
    timePercent,
    millisOf,
    update,
    tick,
  };
}
