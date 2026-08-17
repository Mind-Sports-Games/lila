import AnalyseCtrl from './ctrl';
import { BgCandidateUI } from './interfaces';
import { GameFamily as BackgammonFamily } from 'stratops/variants/backgammon/GameFamily';
import { clearCandidatePreview, scheduleShowPlayed, reapplyFenOverride } from './backgammonAnalysis';

// Reuse the advantage chart (ui/chart/src/acpl.ts via chart.game module)
// for backgammon. It reads `data.treeParts[].eval.cp`, maps it through a sigmoid to a
// [-1,1] area chart, and (for backgammon) derives the tooltip margin from `eval.win`. We feed it
// the REAL per-action game tree so the line spans every board ply and the highlight
// tracks as you step.
//
// Each turn on the board is several actions: a dice roll, one node per checker moved,
// and an end-turn. gnubg evaluates a turn ONCE (after the dice are played), giving us
// two numbers per turn: the BEST play of the roll and the PLAYED play. So per turn we
// show: the dice-roll node = best-of-roll win% (the LUCK), and the checker/end nodes =
// played win% (the SKILL). White's perspective throughout.
//
// Assumptions (verify against a real game; see the console one-liner in the PR notes):
//  - turns alternate players, so a turn = a maximal run of same `playedPlayerIndex`,
//    and the FIRST node of a run is the dice roll. Robust to `turnCount` quirks.
//  - gnubg decisions are in the same order as the board's turns (true with no cube;
//    TODO: cube offers/responses are extra decisions — handle for cube play).

interface BgProbs {
  win: number;
  winGammon: number;
  winBackgammon: number;
  lose: number;
  loseGammon: number;
  loseBackgammon: number;
}
interface BgCandidate {
  rank: number;
  play?: string; // gnubg move notation e.g. "24/20 8/7*"
  played: boolean;
  equity?: number;
  equityDelta?: number; // EMG gap vs rank-1 move (negative for rank 2+; absent on rank 1)
  probabilities: BgProbs;
}
interface BgMove {
  player: string;
  kind: string; // ChequerPlay | Dance | CubeOffer | CubeResponse
  rollLuck?: number; // luck in EMG for the dice roll preceding this decision
  playedEquity?: number; // EMG equity of the played move (mover's perspective)
  bestEquity?: number; // EMG equity of the rank-1 move (mover's perspective)
  candidates: BgCandidate[];
}
interface BgPlayerStatsJson {
  player: string;
  overallErrorRate?: number;
  luckTotalEmg?: number;
  overallRating?: string;
  skill?: string; // tier derived from overallErrorRate, computed by BackgammonAnalysis.skillLabel
  luckRating?: string;
}
interface BgGame {
  moves: BgMove[];
  stats?: BgPlayerStatsJson[];
}
interface BgAnalysis {
  player1: string;
  player2: string;
  games: BgGame[];
}

// white's best-of-roll and played win% (0..1) for each turn, in play order
interface TurnVal {
  best: number;
  played: number;
  error: number; // EMG equity loss (always >= 0); gnubg's own value when available
  player: string; // a.player1 or a.player2
  luck?: number; // rollLuck from gnubg (EMG), undefined for dances/cube
  topMove?: boolean; // played move was rank-1 candidate
  topMoveGap?: number; // EMG gap between rank-1 and rank-2 candidate (0 if only one choice)
  noAnnotation?: boolean; // dance (no legal moves) or forced move (one candidate) — skip error colour
}

// only the fields the chart module reads off a node
interface ChartNode {
  ply: number;
  turnCount: number;
  playedPlayerIndex?: 'p1' | 'p2';
  san?: string;
  uci?: string;
  eval?: { cp: number; win: number };
  glyphs?: Tree.Glyph[];
}

// inverse of the chart's cp -> winChance map (y = 2/(1+e^-0.004cp) - 1), so feeding this
// cp reproduces our win% exactly. w in [0,1].
function winToCp(w: number): number {
  const x = Math.min(0.995, Math.max(0.005, w));
  return Math.round(250 * Math.log(x / (1 - x)));
}

