import { RoundData, Step } from './interfaces';

export const firstPly = (d: RoundData): number => d.steps[0].ply;

export const lastPly = (d: RoundData): number => lastStep(d).ply;

export const lastTurn = (d: RoundData): number => lastStep(d).turnCount;

export const lastStep = (d: RoundData): Step => d.steps[d.steps.length - 1];

export const plyStep = (d: RoundData, ply: number): Step => d.steps[ply - firstPly(d)];

export const massage = (d: RoundData): void => {
  if (d.clock) {
    d.clock.showTenths = d.pref.clockTenths;
    d.clock.showBar = d.pref.clockBar;
  }

  if (d.correspondence) d.correspondence.showBar = d.pref.clockBar;

  if (['horde', 'crazyhouse', 'shogi', 'minishogi', 'amazons'].includes(d.game.variant.key))
    d.pref.showCaptured = false;

  if (d.expirationAtStart) d.expirationAtStart.updatedAt = Date.now() - d.expirationAtStart.idleMillis;
  if (d.expirationOnPaused) d.expirationOnPaused.updatedAt = Date.now() - d.expirationOnPaused.idleMillis;
};
