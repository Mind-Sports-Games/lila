import { BaseGameData, Player } from './interfaces';
import * as status from './status';

export * from './interfaces';

export const playable = (data: BaseGameData): boolean => data.game.status.id < status.ids.aborted && !imported(data);

export const isPlayerPlaying = (data: BaseGameData): boolean => playable(data) && !data.player.spectator;

export const isPlayerTurn = (data: BaseGameData): boolean =>
  isPlayerPlaying(data) && data.game.player == data.player.playerIndex;

export const mandatory = (data: BaseGameData): boolean => !!data.tournament || !!data.simul || !!data.swiss;

export const playedTurns = (data: BaseGameData): number => data.game.turns - (data.game.startedAtTurn || 0);

export const bothPlayersHavePlayed = (data: BaseGameData): boolean => playedTurns(data) > 1;

export const abortable = (data: BaseGameData): boolean =>
  playable(data) &&
  !bothPlayersHavePlayed(data) &&
  !mandatory(data) &&
  ((data.game.multiMatch?.index !== undefined && data.game.multiMatch?.index < 2) ||
    data.game.multiMatch?.index === undefined);

export const takebackable = (data: BaseGameData): boolean =>
  playable(data) &&
  data.takebackable &&
  bothPlayersHavePlayed(data) &&
  !data.player.proposingTakeback &&
  !data.opponent.proposingTakeback;

export const drawable = (data: BaseGameData): boolean =>
  playable(data) && data.game.turns >= 2 && !data.player.offeringDraw && !hasAi(data);

export const resignable = (data: BaseGameData): boolean => playable(data) && !abortable(data);

// can the current player go berserk?
export const berserkableBy = (data: BaseGameData): boolean =>
  !!data.tournament && data.tournament.berserkable && isPlayerPlaying(data) && !bothPlayersHavePlayed(data);

export const moretimeable = (data: BaseGameData): boolean =>
  isPlayerPlaying(data) &&
  data.moretimeable &&
  (!!data.clock ||
    (!!data.correspondence && data.correspondence[data.opponent.playerIndex] < data.correspondence.increment - 3600));

const imported = (data: BaseGameData): boolean => data.game.source === 'import';

export const replayable = (data: BaseGameData): boolean =>
  imported(data) || status.finished(data) || (status.aborted(data) && bothPlayersHavePlayed(data));

export function getPlayer(data: BaseGameData, playerIndex: PlayerIndex): Player;
export function getPlayer(data: BaseGameData, playerIndex?: PlayerIndex): Player | null {
  if (data.player.playerIndex === playerIndex) return data.player;
  if (data.opponent.playerIndex === playerIndex) return data.opponent;
  return null;
}

export const hasAi = (data: BaseGameData): boolean => !!(data.player.ai || data.opponent.ai);

export const userAnalysable = (data: BaseGameData): boolean =>
  status.finished(data) || (playable(data) && (!data.clock || !isPlayerPlaying(data)));

export const isCorrespondence = (data: BaseGameData): boolean => data.game.speed === 'correspondence';

export const setOnGame = (data: BaseGameData, playerIndex: PlayerIndex, onGame: boolean): void => {
  const player = getPlayer(data, playerIndex);
  onGame = onGame || !!player.ai;
  player.onGame = onGame;
  if (onGame) setGone(data, playerIndex, false);
};

export const setGone = (data: BaseGameData, playerIndex: PlayerIndex, gone: number | boolean): void => {
  const player = getPlayer(data, playerIndex);
  player.isGone = !player.ai && gone;
  if (player.isGone === false && player.user) player.user.online = true;
};

export const nbMoves = (data: BaseGameData, playerIndex: PlayerIndex): number =>
  Math.floor((data.game.turns + (playerIndex == 'p1' ? 1 : 0)) / 2);

export const isSwitchable = (data: BaseGameData): boolean => !hasAi(data) && (!!data.simul || isCorrespondence(data));
