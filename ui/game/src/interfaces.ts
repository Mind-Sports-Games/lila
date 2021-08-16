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
  player: Color;
  turns: number;
  startedAtTurn?: number;
  source: Source;
  speed: Speed;
  variant: Variant | DraughtsVariant;
  winner?: Color;
  drawOffers?: number[];
  moveCentis?: number[];
  initialFen?: string;
  importedBy?: string;
  threefold?: boolean;
  boosted?: boolean;
  rematch?: string;
  microMatch?: MicroMatch;
  rated?: boolean;
  perf: string;
}

export interface Game extends BaseGame {
  variant: Variant;
}

export interface DraughtsGame extends BaseGame {
  variant: DraughtsVariant;
}

export interface MicroMatch {
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
  | 'noStart'
  | 'cheat'
  | 'variantEnd'
  | 'unknownFinish';

export type StatusId = number;

export interface Player {
  id: string;
  name: string;
  user?: PlayerUser;
  spectator?: boolean;
  color: Color;
  proposingTakeback?: boolean;
  offeringRematch?: boolean;
  offeringDraw?: boolean;
  ai: number | null;
  onGame: boolean;
  gone: number | boolean;
  blurs?: Blurs;
  hold?: Hold;
  ratingDiff?: number;
  checks?: number;
  rating?: number;
  provisional?: string;
  engine?: boolean;
  berserk?: boolean;
  version: number;
}

export interface TournamentRanks {
  white: number;
  black: number;
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
  white: number;
  black: number;
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

export interface Trans {
  (key: string): string;
  noarg: (key: string) => string;
}

export interface Hold {
  ply: number;
  mean: number;
  sd: number;
}

export type ContinueMode = 'friend' | 'ai';

export interface GameView {
  status(ctrl: Ctrl): string;
}
