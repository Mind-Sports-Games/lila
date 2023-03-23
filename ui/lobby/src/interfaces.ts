import { ChatCtrl, ChatPlugin } from 'chat';
import { VNode } from 'snabbdom';

export type MaybeVNode = VNode | string | null | undefined;
export type MaybeVNodes = MaybeVNode[];

export type Sort = 'rating' | 'time';
export type Mode = 'list' | 'chart';
export type Tab = 'pools' | 'real_time' | 'seeks' | 'now_playing';

interface Untyped {
  [key: string]: any;
}

export interface Hook extends Untyped {}

export interface Seek extends Untyped {}

export interface Pool {
  id: PoolId;
  lim: number;
  inc: number;
  byoyomi?: number;
  periods?: number;
  perf: string;
  variant: DraughtsVariantKey | VariantKey;
  variantDisplayName: string;
  variantId: string;
}

export interface LobbyOpts extends Untyped {
  element: HTMLElement;
  socketSend: SocketSend;
  pools: Pool[];
  blindMode: boolean;
  chat?: ChatOpts;
  chatSocketVersion: number;
}

export interface ChatOpts {
  preset: 'start' | 'end' | undefined;
  parseMoves?: boolean;
  plugin?: ChatPlugin;
  alwaysEnabled: boolean;
  noteId?: string;
  noteAge?: number;
  noteText?: string;
  instance?: Promise<ChatCtrl>;
}

export interface LobbyData extends Untyped {
  hooks: Hook[];
  seeks: Seek[];
  nowPlaying: NowPlaying[];
}

export interface VariantBoardSize {
  size: BoardSize;
  key: string;
}

export interface GameLogic {
  id: number;
  name: string;
}

export interface Variant {
  gameLogic: GameLogic;
  gameFamily: string;
  key: string;
  name: string;
  boardSize?: VariantBoardSize;
}

export interface NowPlaying {
  fullId: string;
  gameId: string;
  fen: Fen;
  playerIndex: PlayerIndex;
  lastMove: string;
  variant: Variant;
  speed: string;
  perf: string;
  rated: boolean;
  hasMoved: boolean;
  opponent: {
    id: string;
    username: string;
    rating?: number;
    ai?: number;
  };
  isMyTurn: boolean;
  secondsLeft?: number;
}

export interface PoolMember {
  id: PoolId;
  range?: PoolRange;
  blocking?: string;
}

export type PoolId = string;
export type PoolRange = string;
