// eslint-disable-next-line
/// <reference path="./chessground.d.ts" />
// eslint-disable-next-line
/// <reference path="./cash.d.ts" />

/// <reference types="highcharts" />

// file://./../../site/src/site.ts
interface PlayStrategy {
  load: Promise<void>; // window.onload promise
  info: any;
  requestIdleCallback(f: () => void, timeout?: number): void;
  sri: string;
  storage: PlayStrategyStorageHelper;
  tempStorage: PlayStrategyStorageHelper;
  once(key: string, mod?: 'always'): boolean;
  powertip: any;
  clockWidget(el: HTMLElement, opts: { time: number; pause?: boolean; delay?: number; pending?: number }): void;
  widget: any;
  spinnerHtml: string;
  assetUrl(url: string, opts?: AssetUrlOpts): string;
  loadCss(path: string): void;
  loadHashedCss(path: string): void;
  loadHashedCssPath(path: string): void;
  jsModule(name: string): string;
  loadScript(url: string, opts?: AssetUrlOpts): Promise<void>;
  loadScriptCJS(url: string, opts?: AssetUrlOpts): Promise<void>;
  loadModule(name: string): Promise<void>;
  hopscotch: any;
  userComplete: () => Promise<UserComplete>;
  slider(): Promise<void>;
  makeChat(data: any): any;
  idleTimer(delay: number, onIdle: () => void, onWakeUp: () => void): void;
  pubsub: Pubsub;
  contentLoaded(parent?: HTMLElement): void;
  unload: {
    expected: boolean;
  };
  watchers(el: HTMLElement): void;
  redirect(o: RedirectTo): void;
  reload(): void;
  escapeHtml(str: string): string;
  announce(d: PlayStrategyAnnouncement): void;
  studyTour(study: Study): void;
  studyTourChapter(study: Study): void;
  libraryChart?: (data: any, allowedVariants?: string[]) => void;

  trans(i18n: I18nDict): Trans;
  quantity(n: number): 'zero' | 'one' | 'few' | 'many' | 'other';

  socket: any;
  sound: SoundI;
  miniBoard: {
    init(node: HTMLElement): void;
    initAll(parent?: HTMLElement): void;
  };
  miniGame: {
    init(node: HTMLElement): string | null;
    initAll(parent?: HTMLElement): void;
    update(node: HTMLElement, data: GameUpdate): void;
    finish(node: HTMLElement, win?: PlayerIndex): void;
  };
  ab?: any;

  // socket.js
  StrongSocket: {
    new (url: string, version: number | false, cfg?: any): any;
    firstConnect: Promise<(tpe: string, data: any) => void>;
    defaultParams: Record<string, any>;
  };

  timeago(date: number | Date): string;
  timeagoLocale(a: number, b: number, c: number): any;

  // misc
  advantageChart?: {
    update(data: any): void;
    (data: any, trans: Trans, el: HTMLElement): void;
  };
  movetimeChart: any;
  RoundNVUI?(redraw: () => void): {
    render(ctrl: any): any;
  };
  AnalyseNVUI?(redraw: () => void): {
    render(ctrl: any): any;
  };
  playMusic(): any;
  quietMode?: boolean;
  keyboardMove?: any;
  analysis?: any; // expose the analysis ctrl
  pageVariant: PageVariant;

  manifest: { css: Record<string, string>; js: Record<string, string>; hashed: Record<string, string> };
}

type I18nDict = { [key: string]: string };
type I18nKey = string;

type RedirectTo = string | { url: string; cookie: Cookie };

type UserComplete = (opts: UserCompleteOpts) => void;

interface UserCompleteOpts {
  input: HTMLInputElement;
  tag?: 'a' | 'span';
  minLength?: number;
  populate?: (result: LightUser) => string;
  onSelect?: (result: LightUser) => void;
  focus?: boolean;
  friend?: boolean;
  tour?: string;
  swiss?: string;
}

interface SoundI {
  loadOggOrMp3(name: string, path: string): Promise<void>;
  loadStandard(name: string, soundSet?: string): void;
  play(name: string, volume?: number): void;
  getVolume(): number;
  setVolume(v: number): void;
  speech(v?: boolean): boolean;
  changeSet(s: string): void;
  say(text: any, cut?: boolean, force?: boolean): boolean;
  sayOrPlay(name: string, text: string): void;
  preloadBoardSounds(): void;
  soundSet: string;
  baseUrl: string;
}

interface PlayStrategySpeech {
  say(t: string, cut: boolean): void;
  step(s: { san?: San }, cut: boolean): void;
}

interface PalantirOpts {
  uid: string;
  redraw(): void;
}
interface Palantir {
  render(h: any): any;
}

interface Cookie {
  name: string;
  value: string;
  maxAge: number;
}

