import { updateElements } from './clockView';
import { Redraw, RoundData } from '../interfaces';
import * as game from 'game';

export type Seconds = number;
export type Centis = number;
export type Millis = number;

interface ClockOpts {
  onFlag(): void;
  redraw: Redraw;
  soundPlayerIndex?: PlayerIndex;
  nvui: boolean;
}

export type BaseClockData = {
  byoyomi: undefined | Seconds;
  running: boolean;
  initial: Seconds;
  increment: Seconds;
  delay: undefined | Seconds;
  delayType: undefined | string;
  p1: Seconds;
  p2: Seconds;
  p1Pending: Seconds;
  p2Pending: Seconds;
  emerg: Seconds;
  showTenths: Prefs.ShowClockTenths;
  showBar: boolean;
  moretime: number;
};

export type FischerClockData = BaseClockData;

export type ByoyomiClockData = BaseClockData & {
  byoyomi: Seconds;
  periods: number;
  p1Periods: number;
  p2Periods: number;
};

export type BronsteinDelayData = BaseClockData & {
  delay: Seconds;
  delayType: 'bronstein';
};

export type SimpleDelayData = BaseClockData & {
  delay: Seconds;
  delayType: 'usdelay';
};

export type ClockData = FischerClockData | ByoyomiClockData | BronsteinDelayData | SimpleDelayData;

export const isByoyomi = (clock: ClockData): clock is ByoyomiClockData => {
  return clock.byoyomi !== undefined;
};

export const isFischer = (clock: ClockData): clock is FischerClockData => {
  return clock.byoyomi === undefined;
};

export const isBronstein = (clock: ClockData): clock is BronsteinDelayData => {
  return clock.delayType === 'bronstein';
};

export const isSimpleDelay = (clock: ClockData): clock is SimpleDelayData => {
  return clock.delayType === 'usdelay';
};

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
  lowtime(): void;
  nextPeriod(): void;
  tick(): void;
  byoTicks?: number;
  next?: number;
  delay: Millis;
  playable: {
    p1: boolean;
    p2: boolean;
  };
}

export class ByoyomiCtrlData {
  byoyomi: number;
  initial: number;

  totalPeriods: number;
  curPeriods: PlayerIndexMap<number> = { p1: 0, p2: 0 };

  byoEmergeS: Seconds;
}

export class ClockController {
  emergSound: EmergSound = {
    lowtime: () => playstrategy.sound.play('lowTime'),
    nextPeriod: () => playstrategy.sound.play('period'),
    tick: () => playstrategy.sound.play('tick'),
    delay: 20000,
    playable: {
      p1: true,
      p2: true,
    },
  };

  showTenths: (millis: Millis) => boolean;
  showBar: boolean;
  times: Times;

  barTime: PlayerIndexMap<number>;
  timeRatioDivisor: PlayerIndexMap<number>;
  emergMs: Millis;

  elements = {
    p1: {},
    p2: {},
  } as PlayerIndexMap<ClockElements>;

  byoyomiData?: ByoyomiCtrlData;
  countdownDelay?: Millis;
  pendingTime: PlayerIndexMap<Millis> = { p1: 0, p2: 0 };
  delay?: Millis;
  goneBerserk: PlayerIndexMap<boolean> = { p1: false, p2: false };

  private tickCallback?: number;

