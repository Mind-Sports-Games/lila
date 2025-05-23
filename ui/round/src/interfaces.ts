import { VNode } from 'snabbdom';
import { GameData, Status } from 'game';
import { ClockData, Seconds, Centis } from './clock/clockCtrl';
import { CorresClockData } from './corresClock/corresClockCtrl';
import RoundController from './ctrl';
import { ChatCtrl, ChatPlugin } from 'chat';
import * as cg from 'chessground/types';
import * as Prefs from 'common/prefs';

export type MaybeVNode = VNode | null | undefined;
export type MaybeVNodes = MaybeVNode[];

export type Redraw = () => void;

export interface Untyped {
  [key: string]: any;
}

export interface NvuiPlugin {
  render(ctrl: RoundController): VNode;
}

export interface SocketOpts {
  sign: string;
  ackable: boolean;
  withLag?: boolean;
  millis?: number;
}

export interface SocketMove {
  u: Uci;
  variant: string;
  b?: 1;
}
export interface SocketDrop {
  role: cg.Role;
  pos: cg.Key;
  variant: string;
  b?: 1;
}
export interface SocketPass {
  variant: string;
  b?: 1;
}
export interface SocketDoRoll {
  variant: string;
  b?: 1;
}
export interface SocketLift {
  pos: cg.Key;
  variant: string;
  b?: 1;
}
export interface SocketUndo {
  variant: string;
  b?: 1;
}
export interface SocketCubeAction {
  interaction: string;
  variant: string;
  b?: 1;
}
export interface SocketEndTurn {
  variant: string;
  b?: 1;
}

export type EncodedDests =
  | string
  | {
      [key: string]: string;
    };
export type Dests = cg.Dests;

export interface MultiActionMetaData {
  couldNextActionEndTurn: boolean;
}

export interface RoundData extends GameData {
  clock?: ClockData;
  pref: Pref;
  steps: Step[];
  possibleMoves?: EncodedDests;
  possibleDrops?: string;
  possibleDropsByRole?: string;
  possibleLifts?: string;
  cubeActions?: string;
  multiActionMetaData?: MultiActionMetaData;
  selectMode: boolean;
  selectedSquares?: cg.Key[];
  currentSelectedSquares?: cg.Key[];
  calculatedCGGoScores?: cg.SimpleGoScores;
  deadStoneOfferState?: string;
  dice?: cg.Dice[];
  doublingCube?: cg.DoublingCube;
  activeDiceValue?: number;
  canOnlyRollDice: boolean;
  canUndo: boolean;
  canEndTurn: boolean;
  forcedAction?: string;
  pauseSecs?: number;
  forecastCount?: number;
  crazyhouse?: CrazyData;
  onlyDropsVariant: boolean;
  hasGameScore: boolean;
  correspondence: CorresClockData;
  url: {
    socket: string;
    round: string;
  };
  tv?: Tv;
  userTv?: {
    id: string;
  };
  expirationAtStart?: Expiration;
  expirationOnPaused?: Expiration;
}

export interface Expiration {
  idleMillis: number;
  updatedAt: number;
  millisToMove: number;
}

export interface Tv {
  channel: string;
  flip: boolean;
}

interface CrazyData {
  pockets: [CrazyPocket, CrazyPocket];
}

interface CrazyPocket {
  [role: string]: number;
}

export interface RoundOpts {
  data: RoundData;
  userId?: string;
  socketSend: SocketSend;
  onChange(d: RoundData): void;
  element: HTMLElement;
  crosstableEl: HTMLElement;
  i18n: I18nDict;
  chat?: ChatOpts;
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

export interface Step {
  ply: Ply;
  turnCount: number;
  fen: Fen;
  san: San;
  uci: Uci;
  check?: boolean;
  crazy?: StepCrazy;
  currentPointValueP1?: number;
  currentPointValueP2?: number;
}

export interface ApiAction extends Step {
  dests: EncodedDests;
  clock?: {
    p1: Seconds;
    p2: Seconds;
    p1Pending: Seconds;
    p2Pending: Seconds;
    p1Periods: number;
    p2Periods: number;
    lag?: Centis;
  };
  status: Status;
  winner?: PlayerIndex;
  check: boolean;
  threefold: boolean;
  perpetualWarning: boolean;
  takebackable: boolean;
  wDraw: boolean;
  bDraw: boolean;
  crazyhouse?: CrazyData;
  role?: cg.Role;
  drops?: string;
  dropsByRole?: string;
  lifts?: string;
  canOnlyRollDice: boolean;
  canUndo: boolean;
  canEndTurn: boolean;
  forcedAction?: string;
  dice?: string;
  cubeActions?: string;
  multiActionMetaData?: MultiActionMetaData;
  canSelectSquares?: boolean;
  deadStoneOfferState?: string;
  squares?: string;
  promotion?: {
    key: cg.Key;
    pieceClass: cg.Role;
  };
  castle?: {
    king: [cg.Key, cg.Key];
    rook: [cg.Key, cg.Key];
    playerIndex: PlayerIndex;
  };
  isMove?: true;
  isDrop?: true;
}

export interface ApiEnd {
  winner?: PlayerIndex;
  winnerPlayer?: PlayerName;
  loserPlayer?: PlayerName;
  status: Status;
  pointValue?: number;
  ratingDiff?: {
    p1: number;
    p2: number;
  };
  boosted: boolean;
  clock?: {
    p1: Centis;
    p2: Centis;
    p1Pending: Centis;
    p2Pending: Centis;
    p1Periods: number;
    p2Periods: number;
  };
}

export interface StepCrazy extends Untyped {}

export interface Pref {
  animationDuration: number;
  autoQueen: Prefs.AutoQueen;
  blindfold: boolean;
  clockBar: boolean;
  clockSound: boolean;
  clockTenths: Prefs.ShowClockTenths;
  confirmResign: boolean;
  confirmPass: boolean;
  playForcedAction: boolean;
  coords: Prefs.Coords;
  destination: boolean;
  playerTurnIndicator: boolean;
  enablePremove: boolean;
  highlight: boolean;
  is3d: boolean;
  keyboardMove: boolean;
  moveEvent: Prefs.MoveEvent;
  mancalaMove: boolean;
  replay: Prefs.Replay;
  rookCastle: boolean;
  showCaptured: boolean;
  submitMove: boolean;
  resizeHandle: Prefs.ShowResizeHandle;
  pieceSet: Piece[];
}

type Piece = {
  name: string;
  gameFamily: string;
};

export interface MoveMetadata {
  premove?: boolean;
  justDropped?: cg.Role;
  justCaptured?: cg.Piece;
}

export type Position = 'top' | 'bottom';

export interface MaterialDiffSide {
  [role: string]: number;
}
export interface MaterialDiff {
  p1: MaterialDiffSide;
  p2: MaterialDiffSide;
}
export interface CheckCount {
  p1: number;
  p2: number;
}

export interface Score {
  p1: number;
  p2: number;
}