function turnValues(a: BgAnalysis): TurnVal[] {
  const out: TurnVal[] = [];
  for (const g of a.games)
    for (const m of g.moves) {
      const cands = m.candidates || [];
      const played = cands.find(c => c.played) || cands[0];
      const best = cands.find(c => c.rank === 1) || played;
      if (!played) {
        // dance / no legal play: no error judgement possible
        const prev = out.length ? out[out.length - 1] : { best: 0.5, played: 0.5, error: 0, player: m.player };
        out.push({ ...prev, player: m.player, luck: m.rollLuck, noAnnotation: true });
        continue;
      }
      // probabilities.win is the mover's win chance; flip to white's view (for chart y-axis)
      const toWhite = (c: BgCandidate) => (m.player === a.player1 ? c.probabilities.win : c.probabilities.lose);
      const bv = toWhite(best);
      const pv = toWhite(played);
      // forced move (one candidate): error is trivially 0, no real choice was made
      const forced = cands.length <= 1;
      // EMG equity loss — use gnubg's own values (accounts for gammons/backgammons).
      // Fallback: approximate from win-prob (× 2 since EMG ≈ 2·win – 1 for no-gammon play).
      const emgLoss =
        m.bestEquity != null && m.playedEquity != null
          ? Math.max(0, m.bestEquity - m.playedEquity)
          : Math.abs(bv - pv) * 2;
      // topMoveGap: equityDelta on the rank-2 candidate is gnubg's gap vs rank 1 (negative).
      const secondBest = cands.find(c => c.rank === 2);
      const topMoveGap =
        secondBest?.equityDelta != null
          ? Math.abs(secondBest.equityDelta)
          : secondBest
            ? Math.abs(bv - toWhite(secondBest)) * 2
            : 0;
      out.push({
        best: bv,
        played: pv,
        error: emgLoss,
        player: m.player,
        luck: m.rollLuck,
        topMove: played.rank === 1,
        topMoveGap,
        noAnnotation: forced,
      });
    }
  return out;
}

function ev(win: number): { cp: number; win: number } {
  return { cp: winToCp(win), win };
}

// Only blunder and perfect play get a special chart glyph; everything else shows as blue by default.
// Thresholds are in EMG (gnubg's equity unit). EMG ≈ 2 × win-prob for no-gammon play.
// Blunder:  ≥ 0.15 EMG loss (~8 pp win-prob in a no-gammon game).
// Mistake:  ≥ 0.05 EMG loss (~2.5 pp).
// Perfect:  rank-1 gap ≥ 0.08 EMG (~4 pp) — the best move was meaningfully better.
function errorToGlyph(error: number, topMove?: boolean, topMoveGap?: number): Tree.Glyph | undefined {
  if (error >= 0.15) return { id: 4, symbol: '??', name: 'Blunder' };
  if (error >= 0.05) return { id: 2, symbol: '?', name: 'Mistake' };
  if (topMove && (topMoveGap ?? 0) >= 0.08) return { id: 3, symbol: '!!', name: 'Perfect play' };
  return undefined;
}

function luckyToGlyph(luck?: number): Tree.Glyph | undefined {
  if ((luck ?? 0) > 0.1) return { id: 51, symbol: '+', name: 'Lucky roll' };
  if ((luck ?? 0) < -0.1) return { id: 52, symbol: '-', name: 'Unlucky roll' };
  return undefined;
}

