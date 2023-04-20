import { VNode } from 'snabbdom';

export type MaybeVNode = VNode | string | null | undefined;
export type MaybeVNodes = MaybeVNode[];
export type Redraw = () => void;

export interface SimulOpts {
  data: SimulData;
  userId?: string;
  element: HTMLElement;
  $side: Cash;
  socketVersion: number;
  chat: any;
  i18n: I18nDict;
  socketSend: SocketSend;
}

export interface SimulData {
  id: string;
  name: string;
  fullName: string;
  isCreated: boolean;
  isRunning: boolean;
  isFinished: boolean;
  text: string;
  host: Host;
  variants: Variant[];
  applicants: Applicant[];
  pairings: Pairing[];
  quote?: {
    text: string;
    author: string;
  };
  team?: Team;
}

export interface Variant {
  key: VariantKey;
  name: string;
  icon: string;
}

export interface Team {
  id: string;
  name: string;
  isIn: boolean;
}

export interface Player extends LightUser {
  rating: number;
  provisional?: boolean;
}

export interface Host extends LightUser {
  rating: number;
  gameId?: string;
}

export interface Applicant {
  player: Player;
  variant: VariantKey;
  accepted: boolean;
}

export interface Pairing {
  player: Player;
  variant: VariantKey;
  hostPlayerIndex: PlayerIndex;
  game: Game;
  p1Color: PlayerColor;
  p2Color: PlayerColor;
}

export interface Game {
  id: string;
  status: number;
  fen: string;
  boardSize?: BoardSize;
  gameLogic: string;
  lastMove: string;
  orient: PlayerIndex;
  clock?: {
    p1: number;
    p2: number;
  };
  winner?: PlayerIndex;
}

export interface BoardSize {
  size: number[];
  key: string;
}
