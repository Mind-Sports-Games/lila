import { h, VNode } from 'snabbdom';
import { bind } from './util';
import AnalyseCtrl from './ctrl';
import * as game from 'game';
import * as stratUtils from 'stratutils';
import { findTag } from './study/studyChapters';
import { BackgammonAnalysisSide, BgCandidateUI } from './interfaces';
import type { Key as CgKey } from 'chessground/types';
import type { DrawShape as CgDrawShape } from 'chessground/draw';

const backgammonVariants = ['backgammon', 'hyper', 'nackgammon'];

export function isBackgammonVariant(key: string): boolean {
  return backgammonVariants.includes(key);
}

function playerName(ctrl: AnalyseCtrl, playerIndex: PlayerIndex): string {
  const p = game.getPlayer(ctrl.data, playerIndex);
  if (p.user) return p.user.username;
  if (p.ai) return 'Engine';
  if (ctrl.study) return findTag(ctrl.study.data.chapter.tags, playerIndex) || 'Anonymous';
  return 'Anonymous';
}

function renderCount(count: number, symbol: string, label: string, playerIndex: PlayerIndex, locked: boolean): VNode {
  const cls = label.toLowerCase().replace(/\s+/g, '-');
  return h(
    `div.advice-summary__${cls}${count ? '.symbol' : ''}`,
    count ? { attrs: { 'data-symbol': symbol, 'data-playerindex': playerIndex }, class: { locked } } : {},
    [h('strong', String(count)), ` ${label}`],
  );
}

function isLocked(ctrl: AnalyseCtrl, symbol: string, pi: PlayerIndex): boolean {
  return ctrl.bgHighlightSymbol === symbol && ctrl.bgHighlightPlayerIndex === pi;
}

// Neither figure explains itself: a PR runs the opposite way to a rating, and luck is signed.
// gnubg's own luck wording ("Haha! Bad dice, man!") is deliberately not quoted anywhere.
// Kept word for word in ui/round/src/bgAnalysis.ts, which shows the same two rows.
const prHelp = 'Performance rating. Lower is better. 0 is flawless play.';
const luckHelp = 'Negative = unlucky, positive = lucky.';

function renderSide(ctrl: AnalyseCtrl, playerIndex: PlayerIndex, side: BackgammonAnalysisSide): VNode {
  const p = game.getPlayer(ctrl.data, playerIndex);
  const luck = side.luck;
  const prRating = side.rating ?? '';
  return h('div.advice-summary__side', [
    h('div.advice-summary__player', [
      h(`i.is.playerIndex-icon.${p.playerColor}`),
      h('span', playerName(ctrl, playerIndex)),
    ]),
    h(
      'div.advice-summary__acpl.symbol',
      {
        attrs: {
          'data-symbol': 'd',
          'data-playerindex': playerIndex,
          title: prHelp,
        },
        class: { locked: isLocked(ctrl, 'd', playerIndex) },
      },
      [h('strong', Math.abs(side.errorRate / 2).toFixed(1)), h('span', ['PR ', h('em', prRating)])],
    ),
    renderCount(side.blunders, '??', 'Blunders', playerIndex, isLocked(ctrl, '??', playerIndex)),
    renderCount(side.mistakes, '?', 'Mistakes', playerIndex, isLocked(ctrl, '?', playerIndex)),
    renderCount(side.perfectPlay, '!!', 'Perfect play', playerIndex, isLocked(ctrl, '!!', playerIndex)),
    h(
      'div.advice-summary__acpl.advice-summary__luck-hdr.symbol',
      {
        attrs: {
          'data-symbol': 'luck',
          'data-playerindex': playerIndex,
          title: luckHelp,
        },
        class: { locked: isLocked(ctrl, 'luck', playerIndex) },
      },
      [
        h('strong', { class: { good: luck > 0.1, bad: luck < -0.1 } }, (luck >= 0 ? '+' : '') + luck.toFixed(2)),
        h('span', 'Luck'),
      ],
    ),
    renderCount(side.luckyRolls, '+', 'Lucky rolls', playerIndex, isLocked(ctrl, '+', playerIndex)),
    renderCount(side.unluckyRolls, '-', 'Unlucky rolls', playerIndex, isLocked(ctrl, '-', playerIndex)),
  ]);
}