// Map the mainline onto chart nodes.
// Per turn we emit exactly two nodes (or one for a dance):
//   1. The roll node    → win% = t.best  (captures the luck of the roll)
//   2. The last checker move before endturn → win% = t.played (captures skill)
// endturn nodes (the "dice picker" position) and intermediate checker moves are excluded.
//
// Roll detection uses pi !== prevPi (first node of a new player's run), NOT diceRollUci,
// because low-point checker moves (e.g. "6/4") also satisfy the dice-roll UCI pattern.
function chartNodes(ctrl: AnalyseCtrl, turns: TurnVal[]): ChartNode[] {
  const src = ctrl.data.treeParts;
  const out: ChartNode[] = [];
  let turnIdx = -1;
  let prevPi: string | undefined;
  let prevFen: string = src[0]?.fen ?? '';
  let turnNotations: string[] = []; // per-action notations accumulated for the current turn

  out.push({ ply: src[0].ply, turnCount: src[0].turnCount }); // root — sliced off by the chart

  for (let i = 1; i < src.length; i++) {
    const n = src[i];
    const currentPrevFen = prevFen;
    prevFen = n.fen ?? prevFen; // update before any early continue

    if (n.uci === 'endturn') continue; // dice-picker position: no chart point, don't update prevPi

    const pi = n.playedPlayerIndex;
    const isRoll = pi !== prevPi; // first node of a new player's turn = the dice roll
    prevPi = pi;

    // Compute this action's human-readable notation once (used for both roll label and combined checker label).
    const actionNotation = BackgammonFamily.computeMoveNotation({
      san: n.san ?? '',
      uci: n.uci ?? '',
      fen: n.fen ?? '',
      prevFen: currentPrevFen,
    });

    if (isRoll) {
      turnIdx++;
      turnNotations = [actionNotation];
      const t = turns[turnIdx];
      // Detect no-play (dance): the next non-endturn node belongs to a different player.
      let j = i + 1;
      while (j < src.length && src[j].uci === 'endturn') j++;
      const isNoPlay = j >= src.length || src[j].playedPlayerIndex !== pi;
      const node: ChartNode = {
        ply: n.ply,
        turnCount: n.turnCount,
        playedPlayerIndex: pi,
        san: isNoPlay ? BackgammonFamily.combinedNotation([actionNotation, '(no-play)']) : actionNotation,
        uci: n.uci,
        eval: t ? ev(t.best) : undefined,
      };
      // id 98 marks this as a dice-roll node so acpl.ts renders it as a square.
      const glyphs: Tree.Glyph[] = [{ id: 98, symbol: '', name: 'Dice roll' }];
      const luckGlyph = luckyToGlyph(t?.luck);
      if (luckGlyph) glyphs.push(luckGlyph);
      out.push({ ...node, glyphs });
    } else {
      turnNotations.push(actionNotation);
      // checker move: only emit the LAST one of this turn.
      // A move is last when the next src node is endturn, a different player's node, or EOF.
      const next = src[i + 1];
      const isLast =
        !next || next.uci === 'endturn' || (next.playedPlayerIndex !== undefined && next.playedPlayerIndex !== pi);
      if (isLast) {
        const t = turns[turnIdx];
        const node: ChartNode = {
          ply: n.ply,
          turnCount: n.turnCount,
          playedPlayerIndex: pi,
          san: BackgammonFamily.combinedNotation(turnNotations),
          uci: n.uci,
          eval: t ? ev(t.played) : undefined,
        };
        // id 99 / symbol 'd' = decision marker: used by christmasTree to find all decisions.
        // The error glyph (if any) is appended so pointsFor('??'/'!!') still matches.
        const errorGlyph = t?.noAnnotation ? undefined : errorToGlyph(t?.error ?? 0, t?.topMove, t?.topMoveGap); // thresholds in EMG
        const glyphs: Tree.Glyph[] = [];
        if (!t?.noAnnotation) glyphs.push({ id: 99, symbol: 'd', name: 'Decision' });
        if (errorGlyph) glyphs.push(errorGlyph);
        out.push(glyphs.length ? { ...node, glyphs } : node);
      }
    }
  }

  return out;
}

