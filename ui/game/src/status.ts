import { BaseGameData } from './interfaces';

// https://github.com/ornicar/scalachess/blob/master/src/main/scala/Status.scala

export const ids = {
  created: 10,
  started: 20,
  aborted: 25,
  mate: 30,
  resign: 31,
  stalemate: 32,
  timeout: 33,
  draw: 34,
  outoftime: 35,
  cheat: 36,
  noStart: 37,
  unknownFinish: 38,
  perpetualCheck: 39,
  singleWin: 40,
  gammonWin: 41,
  backgammonWin: 42,
  resignGammon: 43,
  resignBackgammon: 44,
  ruleOfGin: 45,
  ginGammon: 46,
  ginBackgammon: 47,
  outoftimeGammon: 48,
  outoftimeBackgammon: 49,
  cubeDropped: 50,
  variantEnd: 60,
};

export function started(data: BaseGameData): boolean {
  return data.game.status.id >= ids.started;
}

export function finished(data: BaseGameData): boolean {
  return data.game.status.id >= ids.mate;
}

export function aborted(data: BaseGameData): boolean {
  return data.game.status.id === ids.aborted;
}

export function playing(data: BaseGameData): boolean {
  return started(data) && !finished(data) && !aborted(data);
}

export function isGammon(statusId: number): boolean {
  return [ids.gammonWin, ids.ginGammon, ids.outoftimeGammon, ids.resignGammon].includes(statusId);
}

export function isBackgammon(statusId: number): boolean {
  return [ids.backgammonWin, ids.ginBackgammon, ids.outoftimeBackgammon, ids.resignBackgammon, ids.cubeDropped].includes(statusId);
}
