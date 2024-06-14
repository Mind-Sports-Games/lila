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
  singleWin: 40,
  gammonWin: 41,
  backgammonWin: 42,
  resignGammon: 43,
  resignBackgammon: 44,
  ruleOfGin: 45,
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