// Build candidate and turn-start-FEN maps keyed by decision ply.
// candidateMap: ply → candidates for that turn's decision.
// fenMap: ply → FEN after dice rolled, before any checker moves.
function buildCandidateMap(
  ctrl: AnalyseCtrl,
  a: BgAnalysis,
): { candidateMap: Map<number, BgCandidateUI[]>; fenMap: Map<number, string>; plyMap: Map<number, number> } {
  const map = new Map<number, BgCandidateUI[]>();
  const fenMap = new Map<number, string>();
  const plyMap = new Map<number, number>(); // decision ply → turn-start node ply
  const allMoves: BgMove[] = a.games.flatMap(g => g.moves);
  const src = ctrl.data.treeParts;

  const toCandidateUI = (move: BgMove, isP1: boolean): BgCandidateUI[] =>
    move.candidates.map(
      c =>
        ({
          rank: c.rank,
          play: c.play,
          played: c.played,
          isP1,
          equity: c.equity,
          equityDelta: c.equityDelta,
          probabilities: c.probabilities,
        }) as BgCandidateUI,
    );

  // Pre-pass: collect the turn-start FEN and ply (first node of each turn, before any checker moves).
  // The dice-roll node is the first node of each player's run but may have no fen of its own
  // (rolling dice doesn't change the board position, only the dice state).  In that case,
  // the endturn node that precedes it already carries the rolled dice for the coming turn,
  // so we fall back to prevFen (the last non-undefined FEN seen before this node).
  const turnStartByIdx = new Map<number, { fen: string; ply: number }>();
  {
    let preTurnIdx = -1;
    let prePrevPi: string | undefined;
    let prevFen: string = src[0]?.fen ?? '';
    for (let i = 1; i < src.length; i++) {
      const n = src[i];
      const priorFen = prevFen; // FEN of the previous non-undefined node, captured before this node
      if (n.fen) prevFen = n.fen; // update for subsequent nodes, including endturn
      if (n.uci === 'endturn') continue;
      const pi = n.playedPlayerIndex;
      if (pi !== undefined && pi !== prePrevPi) {
        preTurnIdx++;
        prePrevPi = pi;
        // The dice-roll node is the first node of each player's run.  It may have no fen
        // of its own (rolling dice doesn't move pieces).  In that case, the endturn node
        // that immediately precedes it already carries the rolled dice in its FEN — that
        // FEN is captured in priorFen and is the correct pre-move turn-start state.
        const startFen = n.fen ?? priorFen;
        if (startFen) turnStartByIdx.set(preTurnIdx, { fen: startFen, ply: n.ply });
      }
    }
  }

  // Pass 1: map every non-endturn ply to its own turn's candidates.
  // Only advance turnIdx on nodes with a defined playedPlayerIndex to avoid
  // false transitions on checker-move nodes that may have it undefined.
  let turnIdx = -1;
  let prevPi: string | undefined;
  let turnPlies: number[] = [];
  let currentIsP1 = true;

  for (let i = 1; i < src.length; i++) {
    const n = src[i];
    if (n.uci === 'endturn') continue;
    const pi = n.playedPlayerIndex;
    const isRoll = pi !== undefined && pi !== prevPi;
    if (isRoll) {
      turnIdx++;
      turnPlies = [];
      prevPi = pi;
      currentIsP1 = pi === 'p1';
    }
    turnPlies.push(n.ply);

    const next = src[i + 1];
    const isLast =
      !next || next.uci === 'endturn' || (next.playedPlayerIndex !== undefined && next.playedPlayerIndex !== pi);
    if (isLast) {
      const move = allMoves[turnIdx];
      if (move?.candidates?.length) {
        const mapped = toCandidateUI(move, currentIsP1);
        const turnStart = turnStartByIdx.get(turnIdx);
        for (const ply of turnPlies) {
          map.set(ply, mapped);
          if (turnStart) {
            fenMap.set(ply, turnStart.fen);
            plyMap.set(ply, turnStart.ply);
          }
        }
        // ply 0 is the root node (dice-picker phase); it has no tree entry of its own,
        // so explicitly expose the first turn's candidates there too.
        if (turnIdx === 0) {
          map.set(0, mapped);
          if (turnStart) {
            fenMap.set(0, turnStart.fen);
            plyMap.set(0, turnStart.ply);
          }
        }
      } else if (move?.kind === 'Dance') {
        // No-play (forced dance): store empty array so renderCandidates can show "No play".
        for (const ply of turnPlies) map.set(ply, []);
        if (turnIdx === 0) map.set(0, []);
      }
    }
  }

  // Pass 2: map each endturn ply to the NEXT turn's candidates (upcoming decision).
  // Turns alternate strictly in backgammon, so nextIsP1 = !currentIsP1.
  turnIdx = -1;
  prevPi = undefined;
  let currentIsP1P2 = true;
  for (let i = 1; i < src.length; i++) {
    const n = src[i];
    if (n.uci === 'endturn') continue;
    const pi2 = n.playedPlayerIndex;
    const isRoll2 = pi2 !== undefined && pi2 !== prevPi;
    if (isRoll2) {
      turnIdx++;
      prevPi = pi2;
      currentIsP1P2 = pi2 === 'p1';
    }
    const next = src[i + 1];
    if (next?.uci === 'endturn') {
      const nextMove = allMoves[turnIdx + 1];
      if (nextMove?.candidates?.length) {
        map.set(next.ply, toCandidateUI(nextMove, !currentIsP1P2));
        const nextTurnStart = turnStartByIdx.get(turnIdx + 1);
        if (nextTurnStart) {
          fenMap.set(next.ply, nextTurnStart.fen);
          plyMap.set(next.ply, nextTurnStart.ply);
        }
      } else if (nextMove?.kind === 'Dance') {
        map.set(next.ply, []);
      }
    }
  }

  return { candidateMap: map, fenMap, plyMap };
}

