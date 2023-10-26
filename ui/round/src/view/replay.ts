import * as game from 'game';
import * as round from '../round';
import * as status from 'game/status';
import * as util from '../util';
import isCol1 from 'common/isCol1';
import RoundController from '../ctrl';
import throttle from 'common/throttle';
import viewStatus from 'game/view/status';
import { game as gameRoute } from 'game/router';
import { h, VNode } from 'snabbdom';
import { Step, MaybeVNodes, RoundData } from '../interfaces';
import { NotationStyle, notationStyle } from 'stratutils';
import { moveFromNotationStyle } from 'common/notation';

const scrollMax = 99999,
  moveTag = 'u8t',
  indexTag = 'i5z',
  indexTagUC = indexTag.toUpperCase(),
  movesTag = 'l4x',
  rmovesTag = 'rm6';

const autoScroll = throttle(100, (movesEl: HTMLElement, ctrl: RoundController) =>
  window.requestAnimationFrame(() => {
    if (ctrl.data.steps.length < 7) return;
    let st: number | undefined;
    if (ctrl.ply < 3) st = 0;
    else if (ctrl.ply == round.lastPly(ctrl.data)) st = scrollMax;
    else {
      const plyEl = movesEl.querySelector('.a1t') as HTMLElement | undefined;
      if (plyEl)
        st = isCol1()
          ? plyEl.offsetLeft - movesEl.offsetWidth / 2 + plyEl.offsetWidth / 2
          : plyEl.offsetTop - movesEl.offsetHeight / 2 + plyEl.offsetHeight / 2;
    }
    if (typeof st == 'number') {
      if (st == scrollMax) movesEl.scrollLeft = movesEl.scrollTop = st;
      else if (isCol1()) movesEl.scrollLeft = st;
      else movesEl.scrollTop = st;
    }
  })
);

const renderDrawOffer = () =>
  h(
    'draw',
    {
      attrs: {
        title: 'Draw offer',
      },
    },
    '½?'
  );

function renderMove(
  step: Step | null,
  notation: NotationStyle,
  variant: Variant,
  prevFen: string,
  curPly: number,
  orEmpty: boolean,
  drawOffers: Set<number>
) {
  return step
    ? h(
        moveTag,
        {
          class: {
            a1t: step.ply === curPly,
          },
        },
        [
          moveFromNotationStyle(notation)({ san: step.san, uci: step.uci, fen: step.fen, prevFen: prevFen }, variant),
          drawOffers.has(step.ply) ? renderDrawOffer() : undefined,
        ]
      )
    : orEmpty
    ? h(moveTag, '…')
    : undefined;
}

function renderMultiActionMove(
  step: (Step | null)[],
  notation: NotationStyle,
  variant: Variant,
  prevFen: string, //not used for amazons move notation therefore doesn't need to be the exact one
  curPly: number,
  orEmpty: boolean,
  drawOffers: Set<number>
) {
  return step
    ? h(
        moveTag,
        {
          class: {
            a1t: step[0]
              ? step[1]
                ? step[0].ply === curPly || step[1].ply === curPly
                : step[0].ply === curPly
              : false,
          },
        },
        [
          step
            .map(s =>
              s
                ? moveFromNotationStyle(notation)({ san: s.san, uci: s.uci, fen: s.fen, prevFen: prevFen }, variant)
                : ''
            )
            .join(' '),
          drawOffers.has(step[0] ? step[0].ply : -1) || drawOffers.has(step[1] ? step[1].ply : -1)
            ? renderDrawOffer()
            : undefined,
        ]
      )
    : orEmpty
    ? h(moveTag, '…')
    : undefined;
}

export function renderResult(ctrl: RoundController): VNode | undefined {
  let result: string | undefined;
  if (status.finished(ctrl.data))
    switch (ctrl.data.game.winner) {
      case 'p1':
        result = '1-0';
        break;
      case 'p2':
        result = '0-1';
        break;
      default:
        result = '½-½';
    }
  if (result || status.aborted(ctrl.data)) {
    const winner = ctrl.data.game.winnerPlayer;
    return h('div.result-wrap', [
      h('p.result', result || ''),
      h(
        'p.status',
        {
          hook: util.onInsert(() => {
            if (ctrl.autoScroll) ctrl.autoScroll();
            else setTimeout(() => ctrl.autoScroll(), 200);
          }),
        },
        [viewStatus(ctrl), winner ? ' • ' + ctrl.trans('playerIndexIsVictorious', winner) : '']
      ),
    ]);
  }
  return;
}

function renderMoves(ctrl: RoundController): MaybeVNodes {
  const steps = ctrl.data.steps,
    notation = notationStyle(ctrl.data.game.variant.key),
    variant = ctrl.data.game.variant,
    firstPly = round.firstPly(ctrl.data),
    lastPly = round.lastPly(ctrl.data),
    drawPlies = new Set(ctrl.data.game.drawOffers || []),
    initialFen = ctrl.data.game.initialFen || '';
  if (typeof lastPly === 'undefined') return [];

  const pairs: Array<Array<Step | null>> = [];
  let startAt = 1;
  if (firstPly % 2 === 1) {
    pairs.push([null, steps[1]]);
    startAt = 2;
  }
  for (let i = startAt; i < steps.length; i += 2) pairs.push([steps[i], steps[i + 1]]);

  const prevFen = (i: number, pairI: number) => {
    if (i === 0 && pairI === 0) {
      return initialFen;
    }
    const step = pairI === 1 ? pairs[i][0] : pairs[i - 1][1];
    return step ? step.fen : initialFen;
  };

  const els: MaybeVNodes = [],
    curPly = ctrl.ply;
  //TODO multiaction generalise for all games? fix with monsterchess?
  if (variant.key === 'amazons') {
    for (let i = 0; i < pairs.length; i = i + 2) {
      els.push(h(indexTag, Math.floor(i / 2) + 1 + ''));
      els.push(renderMultiActionMove(pairs[i], notation, variant, prevFen(i, 0), curPly, true, drawPlies));
      els.push(renderMultiActionMove(pairs[i + 1], notation, variant, prevFen(i + 1, 0), curPly, false, drawPlies));
    }
  } else {
    for (let i = 0; i < pairs.length; i++) {
      els.push(h(indexTag, i + 1 + ''));
      els.push(renderMove(pairs[i][0], notation, variant, prevFen(i, 0), curPly, true, drawPlies));
      els.push(renderMove(pairs[i][1], notation, variant, prevFen(i, 1), curPly, false, drawPlies));
    }
  }
  els.push(renderResult(ctrl));

  return els;
}