// Backgammon board is 13 wide × 2 tall in chessground.
// Point mapping derived from calculateBackgammonScores (chessground/src/util.ts):
//   rank 2 (top row):    boardPos = 14 - col  →  point p ≤ 13: col = 14 - p
//   rank 1 (bottom row): boardPos = col + 13  →  point p ≥ 14: col = p - 13
const BG_FILES = 'abcdefghijklm';

// GNUBG uses player-relative point numbers (both players move 24→1 in their own frame).
// P1's GNUBG points equal P1-absolute board labels directly (identity).
// P2's GNUBG point p maps to absolute 25-p (P2 moves in the opposite direction).
function gnubgToAbsolute(p: number, isP1: boolean): number {
  return isP1 ? p : 25 - p;
}

// Convert a P2 GNUBG play string (P2-relative) to P1-absolute notation for display.
// The board always shows P1-absolute labels, so arrows and text must use the same system.
function p2PlayToP1Absolute(play: string): string {
  return play
    .split(' ')
    .filter(Boolean)
    .map(token =>
      token
        .split('/')
        .map(part => {
          const hit = part.endsWith('*');
          const clean = hit ? part.slice(0, -1) : part;
          if (clean === 'bar' || clean === 'off') return part;
          // Extract and preserve the "(n)" count suffix before parsing the point number.
          const countSuffix = clean.match(/\(\d+\)$/)?.[0] ?? '';
          const cleanNoCount = countSuffix ? clean.slice(0, -countSuffix.length) : clean;
          const n = parseInt(cleanNoCount, 10);
          return isNaN(n) ? part : String(25 - n) + (hit ? '*' : '') + countSuffix;
        })
        .join('/'),
    )
    .join(' ');
}

function bgPointToKey(absolutePoint: number): CgKey {
  if (absolutePoint < 13) return (BG_FILES[12 - absolutePoint] + '1') as CgKey;
  return (BG_FILES[absolutePoint - 13] + '2') as CgKey;
}

interface CheckerMove {
  from: number | null;
  to: number | null;
  hit: boolean;
  count: number;
}

// Parse GNUBG play notation like "24/20 8/7*" or "13/7/3" (multi-hop = one checker, two dice).
// "A/B/C" generates hops A→B and B→C. "(n)" suffix means n pieces make the same move (doubles).
function parseGnubgPlay(play: string, isP1: boolean): CheckerMove[] {
  const moves: CheckerMove[] = [];
  for (const token of play.split(' ').filter(Boolean)) {
    const parts = token.split('/');
    // (n) suffix is always on the last segment (e.g. "13/7/3(2)") — extract once
    // and apply to every hop so multi-hop tokens share the same count.
    const lastSeg = parts[parts.length - 1];
    const countMatch = lastSeg.match(/\((\d+)\)/);
    const count = countMatch ? parseInt(countMatch[1], 10) : 1;
    for (let i = 0; i < parts.length - 1; i++) {
      const fromStr = parts[i].replace('*', '');
      const toStr = parts[i + 1];
      const hit = toStr.endsWith('*');
      const toClean = toStr
        .replace('*', '')
        .replace(/\(\d+\)$/, '')
        .toLowerCase();
      const fromRaw = fromStr.toLowerCase() === 'bar' ? null : parseInt(fromStr, 10);
      const toRaw = toClean === 'off' ? null : parseInt(toClean, 10);
      moves.push({
        from: fromRaw !== null ? gnubgToAbsolute(fromRaw, isP1) : null,
        to: toRaw !== null ? gnubgToAbsolute(toRaw, isP1) : null,
        hit,
        count,
      });
    }
  }
  return moves;
}

let expandedCandidateRank = -1;
let shownCandidateRank = -1;
let lastCandidatePly = -1;
let pendingArrows: CgDrawShape[] = [];
let showPlayedOnNextRender = false;
// Stores the FEN we're currently overriding the board with (pre-move position) so it can
// be re-applied if showGround() resets it (e.g. async dests response via addDests).
let activeFenOverride: { fen: string; dice: unknown[] } | null = null;
let activeFenOverridePly = -1;