// Write glyphs directly onto ctrl.data.treeParts nodes so treeView.ts nodeClasses() colours them.
// Symbols are blank — colour via CSS only. Luck glyphs stack with skill glyphs.
function annotateTreeNodes(ctrl: AnalyseCtrl, turns: TurnVal[]): void {
  const src = ctrl.data.treeParts;
  let turnIdx = -1;
  let prevPi: string | undefined;
  for (let i = 1; i < src.length; i++) {
    const n = src[i];
    if (n.uci === 'endturn') continue;
    const pi = n.playedPlayerIndex;
    const isRoll = pi !== prevPi;
    prevPi = pi;
    if (isRoll) {
      turnIdx++;
      const t = turns[turnIdx];
      const isBlunder = !t?.noAnnotation && (t?.error ?? 0) >= 0.15;
      const isMistake = !isBlunder && !t?.noAnnotation && (t?.error ?? 0) >= 0.05;
      const isPerfect = !t?.noAnnotation && !!t?.topMove && (t?.topMoveGap ?? 0) >= 0.08;
      const glyphs: Tree.Glyph[] = [];
      if (isBlunder) glyphs.push({ id: 4, symbol: '', name: 'Blunder' });
      else if (isMistake) glyphs.push({ id: 2, symbol: '', name: 'Mistake' });
      else if (isPerfect) glyphs.push({ id: 3, symbol: '', name: 'Perfect play' });
      if ((t?.luck ?? 0) > 0.1) glyphs.push({ id: 51, symbol: '', name: 'Lucky roll' });
      else if ((t?.luck ?? 0) < -0.1) glyphs.push({ id: 52, symbol: '', name: 'Unlucky roll' });
      if (glyphs.length) n.glyphs = glyphs;
    }
  }
}

let handlersRegistered = false;

