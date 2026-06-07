import AnalyseCtrl from './ctrl';

// NOTE(bg-analysis): reuse the chess advantage chart (public/javascripts/chart/acpl.js)
// for backgammon. It reads `data.treeParts[].eval.cp`, maps it through a sigmoid to a
// [-1,1] area chart, and (for backgammon) shows `eval.win` in the tooltip. We feed it
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
//    TODO(bg-analysis): cube offers/responses are extra decisions — handle for cube play).

interface BgProbs {
  win: number;
  lose: number;
}
interface BgCandidate {
  rank: number;
  played: boolean;
  probabilities: BgProbs;
}
interface BgMove {
  player: string;
  candidates: BgCandidate[];
}
interface BgGame {
  moves: BgMove[];
}
interface BgAnalysis {
  white: string;
  black: string;
  games: BgGame[];
}

// white's best-of-roll and played win% (0..1) for each turn, in play order
interface TurnVal {
  best: number;
  played: number;
}

// only the fields advantageChart reads off a node
interface ChartNode {
  ply: number;
  turnCount: number;
  playedPlayerIndex?: 'p1' | 'p2';
  san?: string;
  uci?: string;
  eval?: { cp: number; win: number };
}

// inverse of acpl.js's cp -> winChance map (y = 2/(1+e^-0.004cp) - 1), so feeding this
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
        // dance / no legal play: carry the previous value forward
        out.push(out.length ? out[out.length - 1] : { best: 0.5, played: 0.5 });
        continue;
      }
      // probabilities.win is the mover's win chance; flip to white's view
      const toWhite = (c: BgCandidate) => (m.player === a.white ? c.probabilities.win : c.probabilities.lose);
      out.push({ best: toWhite(best), played: toWhite(played) });
    }
  return out;
}

function ev(win: number): { cp: number; win: number } {
  return { cp: winToCp(win), win };
}

// Map the real per-action mainline onto chart nodes: dice-roll node = best (luck),
// checker/end nodes = played (skill).
function chartNodes(ctrl: AnalyseCtrl, turns: TurnVal[]): ChartNode[] {
  const src = ctrl.data.treeParts;
  const out: ChartNode[] = [];
  let turnIdx = -1;
  let prevPi: string | undefined;
  src.forEach((n, i) => {
    if (i === 0) {
      out.push({ ply: n.ply, turnCount: n.turnCount }); // root — sliced off by the chart
      return;
    }
    const pi = n.playedPlayerIndex;
    const isRoll = pi !== prevPi; // first action of a new turn
    prevPi = pi;
    if (isRoll) turnIdx++;
    const t = turns[turnIdx];
    const win = t ? (isRoll ? t.best : t.played) : undefined;
    out.push({
      ply: n.ply,
      turnCount: n.turnCount,
      playedPlayerIndex: pi,
      san: n.san,
      uci: n.uci,
      eval: win === undefined ? undefined : ev(win),
    });
  });
  return out;
}

export default function bgWinChart(ctrl: AnalyseCtrl, panel: HTMLElement): void {
  fetch(`/${ctrl.data.game.id}/backgammon-rating.json`, { headers: { Accept: 'application/json' } })
    .then(r => {
      if (!r.ok) throw r.status;
      return r.json();
    })
    .then((a: BgAnalysis) => {
      const nodes = chartNodes(ctrl, turnValues(a));
      panel.querySelector('#acpl-chart-loader')?.remove();
      let el = panel.querySelector('#acpl-chart');
      if (!el) {
        panel.innerHTML = '<div id="acpl-chart"></div>';
        el = panel.querySelector('#acpl-chart');
      }
      if (!el) return;
      const chartEl = el;
      // division is undefined for backgammon; divisionLines() dereferences it, so default it.
      const chartData = {
        ...ctrl.data,
        game: { ...ctrl.data.game, division: ctrl.data.game.division || {} },
        treeParts: nodes,
      };
      playstrategy.loadScriptCJS('javascripts/chart/acpl.js').then(() => {
        playstrategy.advantageChart!(chartData, ctrl.trans, chartEl as HTMLElement);
      });
    })
    .catch(() => {
      // TODO(bg-analysis): no stored analysis yet (404) or fetch/parse error — no-op
      // so the request form / existing content is left untouched.
    });
}