export function scheduleShowPlayed(): void {
  showPlayedOnNextRender = true;
}

// Re-apply the board FEN override and arrows.
// Returns true if the override was active and applied (caller should skip its own setAutoShapes).
// Called from onAfterAddDests (fires after showGround in addDests) and from the ctrl.setAutoShapes
// hook in bgWinChart.ts, which intercepts ceval's continuous setAutoShapes calls so they can't
// overwrite the candidate arrows.
export function reapplyFenOverride(ctrl: AnalyseCtrl): boolean {
  // Prevents displaying the dice picker and player dice underneath the picker overlay.
  if (ctrl.controlConfig.isBoardOverlayActive?.()) {
    activeFenOverride = null;
    pendingArrows = [];
    expandedCandidateRank = -1;
    shownCandidateRank = -1;
    return false;
  }
  if (!activeFenOverride || ctrl.node.ply !== activeFenOverridePly || pendingArrows.length === 0) {
    activeFenOverride = null;
    return false;
  }
  ctrl.chessground.set({ fen: activeFenOverride.fen, dice: activeFenOverride.dice as never, cubeActions: [] });
  ctrl.chessground.setAutoShapes(pendingArrows);
  // showGround() calls cg.redrawAll() synchronously (needsFullRedrawAfterGround=true for
  // backgammon) BEFORE calling setAutoShapes, drawing shapes against POST-MOVE state.pieces.
  // We counter this by calling redrawAll() immediately after restoring PRE-MOVE + shapes.
  ctrl.chessground.redrawAll();
  return true;
}

// Build chessground arrow/circle shapes for a candidate's play string.
// ctrl.chessground.set({fen}) must be called before this so state.pieces is current.
function buildArrowShapes(ctrl: AnalyseCtrl, c: BgCandidateUI): CgDrawShape[] {
  if (!c.play) return [];
  const myPlayerIndex = c.isP1 ? 'p1' : 'p2';
  // Why "19/20 19/24" produces two visually distinct arrows but "19/20(3)" cannot:
  //
  // "19/20 19/24" — two tokens, same origin, DIFFERENT destinations.
  //   originUsedCount tracks pieces already gone from 19: the 2nd token gets
  //   stackOffset:-1 so its tail starts one step lower in the stack. The two arrows
  //   diverge toward different squares, so they look distinct even with the same
  //   horizontal origin column.
  //
  // "19/20(3)" — one token with count=3, SAME origin AND destination.
  //   Three arrows orig→dest at stackOffset 0/-1/-2 would all converge to the
  //   exact same tip pixel, producing three overlapping lines that look like one.
  //   We draw 1 arrow + blue circles for the extra pieces instead.
  const originUsedCount = new Map<CgKey, number>();
  const destArrivedCount = new Map<CgKey, number>();
  return parseGnubgPlay(c.play, c.isP1).flatMap((m): CgDrawShape[] => {
    if (m.from !== null && m.to !== null) {
      const fromKey = bgPointToKey(m.from);
      const destKey = bgPointToKey(m.to);
      const alreadyLeft = originUsedCount.get(fromKey) ?? 0;
      originUsedCount.set(fromKey, alreadyLeft + m.count);
      const arrivedSoFar = destArrivedCount.get(destKey) ?? 0;
      destArrivedCount.set(destKey, arrivedSoFar + m.count);
      // destStackOffset: +1 if destination has own pieces (tip lands on top of the stack,
      // since pos2px returns the topmost existing piece's cy, not the landing position),
      // +arrivedSoFar for additional arrows to the same destination this move.
      const destPiece = ctrl.chessground.state.pieces.get(destKey);
      const destIsOwn = destPiece?.playerIndex === myPlayerIndex;
      const destOffset = (destIsOwn ? 1 : 0) + arrivedSoFar;
      const shapes: CgDrawShape[] = [
        {
          orig: fromKey,
          dest: destKey,
          brush: 'blue',
          ...(alreadyLeft > 0 ? { stackOffset: -alreadyLeft } : {}),
          ...(destOffset > 0 ? { destStackOffset: destOffset } : {}),
        },
      ];
      for (let i = 1; i < m.count; i++) shapes.push({ orig: fromKey, brush: 'blue', stackOffset: -(alreadyLeft + i) });
      return shapes;
    }
    if (m.from === null && m.to !== null) {
      // Bar entry: red circle(s) at landing point.
      // +1 if destination has own pieces, same reasoning as arrow tips above.
      const destKey = bgPointToKey(m.to);
      const destPiece = ctrl.chessground.state.pieces.get(destKey);
      const base = destPiece?.playerIndex === myPlayerIndex ? 1 : 0;
      return Array.from({ length: m.count }, (_, i) => ({
        orig: destKey,
        brush: 'red',
        ...(base + i > 0 ? { stackOffset: base + i } : {}),
      }));
    }
    if (m.from !== null && m.to === null) {
      // Bearing off: green circle(s) at origin, downward through the stack.
      // Use originUsedCount so a bearing off from the same point as an arrow
      // (e.g. "21/off 21/23") doesn't overlap with it.
      const fromKey = bgPointToKey(m.from);
      const alreadyLeft = originUsedCount.get(fromKey) ?? 0;
      originUsedCount.set(fromKey, alreadyLeft + m.count);
      return Array.from({ length: m.count }, (_, i) => ({
        orig: fromKey,
        brush: 'green',
        ...(alreadyLeft + i > 0 ? { stackOffset: -(alreadyLeft + i) } : {}),
      }));
    }
    return [];
  });
}

