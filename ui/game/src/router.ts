import { BaseGameData, ContinueMode } from './interfaces';

export function game(data: BaseGameData, playerIndex?: PlayerIndex, embed?: boolean): string;
export function game(data: string, playerIndex?: PlayerIndex, embed?: boolean): string;
export function game(data: any, playerIndex?: PlayerIndex, embed?: boolean): string {
  const id = data.game ? data.game.id : data;
  return (embed ? '/embed/' : '/') + id + (playerIndex ? '/' + playerIndex : '');
}

export function cont(data: BaseGameData, mode: ContinueMode): string {
  return game(data) + '/continue/' + mode;
}