  constructor(d: RoundData, readonly opts: ClockOpts) {
    const cdata = d.clock!;

    if (cdata.showTenths === Prefs.ShowClockTenths.Never) this.showTenths = () => false;
    else {
      const cutoff = cdata.showTenths === Prefs.ShowClockTenths.Below10Secs ? 10000 : 3600000;
      this.showTenths = time => time < cutoff;
    }

    if (isSimpleDelay(cdata)) {
      this.countdownDelay = cdata.delay;
    }
    if (isSimpleDelay(cdata) || isBronstein(cdata)) {
      this.delay = cdata.delay;
    }

    if (isByoyomi(cdata)) {
      this.byoyomiData = new ByoyomiCtrlData();
      this.byoyomiData.byoyomi = cdata.byoyomi;
      this.byoyomiData.initial = cdata.initial;

      this.byoyomiData.totalPeriods = cdata.periods;
      this.byoyomiData.curPeriods['p1'] = cdata.p1Periods ?? 0;
      this.byoyomiData.curPeriods['p2'] = cdata.p2Periods ?? 0;

      this.byoyomiData.byoEmergeS = Math.max(3, this.byoyomiData.byoyomi * 0.1);
    }

    this.goneBerserk[d.player.playerIndex] = !!d.player.berserk;
    this.goneBerserk[d.opponent.playerIndex] = !!d.opponent.berserk;

    this.showBar = cdata.showBar && !this.opts.nvui;
    const delayOrIncrement = cdata.increment || cdata.delay || 0;
    this.barTime = {
      p1: 1000 * (Math.max(cdata.initial, 2) + 5 * delayOrIncrement),
      p2: 1000 * (Math.max(cdata.initial, 2) + 5 * delayOrIncrement),
    };
    if (isByoyomi(cdata) && this.isUsingByo(d.player.playerIndex)) {
      this.barTime[d.player.playerIndex] = 1000 * cdata.byoyomi;
    }
    if (isByoyomi(cdata) && this.isUsingByo(d.opponent.playerIndex)) {
      this.barTime[d.opponent.playerIndex] = 1000 * cdata.byoyomi;
    }
    this.timeRatioDivisor = {
      p1: 1 / this.barTime['p1'],
      p2: 1 / this.barTime['p1'],
    };

    this.emergMs = 1000 * Math.min(60, Math.max(10, cdata.initial * 0.125));

    this.setClock(d, cdata.p1, cdata.p2, cdata.p1Pending, cdata.p2Pending);
    if (isByoyomi(cdata)) {
      this.setClock(d, cdata.p1, cdata.p2, cdata.p1Pending, cdata.p2Pending, cdata.p1Periods, cdata.p2Periods);
    } else {
      this.setClock(d, cdata.p1, cdata.p2, cdata.p1Pending, cdata.p2Pending);
    }
  }

  isUsingByo = (playerIndex: PlayerIndex): boolean => {
    if (!this.byoyomiData) return false;
    return (
      this.byoyomiData.byoyomi > 0 && (this.byoyomiData.curPeriods[playerIndex] > 0 || this.byoyomiData.initial === 0)
    );
  };

  timeRatio = (millis: number, playerIndex: PlayerIndex): number =>
    Math.min(1, millis * this.timeRatioDivisor[playerIndex]);

  setClock = (
    d: RoundData,
    p1: Seconds,
    p2: Seconds,
    p1Pending: Seconds,
    p2Pending: Seconds,
    p1Per = 0,
    p2Per = 0,
    delay: Centis = 0
  ) => {
    const isClockRunning = game.playable(d) && (game.playedTurns(d) > 1 || d.clock!.running),
      delayMs = delay * 10;

    this.times = {
      p1: p1 * 1000,
      p2: p2 * 1000,
      activePlayerIndex: isClockRunning ? d.game.player : undefined,
      lastUpdate: performance.now() + delayMs,
    };
    this.pendingTime['p1'] = p1Pending;
    this.pendingTime['p2'] = p2Pending;
    if (this.byoyomiData) {
      this.byoyomiData.curPeriods['p1'] = p1Per;
      this.byoyomiData.curPeriods['p2'] = p2Per;
    }

    if (isClockRunning) this.scheduleTick(this.times[d.game.player], delayMs);
  };

  setBerserk = (playerIndex: PlayerIndex): void => {
    this.goneBerserk[playerIndex] = true;
  };

  addTime = (playerIndex: PlayerIndex, time: Centis): void => {
    this.times[playerIndex] += time * 10;
  };

  nextPeriod = (playerIndex: PlayerIndex): void => {
    if (this.byoyomiData) {
      this.byoyomiData.curPeriods[playerIndex] += 1;
      this.times[playerIndex] += this.byoyomiData.byoyomi * 1000;
      if (this.opts.soundPlayerIndex === playerIndex) this.emergSound.nextPeriod();
      this.emergSound.byoTicks = undefined;
    }
  };