function renderCandidates(ctrl: AnalyseCtrl): VNode | undefined {
  const ply = ctrl.node.ply;

  if (ply !== lastCandidatePly) {
    lastCandidatePly = ply;
    // Start with nothing selected — played row is always visually expanded via c.played.
    expandedCandidateRank = -1;
    shownCandidateRank = -1;
    pendingArrows = [];
    activeFenOverride = null;
    // Clear arrows immediately on ply change; without this, if the new ply has no
    // candidates the early return below fires before setAutoShapes([]) is ever called.
    ctrl.chessground?.setAutoShapes([]);
  }

  // Consume the auto-show flag here, before any early returns, so it never leaks
  // to a subsequent ply's render if this ply has no candidates or is a variation node.
  const shouldAutoShow = showPlayedOnNextRender;
  showPlayedOnNextRender = false;

  // Only show candidates when the current node is part of the original game — not in
  // user-added variations (even if a variation was promoted to mainline by the user).
  if (ctrl.bgOriginalNodeIdByPly?.get(ply) !== ctrl.node.id) return undefined;

  const candidates = ctrl.bgTurnCandidates?.get(ply);
  if (candidates === undefined) return undefined;

  if (candidates.length === 0) {
    return h('div.bg-candidates', [
      h('div.bg-candidates__header', 'Top moves'),
      h('div.bg-candidates__no-play', 'No play'),
    ]);
  }

  // Auto-show the played move's arrows when navigation was triggered by a symbol click
  // (blunder/mistake/perfect play), so the board immediately shows what was played.
  if (shouldAutoShow) {
    const played = candidates.find(c => c.played);
    if (played) {
      const turnStartFen = ctrl.bgTurnStartFen?.get(ply);
      const targetFen = turnStartFen ?? ctrl.node.fen;
      const dice = stratUtils.backgammon.readDice(targetFen, ctrl.data.game.variant.key);
      activeFenOverride = { fen: targetFen, dice };
      activeFenOverridePly = ply;
      ctrl.chessground.set({ fen: targetFen, dice, cubeActions: [] });
      pendingArrows = buildArrowShapes(ctrl, played);
      shownCandidateRank = played.rank;
      ctrl.chessground.setAutoShapes(pendingArrows);
      // Force an immediate synchronous draw so shapes are positioned against PRE-MOVE
      // state.pieces before any subsequent redrawAll() (from needsFullRedrawAfterGround) can
      // render them against POST-MOVE pieces.
      ctrl.chessground.redrawAll();
      ctrl.controlConfig.dismissBoardOverlay?.();
    }
  }

  ctrl.chessground.setAutoShapes(pendingArrows);

  const rows = candidates.map((c: BgCandidateUI) => {
    const expanded = c.played || expandedCandidateRank === c.rank;
    const showing = shownCandidateRank === c.rank;
    const deltaStr = c.equityDelta != null ? (c.equityDelta >= 0 ? '+' : '') + c.equityDelta.toFixed(3) : '—';
    const p = c.probabilities;
    // key includes ply so Snabbdom recreates elements on ply change,
    // triggering hook.insert with the new candidates' closures.
    return h(
      `div.bg-candidates__row${c.played ? '.played' : ''}${expanded ? '.expanded' : ''}${showing ? '.showing' : ''}`,
      {
        key: `${ply}-${c.rank}`,
        hook: bind('click', () => {
          const wasSelected = expandedCandidateRank === c.rank;
          expandedCandidateRank = wasSelected ? -1 : c.rank;
          shownCandidateRank = wasSelected ? -1 : c.rank;

          if (wasSelected) {
            // Deselecting: navigate to the turn-start node so the board shows the dice-rolled
            // position and all subsequent navigation works from the correct tree position.
            activeFenOverride = null;
            pendingArrows = [];
            ctrl.chessground.setAutoShapes([]);
            const turnStartPly = ctrl.bgTurnStartPly?.get(ply);
            if (turnStartPly !== undefined) {
              ctrl.jumpToMain(turnStartPly); // showGround + afterJump handled internally
            } else {
              ctrl.controlConfig.afterJump?.();
            }
            // ctrl.jump() does NOT redraw — and when we're already on the turn-start node the
            // jump is a no-op anyway. Without this the row keeps its highlight after deselect:
            // setAutoShapes() clears the arrows imperatively, but the list is only vdom.
            ctrl.redraw();
            return;
          }

          // Selecting: show bgTurnStartFen + arrows as a preview.
          // If on an endturn node (dice picker), navigate to the roll node first so the
          // moves tree highlights the current turn rather than the previous turn's last move.
          const turnStartPly = ctrl.bgTurnStartPly?.get(ply);
          if (ctrl.node.uci === 'endturn' && turnStartPly !== undefined) {
            // Update lastCandidatePly before navigating so the ply-change guard in
            // renderCandidates doesn't reset expandedCandidateRank on the next render.
            lastCandidatePly = turnStartPly;
            ctrl.jumpToMain(turnStartPly);
          }
          // Set FEN so state.pieces reflects the turn-start position when we build shapes.
          // Also store it in activeFenOverride so reapplyFenOverride() can re-apply it
          // after addDests() resets the board (addDests fires async, after redrawAll's rAF,
          // so without this the shapes render against the post-move pieces, off by 1).
          const turnStartFen = ctrl.bgTurnStartFen?.get(ply);
          const targetFen = turnStartFen ?? ctrl.node.fen;
          const dice = stratUtils.backgammon.readDice(targetFen, ctrl.data.game.variant.key);
          activeFenOverride = { fen: targetFen, dice };
          activeFenOverridePly = ctrl.node.ply;
          ctrl.chessground.set({ fen: targetFen, dice, cubeActions: [] });
          // setAutoShapes must come BEFORE redrawAll() because redrawAll() calls redrawNow()
          // synchronously and renders the SVG immediately — stale shapes would flash briefly.
          pendingArrows = buildArrowShapes(ctrl, c);
          ctrl.chessground?.setAutoShapes(pendingArrows);
          // Dismiss any dice picker overlay so the board stays visible during preview.
          ctrl.controlConfig.dismissBoardOverlay?.();
          ctrl.chessground.redrawAll();
          ctrl.redraw();
        }),
      },
      [
        h('div.bg-candidates__main', [
          h('span.bg-candidates__move', c.play ? (c.isP1 ? c.play : p2PlayToP1Absolute(c.play)) : '—'),
          h('span.bg-candidates__meta', [
            h('span.rank', `${c.rank}.`),
            h(`span.delta${c.equityDelta != null && c.equityDelta < -0.04 ? '.bad' : ''}`, deltaStr),
          ]),
        ]),
        expanded
          ? h('div.bg-candidates__probs', [
              h('span', `Win ${(p.win * 100).toFixed(1)}%`),
              h('span', `G ${(p.winGammon * 100).toFixed(1)}%`),
              h('span', `BG ${(p.winBackgammon * 100).toFixed(1)}%`),
              h('span.sep', '|'),
              h('span', `Lose ${(p.lose * 100).toFixed(1)}%`),
              h('span', `G ${(p.loseGammon * 100).toFixed(1)}%`),
              h('span', `BG ${(p.loseBackgammon * 100).toFixed(1)}%`),
            ])
          : null,
      ],
    );
  });

  return h('div.bg-candidates', [h('div.bg-candidates__header', 'Top moves'), h('div.bg-candidates__list', rows)]);
}

