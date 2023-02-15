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
  running: boolean;
  initial: Seconds;
  increment: Seconds;
  p1: Seconds;
  p2: Seconds;
  emerg: Seconds;
  showTenths: Prefs.ShowClockTenths;
  showBar: boolean;
  moretime: number;
};

export type FischerClockData = BaseClockData & {
  byoyomi: undefined;
};

export type ByoyomiClockData = BaseClockData & {
  byoyomi: Seconds;
  periods: number;
  p1Periods: number;
  p2Periods: number;
};

export type ClockData = FischerClockData | ByoyomiClockData;

export const isByoyomi = (clock: ClockData): clock is ByoyomiClockData => {
  return clock.byoyomi !== undefined;
};

export const isFischer = (clock: ClockData): clock is FischerClockData => {
  return clock.byoyomi === undefined;
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
  // TODO: byoyomi Why is this duplicated between here and the controller?
  goneBerserk: PlayerIndexMap<boolean> = { p1: false, p2: false };

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

  barTime: number;
  timeRatioDivisor: number;
  emergMs: Millis;

  elements = {
    p1: {},
    p2: {},
  } as PlayerIndexMap<ClockElements>;

  byoyomiData?: ByoyomiCtrlData;

  private tickCallback?: number;

  constructor(d: RoundData, readonly opts: ClockOpts) {
    const cdata = d.clock!;

    if (cdata.showTenths === Prefs.ShowClockTenths.Never) this.showTenths = () => false;
    else {
      const cutoff = cdata.showTenths === Prefs.ShowClockTenths.Below10Secs ? 10000 : 3600000;

      const fischerShowTenths = (time: Millis): boolean => time < cutoff;
      const byoyomiShowTenths = (time: Millis): boolean =>
        time < cutoff &&
        !!this.byoyomiData &&
        (this.byoyomiData.byoyomi === 0 ||
          time <= 1000 ||
          this.isUsingByo(d.player.playerIndex) ||
          cdata.showTenths === Prefs.ShowClockTenths.Always);
      this.showTenths = d.clock && isFischer(cdata) ? fischerShowTenths : byoyomiShowTenths;
      // TODO: I don't fully understand the above if statement and suspect it's not quite right for byoyomi
    }

    if (isByoyomi(cdata)) {
      this.byoyomiData = new ByoyomiCtrlData();
      this.byoyomiData.byoyomi = cdata.byoyomi;
      this.byoyomiData.initial = cdata.initial;

      this.byoyomiData.totalPeriods = cdata.periods;
      this.byoyomiData.curPeriods['p1'] = cdata.p1Periods ?? 0;
      this.byoyomiData.curPeriods['p2'] = cdata.p2Periods ?? 0;

      this.byoyomiData.goneBerserk[d.player.playerIndex] = !!d.player.berserk;
      this.byoyomiData.goneBerserk[d.opponent.playerIndex] = !!d.opponent.berserk;
      // TODO: this byoyomi countdown should be configurable ir based on the size of the byoyomi,
      //       not hard coded like this.
      this.byoyomiData.byoEmergeS = 3;
    }

    this.showBar = cdata.showBar && !this.opts.nvui;
    this.barTime = 1000 * (Math.max(cdata.initial, 2) + 5 * cdata.increment);
    this.timeRatioDivisor = 1 / this.barTime;

    this.emergMs = 1000 * Math.min(60, Math.max(10, cdata.initial * 0.125));

    if (isByoyomi(cdata)) {
      this.setClock(d, cdata.p1, cdata.p1, cdata.p1Periods, cdata.p1Periods);
    } else {
      this.setClock(d, cdata.p1, cdata.p2);
    }
  }

  isUsingByo = (playerIndex: PlayerIndex): boolean => {
    if (!this.byoyomiData) return false;
    return (
      this.byoyomiData.byoyomi > 0 && (this.byoyomiData.curPeriods[playerIndex] > 0 || this.byoyomiData.initial === 0)
    );
  };

  timeRatio = (millis: number): number => Math.min(1, millis * this.timeRatioDivisor);

  //setClock = (d: RoundData, p1: Seconds, p2: Seconds, delay: Centis = 0) => {
  setClock = (d: RoundData, p1: Seconds, p2: Seconds, p1Per = 0, p2Per = 0, delay: Centis = 0) => {
    const isClockRunning = game.playable(d) && (game.playedTurns(d) > 1 || d.clock!.running),
      delayMs = delay * 10;

    this.times = {
      p1: p1 * 1000,
      p2: p2 * 1000,
      activePlayerIndex: isClockRunning ? d.game.player : undefined,
      lastUpdate: performance.now() + delayMs,
    };
    if (this.byoyomiData) {
      this.byoyomiData.curPeriods['p1'] = p1Per;
      this.byoyomiData.curPeriods['p2'] = p2Per;
    }

    if (isClockRunning) this.scheduleTick(this.times[d.game.player], delayMs);
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
      !this.byoyomiData.goneBerserk[playerIndex] &&
      this.byoyomiData.byoyomi > 0 &&
      this.byoyomiData.curPeriods[playerIndex] < this.byoyomiData.totalPeriods
    ) {
      this.nextPeriod(playerIndex);
      this.opts.redraw();
    } else if (millis === 0) this.opts.onFlag();
    else updateElements(this, this.elements[playerIndex], millis, playerIndex);

    if (this.opts.soundPlayerIndex === playerIndex) {
      if (this.emergSound.playable[playerIndex]) {
        if (
          millis < this.emergMs &&
          !(now < this.emergSound.next!) &&
          // TODO: test this out,
          (!this.byoyomiData || (this.byoyomiData && this.byoyomiData.curPeriods[playerIndex] === 0))
        ) {
          //if (millis < this.emergMs && !(now < this.emergSound.next!)) {
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

  isRunning = () => this.times.activePlayerIndex !== undefined;
}