  stopClock = (): Millis | void => {
    const playerIndex = this.times.activePlayerIndex;
    if (playerIndex) {
      const curElapse = this.elapsed();
      this.times[playerIndex] = Math.max(0, this.times[playerIndex] - curElapse);
      this.times.activePlayerIndex = undefined;
      this.emergSound.byoTicks = undefined;
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
    if (
      millis === 0 &&
      this.byoyomiData &&
      !this.goneBerserk[playerIndex] &&
      this.byoyomiData.byoyomi > 0 &&
      this.byoyomiData.curPeriods[playerIndex] < this.byoyomiData.totalPeriods
    ) {
      this.barTime[playerIndex] = 1000 * this.byoyomiData.byoyomi;
      this.timeRatioDivisor[playerIndex] = 1 / this.barTime[playerIndex];
      this.nextPeriod(playerIndex);
      this.opts.redraw();
    } else if (millis === 0) this.opts.onFlag();
    else updateElements(this, this.elements[playerIndex], millis, playerIndex);

    if (this.opts.soundPlayerIndex === playerIndex) {
      if (this.emergSound.playable[playerIndex]) {
        if (
          millis < this.emergMs &&
          !(now < this.emergSound.next!) &&
          (!this.byoyomiData || (this.byoyomiData && this.byoyomiData.curPeriods[playerIndex] === 0))
        ) {
          this.emergSound.lowtime();
          this.emergSound.next = now + this.emergSound.delay;
          this.emergSound.playable[playerIndex] = false;
        }
      } else if (millis > 1.5 * this.emergMs) {
        this.emergSound.playable[playerIndex] = true;
      }
      if (this.byoyomiData) {
        if (
          this.byoyomiData.byoyomi >= 5 &&
          millis > 0 &&
          ((this.emergSound.byoTicks === undefined && millis < this.byoyomiData.byoEmergeS * 1000) ||
            (this.emergSound.byoTicks && Math.floor(millis / 1000) < this.emergSound.byoTicks)) &&
          this.isUsingByo(playerIndex)
        ) {
          this.emergSound.byoTicks = Math.floor(millis / 1000);
          this.emergSound.tick();
        }
      }
    }
  };

  elapsed = (now = performance.now()) => Math.max(0, now - this.times.lastUpdate);

  millisOf = (playerIndex: PlayerIndex): Millis =>
    this.times.activePlayerIndex === playerIndex
      ? Math.max(0, this.times[playerIndex] - this.elapsed())
      : this.times[playerIndex];

  delayMillisOf = (playerIndex: PlayerIndex, activePlayerInGame: PlayerIndex): Millis => {
    const isBerserk = this.goneBerserk[playerIndex];
    const countDown = isBerserk ? 0 : this.countdownDelay ?? 0;
    const delayMillis = 1000 * countDown;
    return this.isNotOpponentsTurn(playerIndex) && playerIndex === activePlayerInGame
      ? Math.max(0, delayMillis - (this.elapsed() + this.pendingMillisOf(playerIndex)))
      : delayMillis;
  };

  pendingMillisOf = (playerIndex: PlayerIndex): Millis => {
    const pendingSeconds = this.pendingTime[playerIndex] ?? 0;
    return 1000 * pendingSeconds;
  };

  isInDelay = (playerIndex: PlayerIndex, isStoppedBetweenPlayerActions = false): boolean => {
    return (
      !!this.delay &&
      (this.isRunning() || isStoppedBetweenPlayerActions) &&
      this.elapsed() + this.pendingMillisOf(playerIndex) <= 1000 * this.delay &&
      !this.goneBerserk[playerIndex]
    );
  };

  isNotInDelay = (playerIndex: PlayerIndex, isStoppedBetweenPlayerActions = false): boolean =>
    !!this.delay &&
    (this.isRunning() || isStoppedBetweenPlayerActions) &&
    this.elapsed() + this.pendingMillisOf(playerIndex) > 1000 * this.delay &&
    !this.goneBerserk[playerIndex];

  isRunning = () => this.times.activePlayerIndex !== undefined;

  isNotOpponentsTurn = (playerIndex: PlayerIndex) =>
    this.times.activePlayerIndex === undefined || this.times.activePlayerIndex === playerIndex;
}