interface AssetUrlOpts {
  sameDomain?: boolean;
  noVersion?: boolean;
  version?: string;
}

type Timeout = ReturnType<typeof setTimeout>;

declare type SocketSend = (type: string, data?: any, opts?: any, noRetry?: boolean) => void;

type TransNoArg = (key: string) => string;

interface Trans {
  (key: string, ...args: Array<string | number>): string;
  noarg: TransNoArg;
  plural(key: string, count: number, ...args: Array<string | number>): string;
  vdom<T>(key: string, ...args: T[]): Array<string | T>;
  vdomPlural<T>(key: string, count: number, countArg: T, ...args: T[]): Array<string | T>;
}

type PubsubCallback = (...data: any[]) => void;

interface Pubsub {
  on(msg: string, f: PubsubCallback): void;
  off(msg: string, f: PubsubCallback): void;
  emit(msg: string, ...args: any[]): void;
}

interface PlayStrategyStorageHelper {
  make(k: string): PlayStrategyStorage;
  makeBoolean(k: string): PlayStrategyBooleanStorage;
  get(k: string): string | null;
  set(k: string, v: string): void;
  fire(k: string, v?: string): void;
  remove(k: string): void;
}

interface PlayStrategyStorage {
  get(): string | null;
  set(v: any): void;
  remove(): void;
  listen(f: (e: PlayStrategyStorageEvent) => void): void;
  fire(v?: string): void;
}

interface PlayStrategyBooleanStorage {
  get(): boolean;
  set(v: boolean): void;
  toggle(): void;
}

interface PlayStrategyStorageEvent {
  sri: string;
  nonce: number;
  value?: string;
}

interface PlayStrategyAnnouncement {
  msg?: string;
  date?: string;
}

interface PlayStrategyEditor {
  getFen(): string;
  setOrientation(o: PlayerIndex): void;
}

declare namespace Editor {
  export interface Config {
    baseUrl: string;
    fen: string;
    options?: Editor.Options;
    is3d: boolean;
    animation: {
      duration: number;
    };
    embed: boolean;
    positions?: OpeningPosition[];
    i18n: I18nDict;
    standardInitialPosition: boolean;
    playerIndex?: PlayerIndex;
    variantKey?: VariantKey;
  }

  export interface Options {
    orientation?: Orientation;
    onChange?: (fen: string) => void;
    inlineCastling?: boolean;
  }

  export interface OpeningPosition {
    eco?: string;
    name: string;
    fen: string;
    epd?: string;
  }
}

interface Window {
  playstrategy: PlayStrategy;

  moment: any;
  Mousetrap: any;
  Chessground: any;
  Highcharts: Highcharts.Static;
  libraryChartData?: any;
  InfiniteScroll(selector: string): void;
  playstrategyReplayMusic: () => {
    jump(node: Tree.Node): void;
  };
  hopscotch: any;
  PlayStrategySpeech?: PlayStrategySpeech;
  PlayStrategyEditor?(element: HTMLElement, config: Editor.Config): PlayStrategyEditor;
  palantir?: {
    palantir(opts: PalantirOpts): Palantir;
  };
  [key: string]: any; // TODO
  readonly paypalOrder: unknown;
  readonly paypalSubscription: unknown;
}

interface Study {
  userId?: string | null;
  isContrib?: boolean;
  isOwner?: boolean;
  setTab(tab: string): void;
}

interface LightUserNoId {
  name: string;
  country?: string;
  title?: string;
  patron?: boolean;
}

interface LightUser extends LightUserNoId {
  id: string;
}

interface Navigator {
  deviceMemory?: number; // https://developer.mozilla.org/en-US/docs/Web/API/Navigator/deviceMemory
}

declare type VariantKey =
  | 'standard'
  | 'chess960'
  | 'antichess'
  | 'fromPosition'
  | 'kingOfTheHill'
  | 'threeCheck'
  | 'fiveCheck'
  | 'atomic'
  | 'horde'
  | 'racingKings'
  | 'crazyhouse'
  | 'noCastling'
  | 'monster'
  | 'linesOfAction'
  | 'scrambledEggs'
  | 'shogi'
  | 'xiangqi'
  | 'minishogi'
  | 'minixiangqi'
  | 'flipello'
  | 'flipello10'
  | 'amazons'
  | 'breakthroughtroyka'
  | 'minibreakthroughtroyka'
  | 'oware'
  | 'togyzkumalak'
  | 'bestemshe'
  | 'go9x9'
  | 'go13x13'
  | 'go19x19'
  | 'backgammon'
  | 'hyper'
  | 'nackgammon'
  | 'abalone';

declare type DraughtsVariantKey =
  | 'international'
  | 'antidraughts'
  | 'breakthrough'
  | 'russian'
  | 'brazilian'
  | 'pool'
  | 'portuguese'
  | 'english'
  | 'fromPositionDraughts'
  | 'frisian'
  | 'frysk';

