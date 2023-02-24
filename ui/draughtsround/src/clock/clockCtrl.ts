import { updateElements } from './clockView';
import { RoundData } from '../interfaces';
import * as game from 'game';

export type Seconds = number;
export type Centis = number;
export type Millis = number;

interface ClockOpts {
  onFlag(): void;
  soundPlayerIndex?: PlayerIndex;
  nvui: boolean;
}

export interface ClockData {
  running: boolean;
  initial: Seconds;
  increment: Seconds;
  p1: Seconds;
  p2: Seconds;
  emerg: Seconds;
  showTenths: Prefs.ShowClockTenths;
  showBar: boolean;
  moretime: number;
}

interface Times {
  p1: Millis;
  p2: Millis;
  activePlayerIndex?: PlayerIndex;
  lastUpdate: Millis;
}

type PlayerIndexMap<T> = { [C in PlayerIndex]: T };

export interface ClockElements {
  time?: HTMLElement;
  clock?: HTMLElement;
  bar?: HTMLElement;
  barAnim?: Animation;
}

interface EmergSound {
  play(): void;
  next?: number;
  delay: Millis;
  playable: {
    p1: boolean;
    p2: boolean;
  };
}

export class ClockController {
  emergSound: EmergSound = {
    play: () => playstrategy.sound.play('lowTime'),
    delay: 20000,
    playable: {
      p1: true,
      p2: true,
    },
  };

  showTenths: (millis: Millis) => boolean;
  showBar: boolean;
  times: Times;

  barTime: number;
  timeRatioDivisor: number;
  emergMs: Millis;

  elements = {
    p1: {},
    p2: {},
  } as PlayerIndexMap<ClockElements>;

  private tickCallback?: number;

  constructor(d: RoundData, readonly opts: ClockOpts) {
    const cdata = d.clock!;

    if (cdata.showTenths === Prefs.ShowClockTenths.Never) this.showTenths = () => false;
    else {
      const cutoff = cdata.showTenths === Prefs.ShowClockTenths.Below10Secs ? 10000 : 3600000;
      this.showTenths = time => time < cutoff;
    }

    this.showBar = cdata.showBar && !this.opts.nvui;
    this.barTime = 1000 * (Math.max(cdata.initial, 2) + 5 * cdata.increment);
    this.timeRatioDivisor = 1 / this.barTime;

    this.emergMs = 1000 * Math.min(60, Math.max(10, cdata.initial * 0.125));

    this.setClock(d, cdata.p1, cdata.p2);
  }

  timeRatio = (millis: number): number => Math.min(1, millis * this.timeRatioDivisor);

  setClock = (d: RoundData, p1: Seconds, p2: Seconds, delay: Centis = 0) => {
    const isClockRunning = game.playable(d) && (game.playedTurns(d) > 1 || d.clock!.running),
      delayMs = delay * 10;

    this.times = {
      p1: p1 * 1000,
      p2: p2 * 1000,
      activePlayerIndex: isClockRunning ? d.game.player : undefined,
      lastUpdate: performance.now() + delayMs,
    };

    if (isClockRunning) this.scheduleTick(this.times[d.game.player], delayMs);
  };

  addTime = (playerIndex: PlayerIndex, time: Centis): void => {
    this.times[playerIndex] += time * 10;
  };

  stopClock = (): Millis | void => {
    const playerIndex = this.times.activePlayerIndex;
    if (playerIndex) {
      const curElapse = this.elapsed();
      this.times[playerIndex] = Math.max(0, this.times[playerIndex] - curElapse);
      this.times.activePlayerIndex = undefined;
      return curElapse;
    }
  };

  hardStopClock = (): void => (this.times.activePlayerIndex = undefined);

  private scheduleTick = (time: Millis, extraDelay: Millis) => {
    if (this.tickCallback !== undefined) clearTimeout(this.tickCallback);
    this.tickCallback = setTimeout(
      this.tick,
      // changing the value of active node confuses the chromevox screen reader
      // so update the clock less often
      this.opts.nvui ? 1000 : (time % (this.showTenths(time) ? 100 : 500)) + 1 + extraDelay
    );
  };

  // Should only be invoked by scheduleTick.
  private tick = (): void => {
    this.tickCallback = undefined;

    const playerIndex = this.times.activePlayerIndex;
    if (playerIndex === undefined) return;

    const now = performance.now();
    const millis = Math.max(0, this.times[playerIndex] - this.elapsed(now));

    this.scheduleTick(millis, 0);
    if (millis === 0) this.opts.onFlag();
    else updateElements(this, this.elements[playerIndex], millis);

    if (this.opts.soundPlayerIndex === playerIndex) {
      if (this.emergSound.playable[playerIndex]) {
        if (millis < this.emergMs && !(now < this.emergSound.next!)) {
          this.emergSound.play();
          this.emergSound.next = now + this.emergSound.delay;
          this.emergSound.playable[playerIndex] = false;
        }
      } else if (millis > 1.5 * this.emergMs) {
        this.emergSound.playable[playerIndex] = true;
      }
    }
  };

  elapsed = (now = performance.now()) => Math.max(0, now - this.times.lastUpdate);

  millisOf = (playerIndex: PlayerIndex): Millis =>
    this.times.activePlayerIndex === playerIndex
      ? Math.max(0, this.times[playerIndex] - this.elapsed())
      : this.times[playerIndex];

  isRunning = () => this.times.activePlayerIndex !== undefined;
}