export function render(ctrl: AnalyseCtrl): VNode | undefined {
  if (ctrl.studyPractice || ctrl.embed) return;
  if (!isBackgammonVariant(ctrl.data.game.variant.key)) return;
  if (!ctrl.bgAnalysis) return h('div.analyse__acpl');
  const candidates = renderCandidates(ctrl);
  return h('div.analyse__acpl', [
    h('div.advice-summary', [renderSide(ctrl, 'p1', ctrl.bgAnalysis.p1), renderSide(ctrl, 'p2', ctrl.bgAnalysis.p2)]),
    ...(candidates ? [candidates] : []),
  ]);
}

// Separate exports for detail mode — each becomes its own CSS grid item
export function renderAdviceSummary(ctrl: AnalyseCtrl): VNode | undefined {
  if (ctrl.studyPractice || ctrl.embed) return;
  if (!isBackgammonVariant(ctrl.data.game.variant.key)) return;
  if (!ctrl.bgAnalysis) return;
  return h('div.advice-summary', [
    renderSide(ctrl, 'p1', ctrl.bgAnalysis.p1),
    renderSide(ctrl, 'p2', ctrl.bgAnalysis.p2),
  ]);
}

export function renderTopMoves(ctrl: AnalyseCtrl): VNode | undefined {
  if (ctrl.studyPractice || ctrl.embed) return;
  if (!isBackgammonVariant(ctrl.data.game.variant.key)) return;
  return renderCandidates(ctrl);
}