declare type Speed = 'bullet' | 'blitz' | 'classical' | 'correspondence' | 'unlimited';

declare type Perf =
  | 'bullet'
  | 'blitz'
  | 'classical'
  | 'correspondence'
  | 'chess960'
  | 'antichess'
  | 'fromPosition'
  | 'kingOfTheHill'
  | 'threeCheck'
  | 'fiveCheck'
  | 'atomic'
  | 'horde'
  | 'racingKings'
  | 'crazyhouse'
  | 'noCastling'
  | 'monster'
  | 'linesOfAction'
  | 'scrambledEggs'
  | 'international'
  | 'antidraughts'
  | 'breakthrough'
  | 'russian'
  | 'brazilian'
  | 'pool'
  | 'portuguese'
  | 'english'
  | 'fromPositionDraughts'
  | 'frisian'
  | 'frysk'
  | 'shogi'
  | 'xiangqi'
  | 'minishogi'
  | 'minixiangqi'
  | 'flipello'
  | 'flipello10'
  | 'amazons'
  | 'breakthroughtroyka'
  | 'minibreakthroughtroyka'
  | 'oware'
  | 'togyzkumalak'
  | 'bestemshe'
  | 'go9x9'
  | 'go13x13'
  | 'go19x19'
  | 'backgammon'
  | 'hyper'
  | 'nackgammon'
  | 'abalone';

//declare type Color = 'white' | 'black';
declare type PlayerName = 'White' | 'Black' | 'Sente' | 'Gote' | 'Red' | 'South' | 'North' | 'Bastaushi' | 'Kostaushi';
declare type PlayerIndex = 'p1' | 'p2';
declare type PlayerColor = 'white' | 'black';
declare type Orientation = 'p1' | 'p2' | 'left' | 'right' | 'p1vflip';

declare type PageVariant = VariantKey | DraughtsVariantKey | undefined;
declare type GameFamilyKey =
  | 'chess'
  | 'draughts'
  | 'loa'
  | 'shogi'
  | 'xiangqi'
  | 'flipello'
  | 'amazons'
  | 'breakthroughtroyka'
  | 'oware'
  | 'togyzkumalak'
  | 'go'
  | 'backgammon'
  | 'abalone';

declare type Files =
  | 'a'
  | 'b'
  | 'c'
  | 'd'
  | 'e'
  | 'f'
  | 'g'
  | 'h'
  | 'i'
  | 'j'
  | 'k'
  | 'l'
  | 'm'
  | 'n'
  | 'o'
  | 'p'
  | 'q'
  | 'r'
  | 's';
declare type Ranks =
  | '1'
  | '2'
  | '3'
  | '4'
  | '5'
  | '6'
  | '7'
  | '8'
  | '9'
  | '10'
  | '11'
  | '12'
  | '13'
  | '14'
  | '15'
  | '16'
  | '17'
  | '18'
  | '19';
declare type Letter =
  | 'a'
  | 'b'
  | 'c'
  | 'd'
  | 'e'
  | 'f'
  | 'g'
  | 'h'
  | 'i'
  | 'j'
  | 'k'
  | 'l'
  | 'm'
  | 'n'
  | 'o'
  | 'p'
  | 'q'
  | 'r'
  | 's'
  | 't'
  | 'u'
  | 'v'
  | 'w'
  | 'x'
  | 'y'
  | 'z'
  | 'A'
  | 'B'
  | 'C'
  | 'D'
  | 'E'
  | 'F'
  | 'G'
  | 'H'
  | 'I'
  | 'J'
  | 'K'
  | 'L'
  | 'M'
  | 'N'
  | 'O'
  | 'P'
  | 'Q'
  | 'R'
  | 'S'
  | 'T'
  | 'U'
  | 'V'
  | 'W'
  | 'X'
  | 'Y'
  | 'Z';
declare type Role = `${Letter}-piece` | `p${Letter}-piece`;
declare type Key = 'a0' | `${Files}${Ranks}`;
declare type Uci = string;
declare type San = string;
declare type Fen = string;
declare type Ply = number;

// TODO: these interfaces should be written in a way that
//       allows for the base one to be defined and then only the
//       differences after that, until then, keep all of them up
//       to date.
interface Variant {
  key: VariantKey;
  name: string;
  short: string;
  title?: string;
  lib: number;
  boardSize: BoardDim;
  iconChar?: string;
}

interface DraughtsVariant {
  key: DraughtsVariantKey;
  name: string;
  short: string;
  title?: string;
  board: BoardData;
  lib: number;
  iconChar?: string;
}

interface BoardDim {
  width: number;
  height: number;
}

