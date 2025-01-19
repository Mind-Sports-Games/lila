import { VNode } from 'snabbdom';

export type MaybeVNode = VNode | string | null | undefined;
export type MaybeVNodes = MaybeVNode[];
export type Redraw = () => void;

export interface SwissOpts {
  data: SwissData;
  userId?: string;
  schedule?: boolean;
  element: HTMLElement;
  $side: Cash;
  socketSend: SocketSend;
  chat: any;
  i18n: I18nDict;
  classes: string | null;
}

export interface SwissData {
  id: string;
  name: string;
  createdBy: number;
  startsAt: string;
  clock: Clock;
  variant: string;
  isMedley: boolean;
  isHandicapped: boolean;
  isMcMahon: boolean;
  mcmahonCutoff: string;
  backgammonPoints: number;
  roundVariant: string;
  roundVariantName: string;
  p1Name: PlayerName;
  p2Name: PlayerName;
  p1Color: PlayerColor;
  p2Color: PlayerColor;
  me?: MyInfo;
  canJoin: boolean;
  timeBeforeStartToJoin?: string;
  joinTeam?: string;
  round: number;
  nbRounds: number;
  nbPlayers: number;
  nbOngoing: number;
  trophy1st: string;
  trophy2nd: string;
  trophy3rd: string;
  multiMatchGameIds?: string[];
  isMatchScore: boolean;
  isBestOfX: boolean;
  isPlayX: boolean;
  nbGamesPerRound: number;
  status: Status;
  standing: Standing;
  boards: Board[];
  playerInfo?: PlayerExt;
  socketVersion?: number;
  quote?: {
    author: string;
    text: string;
  };
  nextRound?: {
    at: string;
    in: number;
  };
  greatPlayer?: {
    name: string;
    url: string;
  };
  podium?: PodiumPlayer[];
  isRecentlyFinished?: boolean;
  stats?: Stats;
  password?: boolean;
}

export type Status = 'created' | 'started' | 'finished';

export interface MyInfo {
  id: string;
  name: string;
  rank: number;
  absent: boolean;
  gameId?: string;
  multiMatchGameIds?: string[];
  isMatchScore: boolean;
  isBestOfX: boolean;
  isPlayX: boolean;
  nbGamesPerRound: number;
}

export interface PairingBase {
  g: string; // game
  o?: boolean; // ongoing
  w?: boolean; // won
  mr?: string[]; //mulitmatch results
  mmids?: string[]; //multimatch gameids
  ms: boolean; // isMatchScore
  x: boolean; // isBestOfX
  px: boolean; // isPlayX
  gpr: string; //nbGamesPerRound
  mp?: string; //match points for player if using matchScore
  vi?: string; //variant icon
  of?: string; //opening fen
}

export interface Pairing extends PairingBase {
  c: boolean; // playerIndex
}
export interface PairingExt extends Pairing {
  user: LightUser;
  rating: number;
  inputRating?: number;
  ratingDisplay?: string;
}

export interface Standing {
  page: number;
  players: Player[];
}

export type Outcome = 'absent' | 'late' | 'bye';

export interface BasePlayer {
  user: LightUser;
  rating: number;
  inputRating?: number;
  ratingDisplay?: string;
  provisional?: boolean;
  withdraw?: boolean;
  points: number;
  tieBreak: number;
  tieBreak2?: number;
  performance?: number;
  absent: boolean;
  disqualified: boolean;
}

export interface PodiumPlayer extends BasePlayer {
  engine?: boolean;
}

export interface Player extends BasePlayer {
  rank: number;
  sheetMin: string;
  sheet: (PairingBase | Outcome)[];
  mmStartingScore?: number;
}

export interface BoardSize {
  size: number[];
  key: string;
}

export interface Board {
  id: string;
  gameLogic: string;
  gameFamily: string;
  variantKey: string;
  boardSize?: BoardSize;
  fen: string;
  lastMove?: string;
  orientation: PlayerIndex;
  p1: BoardPlayer;
  p2: BoardPlayer;
  p1Color: PlayerColor;
  p2Color: PlayerColor;
  multiMatchGameIds?: string[];
  multiMatchGames?: Board[];
  isBestOfX: boolean;
  isPlayX: boolean;
  clock?: {
    p1: number;
    p2: number;
  };
  winner?: PlayerIndex;
}

export interface BoardPlayer extends BasePlayer {
  rank: number;
}

export interface PerfType {
  icon: string;
  name: string;
}

export interface Clock {
  limit: number;
  increment: number;
}

export interface Pager {
  nbResults: number;
  nbPages: number;
  from: number;
  to: number;
  currentPageResults: Page;
}

export type Page = Player[];

export interface Pages {
  [n: number]: Page;
}

export interface PlayerExt extends Player {
  sheet: (PairingExt | Outcome)[];
}

export interface Stats {
  games: number;
  p1Wins: number;
  p2Wins: number;
  draws: number;
  byes: number;
  absences: number;
  averageRating: number;
}