// Clear an active candidate preview without navigating.
// Used when the user navigates externally (e.g. chart click, advice summary click) —
// we only need to wipe the visual state; the caller handles navigation itself.
// Setting lastCandidatePly=-1 forces the ply-change guard to fire on the next render
// even if the new ply happens to equal the old preview ply (same turn blunder).
export function clearCandidatePreview(ctrl: AnalyseCtrl): void {
  if (expandedCandidateRank === -1) return;
  expandedCandidateRank = -1;
  shownCandidateRank = -1;
  pendingArrows = [];
  lastCandidatePly = -1;
  activeFenOverride = null;
  ctrl.chessground?.setAutoShapes([]);
}

// Dismiss an active candidate preview, navigating to the turn-start node.
// Returns true if a preview was active (caller should skip navigation in that case).
// After dismissal, ctrl.node is the turn-start node (dice rolled, no moves yet), so
// subsequent prev/next presses work naturally from the correct tree position.
export function dismissCandidatePreview(ctrl: AnalyseCtrl): boolean {
  if (expandedCandidateRank === -1) return false;
  expandedCandidateRank = -1;
  shownCandidateRank = -1;
  pendingArrows = [];
  activeFenOverride = null;
  ctrl.chessground?.setAutoShapes([]);
  const turnStartPly = ctrl.bgTurnStartPly?.get(ctrl.node.ply);
  if (turnStartPly !== undefined) {
    ctrl.jumpToMain(turnStartPly); // showGround + afterJump handled internally
  } else {
    ctrl.controlConfig.afterJump?.();
  }
  ctrl.redraw(); // ctrl.jump() does not redraw; the candidate list needs it to drop the highlight
  return true;
}