function registerHandlers(ctrl: AnalyseCtrl): void {
  const symbolToGlyphIds: Record<string, number[]> = {
    '??': [4],
    '?': [2],
    '!!': [3],
    '+': [51],
    '-': [52],
    d: [4, 2, 3], // PR: blunders, mistakes, perfect play
    luck: [51, 52], // lucky and unlucky rolls
  };
  // Incremented whenever analysis.chart.click fires (whether from acpl's christmasTree jQuery
  // handler or from our own fallback below). Used to detect whether christmasTree already
  // handled a given advice-summary click so we don't emit the pubsub events twice.
  let bgClickGeneration = 0;
  playstrategy.pubsub.on('analysis.chart.category.select', (symbol: string | null) => {
    ctrl.bgHighlightGlyphIds = symbol ? symbolToGlyphIds[symbol] : undefined;
    ctrl.bgHighlightSymbol = symbol || undefined;
    if (!symbol) ctrl.bgHighlightPlayerIndex = undefined;
    clearCandidatePreview(ctrl);
    ctrl.redraw();
  });
  // Fired after ctrl.ts's analysis.chart.click handler (registration order: ctrl first,
  // bgWinChart second). ctrl.ts has already called jumpToMain(lastCheckerPly) + redraw()
  // synchronously. Since redraw() is a synchronous Snabbdom patch, both renders are in
  // the same JS task — the browser only paints once at the end, no flash.
  // We do NOT navigate to turnStartPly here: ctrl.node.ply must stay at lastCheckerPly
  // so that analysis.change fires with that ply and state.currentPly (in acpl.ts) is
  // set past the glyph node, allowing the next symbol click to cycle to the next match.
  // renderCandidates auto-show overrides the board FEN to the turn-start position.
  let pointerInAdviceSummary = false;
  playstrategy.pubsub.on('analysis.chart.click', () => {
    bgClickGeneration++;
    clearCandidatePreview(ctrl);
    if (!pointerInAdviceSummary) {
      if (ctrl.bgHighlightSymbol) playstrategy.pubsub.emit('analysis.chart.category.select', null);
      return;
    }
    const sym = ctrl.bgHighlightSymbol;
    if (sym === '??' || sym === '?' || sym === '!!' || sym === 'd') {
      scheduleShowPlayed();
      ctrl.redraw();
    }
  });
  // Force a redraw on orientation/resize changes so isCol1() recomputes and the
  // detail-mode toggle button appears/disappears correctly on portrait↔landscape rotation.
  window.addEventListener('resize', () => ctrl.redraw());

  document.addEventListener('mousedown', (e: MouseEvent) => {
    const el = e.target as HTMLElement;
    pointerInAdviceSummary = !!el.closest?.('div.advice-summary');
    ctrl.bgKeepAnnotationLock = pointerInAdviceSummary || !!el.closest?.('div.bg-candidates');
  });
  document.addEventListener('keydown', () => (ctrl.bgKeepAnnotationLock = false));
  document.addEventListener('wheel', () => (ctrl.bgKeepAnnotationLock = false), { passive: true });

  // Capture player index when a symbol is clicked so Snabbdom can re-apply the locked
  // class after a mode switch recreates the DOM. Registered before acpl's jQuery handler
  // so it fires first and ctrl is updated before the pubsub-triggered redraw.
  // Also provides a fallback navigation path when acpl's christmasTree jQuery handler was
  // never registered (e.g. page loaded on move-times tab and the hidden canvas prevented
  // Chart.js from initialising): we emit the pubsub events ourselves unless bgClickGeneration
  // already changed during this click event (meaning christmasTree handled it).
  document.addEventListener('click', (e: MouseEvent) => {
    const el = (e.target as HTMLElement).closest?.('[data-symbol][data-playerindex]');
    if (!el?.closest?.('div.advice-summary')) return;
    const symbol = el.getAttribute('data-symbol') ?? undefined;
    const pi = (el.getAttribute('data-playerindex') ?? undefined) as PlayerIndex | undefined;
    ctrl.bgHighlightPlayerIndex = pi;
    if (!symbol || !pi) return;
    // Snapshot bgClickGeneration before jQuery's handlers run. christmasTree's jQuery
    // handler fires synchronously after this native listener (jQuery registers its own
    // native document listener which follows ours in registration order). If christmasTree
    // emits analysis.chart.click, bgClickGeneration increments synchronously. The
    // setTimeout(0) runs after all synchronous click handlers, so we can check.
    const genSnapshot = bgClickGeneration;
    setTimeout(() => {
      if (bgClickGeneration !== genSnapshot) return; // christmasTree already navigated
      // Find annotated roll nodes (from annotateTreeNodes) with matching glyph ID.
      // annotateTreeNodes annotates roll nodes (not decision nodes) with error/luck glyphs,
      // but bgTurnCandidates maps roll-node plies to candidates too, so navigating to the
      // roll ply shows the same candidate panel as navigating to the decision ply would.
      const glyphIds = symbolToGlyphIds[symbol] ?? [];
      if (!glyphIds.length) return;
      const plies: number[] = [];
      for (const n of ctrl.data.treeParts) {
        if (n.glyphs?.some(g => glyphIds.includes(g.id)) && n.playedPlayerIndex === pi) {
          plies.push(n.ply);
        }
      }
      if (!plies.length) return;
      plies.sort((a, b) => a - b);
      const currentPly = ctrl.node.ply;
      const ply = plies.find(p => p > currentPly) ?? plies[0];
      if (ply !== undefined) {
        playstrategy.pubsub.emit('analysis.chart.category.select', symbol);
        playstrategy.pubsub.emit('analysis.chart.click', ply);
      }
    }, 0);
  });
}