export function analysisButton(ctrl: RoundController): VNode | undefined {
  const forecastCount = ctrl.data.forecastCount;
  return game.userAnalysable(ctrl.data) && util.allowAnalysisForVariant(ctrl.data.game.variant.key)
    ? h(
        'a.fbt.analysis',
        {
          class: {
            text: !!forecastCount,
          },
          attrs: {
            title: ctrl.noarg('analysis'),
            href: gameRoute(ctrl.data, ctrl.data.player.playerIndex) + '/analysis#' + ctrl.turnCount,
            'data-icon': 'A',
          },
        },
        forecastCount ? ['' + forecastCount] : []
      )
    : undefined;
}

function renderButtons(ctrl: RoundController) {
  const d = ctrl.data,
    firstPly = round.firstPly(d),
    lastPly = round.lastPly(d);
  return h(
    'div.buttons',
    {
      hook: util.bind(
        'mousedown',
        e => {
          const target = e.target as HTMLElement;
          const ply = parseInt(target.getAttribute('data-ply') || '');
          if (!isNaN(ply)) ctrl.userJump(ply);
          else {
            const action =
              target.getAttribute('data-act') || (target.parentNode as HTMLElement).getAttribute('data-act');
            if (action === 'flip') {
              if (d.tv) location.href = '/tv/' + d.tv.channel + (d.tv.flip ? '' : '?flip=1');
              else if (d.player.spectator) location.href = gameRoute(d, d.opponent.playerIndex);
              else ctrl.flipNow();
            }
          }
        },
        ctrl.redraw
      ),
    },
    [
      h('button.fbt.flip', {
        class: { active: ctrl.flip },
        attrs: {
          title: ctrl.noarg('flipBoard'),
          'data-act': 'flip',
          'data-icon': 'B',
        },
      }),
      ...[
        ['W', firstPly],
        ['Y', ctrl.ply - 1],
        ['X', ctrl.ply + 1],
        ['V', lastPly],
      ].map((b, i) => {
        const enabled = ctrl.ply !== b[1] && (b[1] as number) >= firstPly && (b[1] as number) <= lastPly;
        return h('button.fbt', {
          class: { glowing: i === 3 && ctrl.isLate() },
          attrs: {
            disabled: !enabled,
            'data-icon': b[0],
            'data-ply': enabled ? b[1] : '-',
          },
        });
      }),
      analysisButton(ctrl) || h('div.noop'),
    ]
  );
}

function initMessage(d: RoundData, trans: Trans) {
  return game.playable(d) && d.game.turns === 0 && !d.player.spectator
    ? h('div.message', util.justIcon(''), [
        h('div', [
          trans('youPlayThePlayerIndexPieces', d.player.playerName),
          ...(d.player.playerIndex === 'p1' ? [h('br'), h('strong', trans.noarg('itsYourTurn'))] : []),
        ]),
      ])
    : null;
}

function col1Button(ctrl: RoundController, dir: number, icon: string, disabled: boolean) {
  return disabled
    ? null
    : h('button.fbt', {
        attrs: {
          disabled: disabled,
          'data-icon': icon,
          'data-ply': ctrl.ply + dir,
        },
        hook: util.bind('mousedown', e => {
          e.preventDefault();
          ctrl.userJump(ctrl.ply + dir);
          ctrl.redraw();
        }),
      });
}

export function render(ctrl: RoundController): VNode | undefined {
  const d = ctrl.data,
    moves =
      ctrl.replayEnabledByPref() &&
      h(
        movesTag,
        {
          hook: util.onInsert(el => {
            el.addEventListener('mousedown', e => {
              let node = e.target as HTMLElement,
                offset = -2;
              if (node.tagName !== moveTag.toUpperCase()) return;
              while ((node = node.previousSibling as HTMLElement)) {
                offset++;
                if (node.tagName === indexTagUC) {
                  //TODO multiaction fix for monsterchess (create jump to turn instead?)
                  ctrl.userJump(
                    (d.game.variant.key === 'amazons' ? 4 : 2) * parseInt(node.textContent || '') +
                      offset * (d.game.variant.key === 'amazons' ? 2 : 1)
                  );
                  ctrl.redraw();
                  break;
                }
              }
            });
            ctrl.autoScroll = () => autoScroll(el, ctrl);
            ctrl.autoScroll();
          }),
        },
        renderMoves(ctrl)
      );
  return ctrl.nvui
    ? undefined
    : h(rmovesTag, [
        renderButtons(ctrl),
        initMessage(d, ctrl.trans) ||
          (moves
            ? isCol1()
              ? h('div.col1-moves', [
                  col1Button(ctrl, -1, 'Y', ctrl.ply == round.firstPly(d)),
                  moves,
                  col1Button(ctrl, 1, 'X', ctrl.ply == round.lastPly(d)),
                ])
              : moves
            : renderResult(ctrl)),
      ]);
}
