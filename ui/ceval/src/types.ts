import type { Outcome } from 'stratops/types';
import type { Prop } from 'common';
import type { StoredProp, StoredBooleanProp } from 'common/storage';

// NNUE: Efficiently Updateable Neural Network
// HCE: Hand-Crafted Evaluation
// Benchmarks have shown that wasm can be around 5-30% faster than asm.js, depending on the specific use case and browser. However, asm.js can still be useful as a fallback for wasm, and some browsers may optimize asm.js better than others.
export type CevalTechnology = 'asmjs' | 'wasm' | 'hce' | 'nnue';

export interface Eval {
  cp?: number;
  mate?: number;
}

export interface ProtocolOpts {
  variant: VariantKey;
  threads: false | (() => number | string);
  hashSize: false | (() => number | string);
}

export interface Work {
  path: string;
  maxDepth: number;
  multiPv: number;
  ply: number;
  threatMode: boolean;
  initialFen: string;
  currentFen: string;
  moves: string[];
  emit: (ev: Tree.ClientEval) => void;
  stopRequested: boolean;
}

export interface CevalOpts {
  storageKeyPrefix?: string;
  multiPvDefault?: number;
  possible: boolean;
  variant: Variant;
  standardMaterial: boolean;
  emit: (ev: Tree.ClientEval, work: Work) => void;
  setAutoShapes: () => void;
  redraw: () => void;
}

export interface Hovering {
  fen: string;
  uci: string;
}

export interface PvBoard {
  fen: string;
  uci: string;
}

export interface Started {
  path: string;
  steps: Step[];
  threatMode: boolean;
}

export interface CevalCtrl {
  goDeeper(): void;
  canGoDeeper(): boolean;
  effectiveMaxDepth(): number;
  technology: CevalTechnology;
  downloadProgress: Prop<number>;
  allowed: Prop<boolean>;
  enabled: Prop<boolean>;
  possible: boolean;
  isComputing(): boolean;
  engineName(): string | undefined;
  variant: Variant;
  setHovering: (fen: string, uci?: string) => void;
  setPvBoard: (pvBoard: PvBoard | null) => void;
  multiPv: StoredProp<number>;
  start: (path: string, steps: Step[], threatMode?: boolean, deeper?: boolean) => void;
  stop(): void;
  threads: StoredProp<number> | undefined;
  hashSize: StoredProp<number> | undefined;
  maxThreads: number;
  maxHashSize: number;
  infinite: StoredBooleanProp;
  supportsNnue: boolean;
  enableNnue: StoredBooleanProp;
  hovering: Prop<Hovering | null>;
  pvBoard: Prop<PvBoard | null>;
  toggle(): void;
  curDepth(): number;
  isDeeper(): boolean;
  destroy(): void;
  redraw(): void;
}

export interface ParentCtrl {
  getCeval(): CevalCtrl;
  nextNodeBest(): string | undefined;
  disableThreatMode?: Prop<boolean>;
  toggleThreatMode(): void;
  toggleCeval(): void;
  outcome(): Outcome | undefined;
  mandatoryCeval?: Prop<boolean>;
  showEvalGauge: Prop<boolean>;
  currentEvals(): NodeEvals;
  ongoing: boolean;
  playUci(uci: string): void;
  getOrientation(): Orientation;
  threatMode(): boolean;
  getNode(): Tree.Node;
  showComputer(): boolean;
  trans: Trans;
}

export interface NodeEvals {
  client?: Tree.ClientEval;
  server?: Tree.ServerEval;
}

export interface Step {
  ply: number;
  fen: string;
  san?: string;
  uci?: string;
  threat?: Tree.ClientEval;
  ceval?: Tree.ClientEval;
}