declare type BoardSize = [number, number];
interface BoardData {
  key: string;
  size: BoardSize;
}

interface Paginator<A> {
  currentPage: number;
  maxPerPage: number;
  currentPageResults: Array<A>;
  nbResults: number;
  previousPage?: number;
  nextPage?: number;
  nbPages: number;
}

declare namespace Tree {
  export type Path = string;

  export interface ClientEval {
    fen: Fen;
    maxDepth: number;
    depth: number;
    knps: number;
    nodes: number;
    millis: number;
    pvs: PvData[];
    cloud?: boolean;
    cp?: number;
    mate?: number;
    retried?: boolean;
  }

  export interface ServerEval {
    cp?: number;
    mate?: number;
    best?: Uci;
    fen: Fen;
    knodes: number;
    depth: number;
    pvs: PvDataServer[];
  }

  export interface PvDataServer {
    moves: string;
    mate?: number;
    cp?: number;
  }

  export interface PvData {
    moves: string[];
    mate?: number;
    cp?: number;
  }

  export interface TablebaseHit {
    winner: PlayerIndex | undefined;
    best?: Uci;
  }

  export interface Node {
    id: string;
    ply: Ply;
    turnCount: number;
    playedPlayerIndex: PlayerIndex;
    playerIndex: PlayerIndex;
    uci?: Uci;
    fen: Fen;
    children: Node[];
    comments?: Comment[];
    gamebook?: Gamebook;
    dests?: string;
    drops?: string | null;
    dropsByRole?: string | null;
    check?: Key;
    threat?: ClientEval;
    ceval?: ClientEval;
    eval?: ServerEval;
    tbhit?: TablebaseHit | null;
    glyphs?: Glyph[];
    clock?: Clock;
    parentClock?: Clock;
    forceVariation?: boolean;
    shapes?: Shape[];
    comp?: boolean;
    san?: string;
    threefold?: boolean;
    perpetualWarning?: boolean;
    fail?: boolean;
    puzzle?: 'win' | 'fail' | 'good' | 'retry';
    crazy?: NodeCrazy;
  }

  export interface ParentedNode extends Node {
    parent?: Tree.Node;
    tag: 'parented';
  }

  export interface NodeCrazy {
    pockets: [CrazyPocket, CrazyPocket];
  }

  export interface CrazyPocket {
    [role: string]: number;
  }

  export interface Comment {
    id: string;
    by:
      | string
      | {
          id: string;
          name: string;
        };
    text: string;
  }

  export interface Gamebook {
    deviation?: string;
    hint?: string;
    shapes?: Shape[];
  }

  type GlyphId = number;

  interface Glyph {
    id: GlyphId;
    name: string;
    symbol: string;
  }

  export type Clock = number;

  export interface Shape {}
}

interface GameUpdate {
  id: string;
  fen: Fen;
  lm: Uci;
  p1?: number;
  p2?: number;
}

interface CashStatic {
  powerTip: any;
}

interface Cash {
  powerTip(options?: PowerTip.Options | 'show' | 'hide'): Cash;
}

declare namespace PowerTip {
  type Placement = 'n' | 'e' | 's' | 'w' | 'nw' | 'ne' | 'sw' | 'se' | 'nw-alt' | 'ne-alt' | 'sw-alt' | 'se-alt';

  interface Options {
    preRender?: (el: HTMLElement) => void;
    placement?: Placement;
    smartPlacement?: boolean;
    popupId?: string;
    poupClass?: string;
    offset?: number;
    fadeInTime?: number;
    fadeOutTime?: number;
    closeDelay?: number;
    intentPollInterval?: number;
    intentSensitivity?: number;
    manual?: boolean;
    openEvents?: string[];
    closeEvents?: string[];
  }
}

interface HighchartsHTMLElement extends HTMLElement {
  highcharts: Highcharts.ChartObject;
}

declare namespace Prefs {
  const enum Coords {
    Hidden = 0,
    Inside = 1,
    Outside = 2,
  }

  const enum AutoQueen {
    Never = 1,
    OnPremove = 2,
    Always = 3,
  }

  const enum ShowClockTenths {
    Never = 0,
    Below10Secs = 1,
    Always = 2,
  }

  const enum ShowResizeHandle {
    Never = 0,
    OnlyAtStart = 1,
    Always = 2,
  }

  const enum MoveEvent {
    Click = 0,
    Drag = 1,
    ClickOrDrag = 2,
  }

  const enum Replay {
    Never = 0,
    OnlySlowGames = 1,
    Always = 2,
  }
}

interface Dictionary<T> {
  [key: string]: T | undefined;
}

type SocketHandlers = Dictionary<(d: any) => void>;

declare const playstrategy: PlayStrategy;
declare const $as: <T>(cashOrHtml: Cash | string) => T;
