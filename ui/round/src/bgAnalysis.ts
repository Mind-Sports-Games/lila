import { attributesModule, classModule, h, init, VNode } from 'snabbdom';
import * as round from './round';
import * as xhr from 'common/xhr';
import * as status from 'game/status';
import { defined } from 'common';
import { game as gameRoute } from 'game/router';
import { RoundData } from './interfaces';

const patch = init([classModule, attributesModule]);

const backgammonVariants = ['backgammon', 'hyper', 'nackgammon'];

// gnubg work is queued behind every other game finishing at the same moment, so poll for a
// while before deciding no analysis is coming. The socket normally beats every retry.
const retryDelay = 20 * 1000;
const giveUpAfter = 10 * 60 * 1000;

// /<id>/backgammon-rating.json, the subset the round page shows.
interface StatsJson {
  player: string;
  overallErrorRate?: number; // mEMG per decision, twice the PR
  luckTotalEmg?: number;
  skill?: string;
  luckRating?: string;
}
interface MatchJson {
  player1: string;
  player2: string;
  games: { stats?: StatsJson[] }[];
}

interface Side {
  name: string;
  playerColor: string;
  pr?: number;
  rating?: string;
  luck?: number;
  luckRating?: string;
}

// Mirrors BackgammonAutoAnalyser.analysable: gnubg only runs on finished games between two
// accounts, and never bot vs bot.
function expectsAnalysis(d: RoundData): boolean {
  if (!backgammonVariants.includes(d.game.variant.key)) return false;
  if (!status.finished(d) || d.game.turns - (d.game.startedAtTurn ?? 0) <= 4) return false;
  const users = [d.player.user, d.opponent.user];
  if (users.some(u => !u)) return false;
  return users.some(u => u!.title !== 'BOT');
}

// The analysis board reads the same first game, so both views quote the same numbers.
function readSides(d: RoundData, m: MatchJson): [Side, Side] | undefined {
  const stats = m.games[0]?.stats;
  if (!stats) return undefined;
  const side = (playerIndex: 'p1' | 'p2', gnubgName: string): Side | undefined => {
    const stat = stats.find(x => x.player === gnubgName);
    if (!stat) return undefined;
    const player = d.player.playerIndex === playerIndex ? d.player : d.opponent;
    return {
      name: player.user?.username ?? player.playerName,
      playerColor: player.playerColor,
      // PR is half the error rate, and positive by convention: gnubg reports an equity loss.
      pr: defined(stat.overallErrorRate) ? Math.abs(stat.overallErrorRate / 2) : undefined,
      rating: stat.skill,
      luck: stat.luckTotalEmg,
      luckRating: stat.luckRating === 'None' ? undefined : stat.luckRating,
    };
  };
  const p1 = side('p1', m.player1),
    p2 = side('p2', m.player2);
  return p1 && p2 ? [p1, p2] : undefined;
}

function renderSide(s: Side): VNode {
  const luck = s.luck;
  return h('div.bg-analysis__side', [
    h('div.bg-analysis__player', [h(`i.is.playerIndex-icon.${s.playerColor}`), h('span', s.name)]),
    h('div.bg-analysis__stat', { attrs: { title: s.rating ? `PR: ${s.rating}` : 'Performance rating' } }, [
      h('strong', defined(s.pr) ? s.pr.toFixed(1) : '–'),
      h('span', ['PR ', s.rating ? h('em', s.rating) : null]),
    ]),
    // gnubg's luck wording stays on the title only, as in the analysis board's advice summary.
    h('div.bg-analysis__stat', { attrs: { title: s.luckRating ? `Luck: ${s.luckRating}` : 'Luck' } }, [
      h('strong', defined(luck) ? (luck >= 0 ? '+' : '') + luck.toFixed(2) : '–'),
      h('span', 'Luck'),
    ]),
  ]);
}

export default class BgAnalysisCtrl {
  private sides?: [Side, Side];
  private pending = false;
  private deadline = 0;
  private timer?: number;
  private vnode?: VNode;

  // A getter, not the object: RoundController.reload swaps its data wholesale.
  constructor(private readonly data: () => RoundData) {}

  // Called once the game is over — on page load for an already finished game, and again from
  // endWithData when it ends under us.
  start = (): void => {
    if (this.pending || this.sides || !expectsAnalysis(this.data())) return;
    this.pending = true;
    this.deadline = performance.now() + giveUpAfter;
    this.render();
    this.fetch();
  };

  // "bgAnalysisProgress" — gnubg has posted its result.
  onProgress = (): void => {
    if (this.pending) this.fetch();
  };

  private fetch = (): void => {
    clearTimeout(this.timer);
    xhr
      .json(`/${this.data().game.id}/backgammon-rating.json`)
      .then((m: MatchJson) => {
        this.sides = readSides(this.data(), m);
        this.pending = !this.sides;
        this.render();
        if (this.pending) this.retry();
      })
      .catch(this.retry);
  };

  private retry = (): void => {
    if (!this.pending) return;
    if (performance.now() > this.deadline) {
      this.pending = false;
      this.render();
    } else this.timer = setTimeout(this.fetch, retryDelay);
  };

  // Every part of the panel opens the analysis board at the final position, where the move
  // tree and charts show what these two numbers are made of.
  private link = (cls: string, children: VNode[]): VNode => {
    const d = this.data();
    return h(
      `a.round__bg-analysis.bg-analysis${cls}`,
      { attrs: { href: `${gameRoute(d, d.player.playerIndex)}/analysis#${round.lastPly(d)}` } },
      children,
    );
  };

  private view = (): VNode | undefined => {
    if (this.sides)
      return this.link('', [
        h('div.bg-analysis__title', [h('span', 'Computer analysis'), h('i', { attrs: { 'data-icon': 'A' } })]),
        h('div.bg-analysis__sides', this.sides.map(renderSide)),
      ]);
    if (this.pending)
      return this.link('.bg-analysis--pending', [h('i.ddloader'), h('span', 'Computer analysis in progress…')]);
    return undefined;
  };

  // Lives under the crosstable in the underboard, which sits outside the round app's vnode
  // tree, so this owns its own mount. boot.ts swaps the crosstable out on game end; the panel
  // is a sibling after it, so it survives that.
  private render = (): void => {
    const blueprint = this.view();
    if (!blueprint) {
      (this.vnode?.elm as HTMLElement | undefined)?.remove();
      this.vnode = undefined;
      return;
    }
    if (this.vnode) this.vnode = patch(this.vnode, blueprint);
    else {
      const under = document.querySelector('.round__underboard');
      if (!under) return;
      const el = document.createElement('div');
      const crosstable = under.querySelector('.crosstable');
      if (crosstable) crosstable.insertAdjacentElement('afterend', el);
      else under.prepend(el);
      this.vnode = patch(el, blueprint);
    }
  };
}
