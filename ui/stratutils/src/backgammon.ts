import type * as cg from 'chessground/types';
import * as status from 'game/status';
import type { BaseGame } from 'game';

export const isBackgammonVariant = (key: string): boolean =>
  ['backgammon', 'hyper', 'nackgammon'].includes(key);

export const diceRollUci = /^[1-6](\/[1-6])+$/;

export const isDiceRollUci = (uci: string): boolean => diceRollUci.test(uci);

export function readDoublingCube(fen: string, variant: VariantKey): cg.DoublingCube | undefined {
  if (!isBackgammonVariant(variant)) return undefined;
  if (fen.split(' ').length < 8) return undefined;
  const doublingCube = fen.split(' ')[6];
  if (doublingCube === '-') return undefined;
  if (doublingCube === '0') return { value: 0, owner: 'both' };
  if (doublingCube.length == 2 || doublingCube.length == 3) {
    if (doublingCube[1] === 'w' || doublingCube[1] === 'b') return { value: +doublingCube[0] + 1, owner: 'both' };
    else return { value: +doublingCube[0], owner: doublingCube[1] === 'W' ? 'p1' : 'p2' };
  }
  return undefined;
}

export function parsePossibleLifts(line?: string | null): cg.Key[] {
  if (!line) return [];
  return (line.match(/[a-l][1-2]/g) as cg.Key[]) || [];
}

export const finalMultiPointState = (game: BaseGame, ply: any, lastPly: any): cg.MultiPointState | undefined => {
  if (!game.multiPointState) return undefined;

  const pointsToAdd =
    game.pointValue && game.winner && ply == lastPly
      ? game.winner === 'p1'
        ? [game.pointValue, 0]
        : [0, game.pointValue]
      : [0, 0];

  if (status.isOutOfTime(game.status.id) && game.winner && ply == lastPly) {
    if (status.isGin(game.status.id)) {
      if (game.winner === 'p1') {
        if (game.multiPointState.p1 + pointsToAdd[0] < game.multiPointState.target) {
          pointsToAdd[1] += 64;
        }
      } else {
        if (game.multiPointState.p2 + pointsToAdd[1] < game.multiPointState.target) {
          pointsToAdd[0] += 64;
        }
      }
    } else {
      if (game.winner === 'p1') {
        pointsToAdd[0] += 64;
      } else {
        pointsToAdd[1] += 64;
      }
    }
  }

  return {
    target: game.multiPointState.target,
    p1: Math.min(game.multiPointState.target, game.multiPointState.p1 + pointsToAdd[0]),
    p2: Math.min(game.multiPointState.target, game.multiPointState.p2 + pointsToAdd[1]),
  };
};

export function readDice(fen: string, variant: VariantKey, canEndTurn?: boolean, isDescending?: boolean): cg.Dice[] {
  if (!isBackgammonVariant(variant)) return [];
  if (fen.split(' ').length < 2) return [];
  const unusedDice = fen
    .split(' ')[1]
    .replace('-', '')
    .split('/')
    .sort((a, b) => +b - +a);
  const usedDice = fen.split(' ')[2].replace('-', '').split('/');
  const dice = [];
  for (const d of unusedDice) {
    if (+d) dice.push({ value: +d, isAvailable: !(canEndTurn ?? false) });
  }
  for (const d of usedDice) {
    if (+d) dice.push({ value: +d, isAvailable: false });
  }
  if (isDescending !== undefined) {
    if (isDescending) return dice.sort((a, b) => +b.value - +a.value);
    else return dice.sort((a, b) => +a.value - +b.value);
  } else return dice.sort((a, b) => +b.value - +a.value);
}
