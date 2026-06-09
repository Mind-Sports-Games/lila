import { h, VNode } from 'snabbdom';
import AnalyseCtrl from './ctrl';
import * as game from 'game';
import { findTag } from './study/studyChapters';
import { BackgammonAnalysisSide } from './interfaces';

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

function erLabel(er: number): string {
  if (er < 2) return 'World class';
  if (er < 4) return 'Expert';
  if (er < 6) return 'Advanced';
  if (er < 10) return 'Intermediate';
  if (er < 15) return 'Casual';
  return 'Beginner';
}

function luckLabel(luck: number): string {
  if (luck > 0.3) return 'Very lucky';
  if (luck > 0.1) return 'Lucky';
  if (luck < -0.3) return 'Very unlucky';
  if (luck < -0.1) return 'Unlucky';
  return 'Neutral';
}

function renderCount(count: number, symbol: string, label: string, playerIndex: PlayerIndex): VNode {
  return h(
    `div.advice-summary__mistake${count ? '.symbol' : ''}`,
    count ? { attrs: { 'data-symbol': symbol, 'data-playerindex': playerIndex } } : {},
    [h('strong', String(count)), ` ${label}`],
  );
}

function renderSide(ctrl: AnalyseCtrl, playerIndex: PlayerIndex, side: BackgammonAnalysisSide): VNode {
  const p = game.getPlayer(ctrl.data, playerIndex);
  const luck = side.luck;
  return h('div.advice-summary__side', [
    h('div.advice-summary__player', [
      h(`i.is.playerIndex-icon.${p.playerColor}`),
      h('span', playerName(ctrl, playerIndex)),
    ]),
    h(
      'div.advice-summary__acpl.symbol',
      {
        attrs: { 'data-symbol': 'd', 'data-playerindex': playerIndex },
      },
      [
        h('strong', (side.errorRate / 2).toFixed(1)),
        h('span', ['PR ', h('em', side.rating || erLabel(side.errorRate))]),
      ],
    ),
    renderCount(side.blunders, '??', 'Blunders', playerIndex),
    renderCount(side.mistakes, '?', 'Mistakes', playerIndex),
    renderCount(side.perfectPlay, '!!', 'Perfect play', playerIndex),
    h(
      'div.advice-summary__acpl.advice-summary__luck-hdr.symbol',
      {
        attrs: { 'data-symbol': 'luck', 'data-playerindex': playerIndex },
      },
      [
        h('strong', { class: { good: luck > 0.1, bad: luck < -0.1 } }, (luck >= 0 ? '+' : '') + luck.toFixed(2)),
        h('span', ['Luck ', h('em', side.luckRating || luckLabel(luck))]),
      ],
    ),
    renderCount(side.luckyRolls, '+', 'Lucky rolls', playerIndex),
    renderCount(side.unluckyRolls, '-', 'Unlucky rolls', playerIndex),
  ]);
}

export function render(ctrl: AnalyseCtrl): VNode | undefined {
  if (ctrl.studyPractice || ctrl.embed) return;
  if (!isBackgammonVariant(ctrl.data.game.variant.key)) return;
  if (!ctrl.bgAnalysis) return h('div.analyse__acpl');
  return h(
    'div.analyse__acpl',
    h('div.advice-summary', [renderSide(ctrl, 'p1', ctrl.bgAnalysis.p1), renderSide(ctrl, 'p2', ctrl.bgAnalysis.p2)]),
  );
}
