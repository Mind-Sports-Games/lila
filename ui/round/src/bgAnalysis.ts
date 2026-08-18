import { attributesModule, classModule, h, init, VNode } from 'snabbdom';
import * as round from './round';
import * as xhr from 'common/xhr';
import * as status from 'game/status';
import isCol1 from 'common/isCol1';
import { allowFishnetForVariant } from 'stratutils';
import { defined } from 'common';
import { game as gameRoute } from 'game/router';
import { Redraw, RoundData } from './interfaces';

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
}

// Mirrors BackgammonAutoAnalyser.analysable: gnubg only runs on finished games between two
// accounts, and never bot vs bot.
function expectsAnalysis(d: RoundData): boolean {
  if (!backgammonVariants.includes(d.game.variant.key)) return false;
  if (!allowFishnetForVariant(d.game.variant.key)) return false;
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
    };
  };
  const p1 = side('p1', m.player1),
    p2 = side('p2', m.player2);
  return p1 && p2 ? [p1, p2] : undefined;
}

// Neither figure explains itself: a PR runs the opposite way to a rating, and luck is signed.
// gnubg's own luck wording ("Haha! Bad dice, man!") is deliberately not quoted anywhere.
// Kept word for word in ui/analyse/src/backgammonAnalysis.ts, which shows the same two rows.
const prHelp = 'Performance rating. Lower is better. 0 is flawless play.';
const luckHelp = 'Negative = unlucky, positive = lucky.';

function renderSide(s: Side): VNode {
  const luck = s.luck;
  return h('div.bg-analysis__side', [
    h('div.bg-analysis__player', [h(`i.is.playerIndex-icon.${s.playerColor}`), h('span', s.name)]),
    h('div.bg-analysis__stat', { attrs: { title: prHelp } }, [
      h('strong', defined(s.pr) ? s.pr.toFixed(1) : '–'),
      h('span', ['PR ', s.rating ? h('em', s.rating) : null]),
    ]),
    h('div.bg-analysis__stat', { attrs: { title: luckHelp } }, [
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

  constructor(
    // A getter, not the object: RoundController.reload swaps its data wholesale.
    private readonly data: () => RoundData,
    private readonly redraw: Redraw,
  ) {
    // Rotating a phone moves the panel between the two homes below, so follow the breakpoint.
    window.addEventListener('resize', () => {
      if (this.pending || this.sides) this.render();
    });
  }

  start = (justFinished = false): void => {
    if (this.pending || this.sides || !expectsAnalysis(this.data())) return;
    if (justFinished) {
      this.pending = true;
      this.deadline = performance.now() + giveUpAfter;
      this.render();
    }
    this.fetch();
  };

  onProgress = (): void => {
    if (!this.sides) this.fetch();
  };

  private fetch = (): void => {
    clearTimeout(this.timer);
    xhr
      .json(`/${this.data().game.id}/backgammon-rating.json`)
      .then((m: MatchJson) => {
        this.sides = readSides(this.data(), m);
        this.pending = this.pending && !this.sides;
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

  // Read by view/main.ts, which is the one column home. Empty elsewhere, so the app grid keeps
  // no row for it.
  view = (): VNode | undefined => {
    if (!isCol1()) return undefined;
    return this.panel();
  };

  private panel = (): VNode | undefined => {
    if (this.sides)
      return this.link('', [
        h('div.bg-analysis__title', [h('span', 'Computer analysis'), h('i', { attrs: { 'data-icon': 'A' } })]),
        h('div.bg-analysis__sides', this.sides.map(renderSide)),
      ]);
    if (this.pending)
      return this.link('.bg-analysis--pending', [h('i.ddloader'), h('span', 'Computer analysis in progress…')]);
    return undefined;
  };

  // Two homes. At one column the whole page is a single scroll, so the panel goes just above the
  // rematch buttons where it is in view the moment the game ends: that means living inside the
  // round app's grid, which only its own vnode tree can do. Wider layouts head the underboard
  // instead, above the crosstable — outside that tree, hence the mount below. boot.ts swaps the
  // crosstable out on game end, which leaves a sibling before it alone.
  private render = (): void => {
    if (isCol1()) this.unmount();
    else this.mount();
    this.redraw();
  };

  private mount = (): void => {
    const blueprint = this.panel();
    if (!blueprint) return this.unmount();
    if (this.vnode) this.vnode = patch(this.vnode, blueprint);
    else {
      const under = document.querySelector('.round__underboard');
      if (!under) return;
      const el = document.createElement('div');
      under.prepend(el);
      this.vnode = patch(el, blueprint);
    }
  };

  private unmount = (): void => {
    (this.vnode?.elm as HTMLElement | undefined)?.remove();
    this.vnode = undefined;
  };
}
