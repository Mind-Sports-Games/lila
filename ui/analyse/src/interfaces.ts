import { VNode } from 'snabbdom';
import { Player, Status, Source, Clock } from 'game';
import * as cg from 'chessground/build/types';
import { ForecastData } from './forecast/interfaces';
import { StudyPracticeData, Goal as PracticeGoal } from './study/practice/interfaces';
import { RelayData } from './study/relay/interfaces';
import AnalyseController from './ctrl';
import { ChatCtrl } from 'chat';
import { ExplorerOpts } from './explorer/interfaces';
import { StudyData } from './study/interfaces';
import { AnalyseSocketSend } from './socket';

export type MaybeVNode = VNode | string | null | undefined;
export type MaybeVNodes = MaybeVNode[];
export type Seconds = number;

export { Key, Piece } from 'chessground/build/types';

export interface NvuiPlugin {
  render(ctrl: AnalyseController): VNode;
}

export interface AnalyseApi {
  socketReceive(type: string, data: any): boolean;
  path(): Tree.Path;
  setChapter(id: string): void;
}

// similar, but not identical, to game/GameData
export interface AnalyseData {
  game: Game;
  player: Player;
  opponent: Player;
  orientation: Orientation;
  spectator?: boolean; // for compat with GameData, for game functions
  canTakeBack: boolean;
  takebackable: boolean;
  moretimeable: boolean;
  analysis?: Analysis;
  userAnalysis: boolean;
  forecast?: ForecastData;
  treeParts: Tree.Node[];
  evalPut?: boolean;
  practiceGoal?: PracticeGoal;
  clock?: Clock;
  pref: AnalysePref;
  gameRecordFormat: string; //sgf or png
  url: {
    socket: string;
  };
  userTv?: {
    id: string;
  };
  onlyDropsVariant: boolean;
  hasGameScore: boolean;
}

export interface AnalysePref {
  coords: Prefs.Coords;
  pieceSet: Piece[];
  is3d?: boolean;
  showDests?: boolean;
  rookCastle?: boolean;
  destination?: boolean;
  highlight?: boolean;
  moveEvent: Prefs.MoveEvent;
  animationDuration?: number;
  mancalaMove: boolean;
}

type Piece = {
  name: string;
  gameFamily: string;
};

export interface ServerEvalData {
  ch: string;
  analysis?: Analysis;
  tree: Tree.Node;
  division?: Division;
}

export interface CachedEval {
  fen: Fen;
  knodes: number;
  depth: number;
  pvs: Tree.PvDataServer[];
  path: string;
}

// similar, but not identical, to game/Game
export interface Game {
  id: string;
  status: Status;
  player: PlayerIndex;
  playerName: PlayerName;
  playerIndex: PlayerIndex;
  turns: number;
  startedAtTurn: number;
  source: Source;
  speed: Speed;
  variant: Variant;
  gameFamily: GameFamilyKey;
  winner?: PlayerIndex;
  winnerPlayer: PlayerName;
  loserPlayer: PlayerName;
  plyCentis?: number[];
  initialFen?: string;
  importedBy?: string;
  division?: Division;
  opening?: Opening;
  perf: string;
  rated?: boolean;
}

export interface Opening {
  name: string;
  eco: string;
  ply: number;
}

export interface Division {
  middle?: number;
  end?: number;
}

export interface Analysis {
  id: string;
  p1: AnalysisSide;
  p2: AnalysisSide;
  partial: boolean;
}

export interface AnalysisSide {
  acpl: number;
  inaccuracy: number;
  mistake: number;
  blunder: number;
}

export interface AnalyseOpts {
  element: HTMLElement;
  data: AnalyseData;
  userId?: string;
  hunter: boolean;
  embed: boolean;
  explorer: ExplorerOpts;
  socketSend: AnalyseSocketSend;
  trans: Trans;
  study?: StudyData;
  tagTypes?: string;
  practice?: StudyPracticeData;
  relay?: RelayData;
  $side?: Cash;
  $underboard?: Cash;
  i18n: I18nDict;
  chat: {
    parseMoves: boolean;
    instance?: Promise<ChatCtrl>;
  };
}

export interface JustCaptured extends cg.Piece {
  promoted?: boolean;
}

export interface EvalGetData {
  fen: Fen;
  path: string;
  variant?: VariantKey;
  mpv?: number;
  up?: boolean;
}

export interface EvalPutData extends Tree.ServerEval {
  variant?: VariantKey;
}

export type Conceal = false | 'conceal' | 'hide' | null;
export type ConcealOf = (isMainline: boolean) => (path: Tree.Path, node: Tree.Node) => Conceal;

export type Redraw = () => void;

export type Position = 'top' | 'bottom';
