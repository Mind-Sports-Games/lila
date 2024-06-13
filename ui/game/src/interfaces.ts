// TODO: these interfaces should be written in a way that
//       allows for the base one to be defined and then only the
//       differences after that, until then, keep all of them up
//       to date.
export interface BaseGameData {
  game: Game | DraughtsGame;
  player: Player;
  opponent: Player;
  spectator?: boolean;
  tournament?: Tournament;
  simul?: Simul;
  swiss?: Swiss;
  canTakeBack: boolean;
  takebackable: boolean;
  moretimeable: boolean;
  clock?: Clock;
  correspondence?: CorrespondenceClock;
}
export interface GameData extends BaseGameData {
  game: Game;
}

export interface DraughtsGameData extends BaseGameData {
  game: DraughtsGame;
}

export interface BaseGame {
  id: string;
  status: Status;
  player: PlayerIndex;
  turns: number;
  startedAtTurn?: number;
  source: Source;
  speed: Speed;
  variant: Variant | DraughtsVariant;
  gameFamily: GameFamilyKey;
  winner?: PlayerIndex;
  winnerPlayer?: PlayerName;
  loserPlayer?: PlayerName;
  canOfferDraw?: boolean;
  drawOffers?: number[];
  canDoPassAction?: boolean;
  plyCentis?: number[];
  initialFen?: string;
  importedBy?: string;
  threefold?: boolean;
  isRepetition?: boolean;
  perpetualWarning?: boolean;
  boosted?: boolean;
  rematch?: string;
  multiMatch?: MultiMatch;
  rated?: boolean;
  perf: string;
}

export interface Game extends BaseGame {
  variant: Variant;
}

export interface DraughtsGame extends BaseGame {
  variant: DraughtsVariant;
}

export interface MultiMatch {
  index: number;
  gameId?: string;
}

export interface Status {
  id: StatusId;
  name: StatusName;
}

export type StatusName =
  | 'started'
  | 'aborted'
  | 'mate'
  | 'resign'
  | 'stalemate'
  | 'timeout'
  | 'draw'
  | 'outoftime'
  | 'ruleOfGin'
  | 'noStart'
  | 'cheat'
  | 'singleWin'
  | 'gammonWin'
  | 'backgammonWin'
  | 'variantEnd'
  | 'perpetualCheck'
  | 'unknownFinish';

export type StatusId = number;

export interface Player {
  id: string;
  name: string;
  user?: PlayerUser;
  spectator?: boolean;
  playerIndex: PlayerIndex;
  playerName: PlayerName;
  playerColor: PlayerColor;
  proposingTakeback?: boolean;
  offeringRematch?: boolean;
  offeringDraw?: boolean;
  offeringSelectSquares?: boolean;
  ai: number | null;
  onGame: boolean;
  gone: number | boolean;
  blurs?: Blurs;
  hold?: Hold;
  ratingDiff?: number;
  checks?: number;
  score?: number;
  rating?: number;
  provisional?: string;
  engine?: boolean;
  berserk?: boolean;
  version: number;
}

export interface TournamentRanks {
  p1: number;
  p2: number;
}

export interface Tournament {
  id: string;
  berserkable: boolean;
  ranks?: TournamentRanks;
  running?: boolean;
  nbSecondsForFirstMove?: number;
  top?: TourPlayer[];
  team?: Team;
}

export interface TourPlayer {
  n: string; // name
  s: number; // score
  t?: string; // title
  f: boolean; // fire
  w: boolean; // withdraw
}

export interface Team {
  name: string;
}

export interface Simul {
  id: string;
  name: string;
  hostId: string;
  nbPlaying: number;
}

export interface Swiss {
  id: string;
  running?: boolean;
  isBestOfX?: boolean;
  isPlayX?: boolean;
  ranks?: TournamentRanks;
}

export interface Clock {
  running: boolean;
  initial: number;
  increment: number;
}
export interface CorrespondenceClock {
  daysPerTurn: number;
  increment: number;
  p1: number;
  p2: number;
}

export type Source = 'import' | 'lobby' | 'pool' | 'friend';

export interface PlayerUser {
  id: string;
  online: boolean;
  username: string;
  patron?: boolean;
  title?: string;
  perfs: {
    [key: string]: Perf;
  };
}

export interface Perf {
  games: number;
  rating: number;
  rd: number;
  prog: number;
  prov?: boolean;
}

export interface Ctrl {
  data: BaseGameData;
  trans: Trans;
}

export interface Blurs {
  nb: number;
  percent: number;
}

//export interface Trans {
//  (key: string): string;
//  noarg: (key: string) => string;
//}

export interface Hold {
  ply: number;
  mean: number;
  sd: number;
}

export type ContinueMode = 'friend' | 'ai';

export interface GameView {
  status(ctrl: Ctrl): string;
}