export default function bgWinChart(ctrl: AnalyseCtrl, panel: HTMLElement): Promise<boolean> {
  if (!handlersRegistered) {
    handlersRegistered = true;
    registerHandlers(ctrl);
  }

  return fetch(`/${ctrl.data.game.id}/backgammon-rating.json`, { headers: { Accept: 'application/json' } })
    .then(r => {
      if (!r.ok) throw r.status;
      return r.json();
    })
    .then((a: BgAnalysis) => {
      const turns = turnValues(a);
      annotateTreeNodes(ctrl, turns);
      const { candidateMap, fenMap, plyMap } = buildCandidateMap(ctrl, a);
      ctrl.bgTurnCandidates = candidateMap;
      ctrl.bgTurnStartFen = fenMap;
      ctrl.bgTurnStartPly = plyMap;
      ctrl.bgOriginalNodeIdByPly = new Map(ctrl.data.treeParts.map(n => [n.ply, n.id]));

      // Chain onto the existing onAfterAddDests (set in backgammon.ts for no-moves detection).
      // addDests() calls showGround() then this hook — we use it to re-apply the pre-move FEN
      // and arrows when an async dests response resets the board after a symbol click.
      const prevOnAfterAddDests = ctrl.controlConfig.onAfterAddDests;
      ctrl.controlConfig.onAfterAddDests = () => {
        prevOnAfterAddDests?.();
        reapplyFenOverride(ctrl);
      };

      // Hook ctrl.setAutoShapes so ceval's continuous evaluation arrows can't overwrite the
      // candidate preview.  ctrl.setAutoShapes() is called from showGround() (inside addDests)
      // AND from onNewCeval (the ceval background worker) — both fire asynchronously AFTER
      // reapplyFenOverride restores the candidate arrows, so without this hook they race and
      // erase the candidate shapes.  When a preview is active we re-apply it instead of
      // rendering eval arrows; once the preview is cleared the original behaviour is restored.
      const origSetAutoShapes = ctrl.setAutoShapes.bind(ctrl);
      ctrl.setAutoShapes = () => {
        if (!reapplyFenOverride(ctrl)) origSetAutoShapes();
      };

      // per-player event counts derived from turn values
      const cnt = {
        p1: { blunders: 0, mistakes: 0, perfectPlay: 0, luckyRolls: 0, unluckyRolls: 0, decisions: 0 },
        p2: { blunders: 0, mistakes: 0, perfectPlay: 0, luckyRolls: 0, unluckyRolls: 0, decisions: 0 },
      };
      for (const t of turns) {
        const key = t.player === a.player1 ? 'p1' : 'p2';
        if (!t.noAnnotation) {
          cnt[key].decisions++;
          if (t.error >= 0.15) cnt[key].blunders++;
          else if (t.error >= 0.05) cnt[key].mistakes++;
          else if (t.topMove && (t.topMoveGap ?? 0) >= 0.08) cnt[key].perfectPlay++;
        }
        if ((t.luck ?? 0) > 0.1) cnt[key].luckyRolls++;
        else if ((t.luck ?? 0) < -0.1) cnt[key].unluckyRolls++;
      }

      // extract per-player stats from the first game (typical single-game match)
      const firstGameStats = a.games[0]?.stats;
      if (firstGameStats) {
        const s1 = firstGameStats.find(s => s.player === a.player1);
        const s2 = firstGameStats.find(s => s.player === a.player2);
        if (s1 && s2) {
          ctrl.bgAnalysis = {
            p1: {
              errorRate: s1.overallErrorRate ?? 0,
              luck: s1.luckTotalEmg ?? 0,
              rating: s1.skill,
              luckRating: s1.luckRating,
              ...cnt.p1,
            },
            p2: {
              errorRate: s2.overallErrorRate ?? 0,
              luck: s2.luckTotalEmg ?? 0,
              rating: s2.skill,
              luckRating: s2.luckRating,
              ...cnt.p2,
            },
          };
          ctrl.redraw();
        }
      }
      const nodes = chartNodes(ctrl, turns);
      panel.querySelector('#acpl-chart-container-loader')?.remove();
      let container = panel.querySelector<HTMLElement>('#acpl-chart-container');
      if (!container) {
        panel.innerHTML = '';
        container = document.createElement('div');
        container.id = 'acpl-chart-container';
        panel.appendChild(container);
      }
      let canvas = container.querySelector<HTMLCanvasElement>('#acpl-chart');
      if (!canvas) {
        canvas = document.createElement('canvas');
        canvas.id = 'acpl-chart';
        container.appendChild(canvas);
      }
      // division is undefined for backgammon; default it so the chart doesn't throw.
      const chartData = {
        ...ctrl.data,
        game: { ...ctrl.data.game, division: ctrl.data.game.division || {} },
        treeParts: nodes,
      };
      playstrategy.loadModule('chart.game').then(() => {
        (window as any).PlayStrategyChartGame.acpl(canvas, chartData, nodes, ctrl.trans);
      });
      return true;
    })
    .catch(() => false);
}
