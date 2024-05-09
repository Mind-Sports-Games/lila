import { h, VNode } from 'snabbdom';
import { parseFen } from 'stratops/fen';
import { variantToRules } from 'stratutils';
import * as chessground from './ground';
import { read as fenRead } from 'chessground/fen';
import {
  bind,
  onInsert,
  dataIcon,
  spinner,
  bindMobileMousedown,
  getPlayerScore,
  getMancalaScore,
  getGoScore,
  allowClientEvalForVariant,
} from './util';
import { defined } from 'common';
import changeColorHandle from 'common/coordsColor';
import { playable } from 'game';
import * as router from 'game/router';
import statusView from 'game/view/status';
import { path as treePath } from 'tree';
import { render as renderTreeView } from './treeView/treeView';
import * as control from './control';
import { view as actionMenu } from './actionMenu';
import { view as renderPromotion } from './promotion';
import renderClocks from './clocks';
import * as pgnExport from './pgnExport';
import forecastView from './forecast/forecastView';
import { view as cevalView } from 'ceval';
import crazyView from './crazy/crazyView';
import { view as keyboardView } from './keyboard';
import explorerView from './explorer/explorerView';
import retroView from './retrospect/retroView';
import practiceView from './practice/practiceView';
import * as gbEdit from './study/gamebook/gamebookEdit';
import * as gbPlay from './study/gamebook/gamebookPlayView';
import { StudyCtrl } from './study/interfaces';
import * as studyView from './study/studyView';
import * as studyPracticeView from './study/practice/studyPracticeView';
import { view as forkView } from './fork';
import { render as acplView } from './acpl';
import AnalyseCtrl from './ctrl';
import { ConcealOf, Position } from './interfaces';
import relayManager from './study/relay/relayManagerView';
import relayTour from './study/relay/relayTourView';
import renderPlayerBars from './study/playerBars';
import { findTag } from './study/studyChapters';
import serverSideUnderboard from './serverSideUnderboard';
import * as gridHacks from './gridHacks';

function renderResult(ctrl: AnalyseCtrl): VNode[] {
  let result: string | undefined;
  if (ctrl.data.game.status.id >= 30)
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
  const tags: VNode[] = [];
  if (result) {
    tags.push(h('div.result', result));
    const winner = ctrl.data.game.winnerPlayer;
    tags.push(
      h('div.status', [statusView(ctrl), winner ? ', ' + ctrl.trans('playerIndexIsVictorious', winner) : null])
    );
  }
  return tags;
}

function makeConcealOf(ctrl: AnalyseCtrl): ConcealOf | undefined {
  const conceal =
    ctrl.study && ctrl.study.data.chapter.conceal !== undefined
      ? {
          owner: ctrl.study.isChapterOwner(),
          ply: ctrl.study.data.chapter.conceal,
        }
      : null;
  if (conceal)
    return (isMainline: boolean) => (path: Tree.Path, node: Tree.Node) => {
      if (!conceal || (isMainline && conceal.ply >= node.ply)) return null;
      if (treePath.contains(ctrl.path, path)) return null;
      return conceal.owner ? 'conceal' : 'hide';
    };
  return undefined;
}

function renderAnalyse(ctrl: AnalyseCtrl, concealOf?: ConcealOf) {
  return h(
    'div.analyse__moves.areplay',
    [
      ctrl.embed && ctrl.study ? h('div.chapter-name', ctrl.study.currentChapter().name) : null,
      renderTreeView(ctrl, concealOf),
    ].concat(renderResult(ctrl))
  );
}

function wheel(ctrl: AnalyseCtrl, e: WheelEvent) {
  const target = e.target as HTMLElement;
  if (target.tagName !== 'PIECE' && target.tagName !== 'SQUARE' && target.tagName !== 'CG-BOARD') return;
  e.preventDefault();
  if (e.deltaY > 0) control.next(ctrl);
  else if (e.deltaY < 0) control.prev(ctrl);
  ctrl.redraw();
  return false;
}

function inputs(ctrl: AnalyseCtrl): VNode | undefined {
  if (ctrl.ongoing || !ctrl.data.userAnalysis) return;
  if (ctrl.redirecting) return spinner();
  return h('div.copyables', [
    h('div.pair', [
      h('label.name', 'FEN'),
      h('input.copyable.autoselect.analyse__underboard__fen', {
        attrs: { spellCheck: false },
        hook: {
          insert: vnode => {
            const el = vnode.elm as HTMLInputElement;
            el.value = defined(ctrl.fenInput) ? ctrl.fenInput : ctrl.node.fen;
            el.addEventListener('change', _ => {
              if (el.value !== ctrl.node.fen && el.reportValidity()) ctrl.changeFen(el.value.trim());
            });
            el.addEventListener('input', _ => {
              ctrl.fenInput = el.value;
              el.setCustomValidity(
                parseFen(variantToRules(ctrl.data.game.variant.key))(el.value.trim()).isOk ? '' : 'Invalid FEN'
              );
            });
          },
          postpatch: (_, vnode) => {
            const el = vnode.elm as HTMLInputElement;
            if (!defined(ctrl.fenInput)) {
              el.value = ctrl.node.fen;
              el.setCustomValidity('');
            } else if (el.value != ctrl.fenInput) el.value = ctrl.fenInput;
          },
        },
      }),
    ]),
    h('div.pgn', [
      h('div.pair', [
        h('label.name', 'PGN'),
        h('textarea.copyable.autoselect', {
          attrs: { spellCheck: false },
          hook: {
            ...onInsert(el => {
              (el as HTMLTextAreaElement).value = defined(ctrl.pgnInput)
                ? ctrl.pgnInput
                : pgnExport.renderFullTxt(ctrl);
              el.addEventListener('input', e => (ctrl.pgnInput = (e.target as HTMLTextAreaElement).value));
            }),
            postpatch: (_, vnode) => {
              (vnode.elm as HTMLTextAreaElement).value = defined(ctrl.pgnInput)
                ? ctrl.pgnInput
                : pgnExport.renderFullTxt(ctrl);
            },
          },
        }),
        h(
          'button.button.button-thin.action.text',
          {
            attrs: dataIcon('G'),
            hook: bind(
              'click',
              _ => {
                const pgn = $('.copyables .pgn textarea').val() as string;
                if (pgn !== pgnExport.renderFullTxt(ctrl)) ctrl.changePgn(pgn);
              },
              ctrl.redraw
            ),
          },
          ctrl.trans.noarg('importPgn')
        ),
      ]),
    ]),
  ]);
}

function jumpButton(icon: string, effect: string, enabled: boolean): VNode {
  return h('button.fbt', {
    class: { disabled: !enabled },
    attrs: { 'data-act': effect, 'data-icon': icon },
  });
}

function dataAct(e: Event): string | null {
  const target = e.target as HTMLElement;
  return target.getAttribute('data-act') || (target.parentNode as HTMLElement).getAttribute('data-act');
}

function repeater(ctrl: AnalyseCtrl, action: 'prev' | 'next', e: Event) {
  const repeat = function () {
    control[action](ctrl);
    ctrl.redraw();
    delay = Math.max(100, delay - delay / 15);
    timeout = setTimeout(repeat, delay);
  };
  let delay = 350;
  let timeout = setTimeout(repeat, 500);
  control[action](ctrl);
  const eventName = e.type == 'touchstart' ? 'touchend' : 'mouseup';
  document.addEventListener(eventName, () => clearTimeout(timeout), { once: true });
}

function controls(ctrl: AnalyseCtrl) {
  const canJumpPrev = ctrl.path !== '',
    canJumpNext = !!ctrl.node.children[0],
    menuIsOpen = ctrl.actionMenu.open,
    noarg = ctrl.trans.noarg;
  return h(
    'div.analyse__controls.analyse-controls',
    {
      hook: onInsert(el => {
        bindMobileMousedown(
          el,
          e => {
            const action = dataAct(e);
            if (action === 'prev' || action === 'next') repeater(ctrl, action, e);
            else if (action === 'first') control.first(ctrl);
            else if (action === 'last') control.last(ctrl);
            else if (action === 'explorer') ctrl.toggleExplorer();
            else if (action === 'practice') ctrl.togglePractice();
            else if (action === 'menu') ctrl.actionMenu.toggle();
          },
          ctrl.redraw
        );
      }),
    },
    [
      ctrl.embed
        ? null
        : h(
            'div.features',
            ctrl.studyPractice
              ? [
                  h('a.fbt', {
                    attrs: {
                      title: noarg('analysis'),
                      target: '_blank',
                      rel: 'noopener',
                      href: ctrl.studyPractice.analysisUrl(),
                      'data-icon': 'A',
                    },
                  }),
                ]
              : [
                  ctrl.ceval.allowed() && allowClientEvalForVariant(ctrl.ceval.variant.key)
                    ? h('button.fbt', {
                        attrs: {
                          title: noarg('openingExplorerAndTablebase'),
                          'data-act': 'explorer',
                          'data-icon': ']',
                        },
                        class: {
                          hidden: menuIsOpen || !ctrl.explorer.allowed() || !!ctrl.retro,
                          active: ctrl.explorer.enabled(),
                        },
                      })
                    : null,
                  ctrl.ceval.possible &&
                  ctrl.ceval.allowed() &&
                  allowClientEvalForVariant(ctrl.ceval.variant.key) &&
                  !ctrl.isGamebook()
                    ? h('button.fbt', {
                        attrs: {
                          title: noarg('practiceWithComputer'),
                          'data-act': 'practice',
                          'data-icon': '',
                        },
                        class: {
                          hidden: menuIsOpen || !!ctrl.retro,
                          active: !!ctrl.practice,
                        },
                      })
                    : null,
                ]
          ),
      h('div.jumps', [
        jumpButton('W', 'first', canJumpPrev),
        jumpButton('Y', 'prev', canJumpPrev),
        jumpButton('X', 'next', canJumpNext),
        jumpButton('V', 'last', canJumpNext),
      ]),
      ctrl.studyPractice
        ? h('div.noop')
        : h('button.fbt', {
            class: { active: menuIsOpen },
            attrs: {
              title: noarg('menu'),
              'data-act': 'menu',
              'data-icon': '[',
            },
          }),
    ]
  );
}

function forceInnerCoords(ctrl: AnalyseCtrl, v: boolean) {
  if (ctrl.data.pref.coords === Prefs.Coords.Outside) {
    $('body').toggleClass('coords-in', v).toggleClass('coords-out', !v);
    changeColorHandle();
  }
}

function addChapterId(study: StudyCtrl | undefined, cssClass: string) {
  return cssClass + (study && study.data.chapter ? '.' + study.data.chapter.id : '');
}

function renderPlayerScore(
  score: number,
  position: Position,
  playerIndex: string,
  variantKey: VariantKey
): VNode | undefined {
  const defaultMancalaRole = 's';
  const children: VNode[] = [];
  if (variantKey === 'togyzkumalak') {
    let part1Score = 0;
    let part2Score = 0;
    let part2Offset = false;
    if (score <= 10) {
      part1Score = score;
      part2Score = 0;
    } else if (score <= 20) {
      part1Score = 10;
      part2Score = score - 10;
    } else {
      part1Score = Math.min((score % 20) + 10, 20);
      part2Score = Math.max(score % 20, 10);
      if (part2Score === 10) part2Offset = true;
    }

    const pieceClassPart1 = `piece.${defaultMancalaRole}${part1Score.toString()}-piece.part1.`;
    const pieceClassPart2 = `piece.${defaultMancalaRole}${part2Score.toString()}${part2Offset ? 'o' : ''}-piece.part2.`;

    children.push(h(pieceClassPart1 + playerIndex));
    if (score > 10) {
      children.push(h(pieceClassPart2 + playerIndex));
    }
    return h('div.game-score.game-score-' + position, { attrs: { 'data-score': score } }, children);
  } else if (variantKey === 'go9x9' || variantKey === 'go13x13' || variantKey === 'go19x19') {
    children.push(h('piece.p-piece.' + playerIndex, { attrs: { 'data-score': score } }));
    return h('div.game-score.game-score-top' + '.' + playerIndex, children);
  } else {
    const pieceClass =
      variantKey === 'oware' ? `piece.${defaultMancalaRole}${score.toString()}-piece.` : 'piece.p-piece.';
    children.push(h(pieceClass + playerIndex, { attrs: { 'data-score': score } }));
    return h('div.game-score.game-score-top' + '.' + playerIndex, children);
  }
}

function renderPlayerScoreNames(ctrl: AnalyseCtrl): VNode | undefined {
  const children: VNode[] = [];
  const study = ctrl.study;
  let playerNames = { p1: 'Player 1', p2: 'player 2' };
  if (study !== undefined) {
    const tags = study.data.chapter.tags;
    playerNames = {
      p1: findTag(tags, 'p1') ?? ctrl.data.player.playerName,
      p2: findTag(tags, 'p2') ?? ctrl.data.opponent.playerName,
    };
  } else {
    playerNames = {
      p1: ctrl.data.player.user ? ctrl.data.player.user.id : ctrl.data.player.playerName,
      p2: ctrl.data.opponent.user ? ctrl.data.opponent.user.id : ctrl.data.opponent.playerName,
    };
  }

  children.push(h('div.game-score-name.p1.text', playerNames.p1));
  children.push(h('div.game-score-name.vs.text', 'vs'));
  children.push(h('div.game-score-name.p2.text', playerNames.p2));
  return h('div.game-score-names', children);
}

export default function (ctrl: AnalyseCtrl): VNode {
  if (ctrl.nvui) return ctrl.nvui.render(ctrl);
  const concealOf = makeConcealOf(ctrl),
    study = ctrl.study,
    showCevalPvs = !(ctrl.retro && ctrl.retro.isSolving()) && !ctrl.practice,
    menuIsOpen = ctrl.actionMenu.open,
    gamebookPlay = ctrl.gamebookPlay(),
    gamebookPlayView = gamebookPlay && gbPlay.render(gamebookPlay),
    gamebookEditView = gbEdit.running(ctrl) ? gbEdit.render(ctrl) : undefined,
    playerBars = renderPlayerBars(ctrl),
    clocks = !playerBars && renderClocks(ctrl),
    gaugeOn = ctrl.showEvalGauge(),
    needsInnerCoords = !!gaugeOn || !!playerBars,
    tour = relayTour(ctrl),
    variantKey = ctrl.data.game.variant.key;

  let topScore = 0,
    bottomScore = 0;
  const cgState = ctrl.chessground && ctrl.chessground.state;
  if (ctrl.data.hasGameScore) {
    switch (variantKey) {
      case 'flipello10':
      case 'flipello': {
        const pieces = cgState ? cgState.pieces : fenRead(ctrl.node.fen, ctrl.data.game.variant.boardSize, variantKey);
        const p1Score = getPlayerScore(variantKey, pieces, 'p1');
        const p2Score = getPlayerScore(variantKey, pieces, 'p2');
        topScore = ctrl.topPlayerIndex() === 'p1' ? p1Score : p2Score;
        bottomScore = ctrl.topPlayerIndex() === 'p2' ? p1Score : p2Score;
        break;
      }
      case 'oware':
      case 'togyzkumalak': {
        const fen = ctrl.node.fen;
        const p1Score = getMancalaScore(fen, 'p1');
        const p2Score = getMancalaScore(fen, 'p2');
        topScore = ctrl.topPlayerIndex() === 'p1' ? p1Score : p2Score;
        bottomScore = ctrl.topPlayerIndex() === 'p2' ? p1Score : p2Score;
        break;
      }
      case 'go9x9':
      case 'go13x13':
      case 'go19x19': {
        const fen = ctrl.node.fen;
        const p1Score = getGoScore(fen, 'p1');
        const p2Score = getGoScore(fen, 'p2');
        topScore = ctrl.topPlayerIndex() === 'p1' ? p1Score : p2Score;
        bottomScore = ctrl.topPlayerIndex() === 'p2' ? p1Score : p2Score;
        break;
      }
      default: {
        break;
      }
    }
  }
  // fix coordinates for non-chess games to display them outside due to not working well displaying on board
  if (
    [
      'xiangqi',
      'shogi',
      'minixiangqi',
      'minishogi',
      'flipello',
      'flipello10',
      'oware',
      'go9x9',
      'go13x13',
      'go19x19',
    ].includes(variantKey)
  ) {
    if (!$('body').hasClass('coords-no')) {
      $('body').removeClass('coords-in').addClass('coords-out');
    }
  }
  //Togyzkumalak and backgammon board always has coodinates on the inside
  if (['togyzkumalak', 'backgammon', 'nackgammon'].includes(variantKey)) {
    if (!$('body').hasClass('coords-no')) {
      $('body').removeClass('coords-out').addClass('coords-in');
    }
  }

  //Add piece-letter class for games which dont want Noto Chess (font-famliy)
  const notationBasic = [
    'xiangqi',
    'shogi',
    'minixiangqi',
    'minishogi',
    'oware',
    'togyzkumalak',
    'go9x9',
    'go13x13',
    'go19x19',
    'backgammon',
    'nackgammon',
  ].includes(variantKey)
    ? '.piece-letter'
    : '';
  return h(
    `main.analyse.variant-${variantKey}${notationBasic}.${ctrl.data.game.gameFamily}`,
    {
      hook: {
        insert: vn => {
          playstrategy.miniGame.initAll();
          forceInnerCoords(ctrl, needsInnerCoords);
          if (!!playerBars != $('body').hasClass('header-margin')) {
            requestAnimationFrame(() => {
              $('body').toggleClass('header-margin', !!playerBars);
              ctrl.redraw();
            });
          }
          gridHacks.start(vn.elm as HTMLElement);
        },
        update(_, _2) {
          forceInnerCoords(ctrl, needsInnerCoords);
        },
        postpatch(old, vnode) {
          if (old.data!.gaugeOn !== gaugeOn) document.body.dispatchEvent(new Event('chessground.resize'));
          vnode.data!.gaugeOn = gaugeOn;
          playstrategy.miniGame.initAll();
        },
      },
      class: {
        'comp-off': !ctrl.showComputer(),
        'gauge-on': gaugeOn,
        'has-players': !!playerBars,
        'has-clocks': !!clocks,
        'has-relay-tour': !!tour,
        'analyse-hunter': ctrl.opts.hunter,
      },
    },
    [
      ctrl.keyboardHelp ? keyboardView(ctrl) : null,
      study ? studyView.overboard(study) : null,
      tour ||
        h(
          addChapterId(study, 'div.analyse__board.main-board'),
          {
            hook:
              'ontouchstart' in window || ctrl.gamebookPlay()
                ? undefined
                : bind('wheel', (e: WheelEvent) => wheel(ctrl, e)),
          },
          [
            ...(clocks || []),
            playerBars ? playerBars[ctrl.bottomIsP1() ? 1 : 0] : null,
            chessground.render(ctrl),
            playerBars ? playerBars[ctrl.bottomIsP1() ? 0 : 1] : null,
            renderPromotion(ctrl),
          ]
        ),
      gaugeOn && !tour ? cevalView.renderGauge(ctrl) : null,
      menuIsOpen || tour || !ctrl.data.hasGameScore
        ? null
        : renderPlayerScore(topScore, 'top', ctrl.topPlayerIndex(), variantKey),
      menuIsOpen || tour || !ctrl.data.hasGameScore ? null : renderPlayerScoreNames(ctrl),
      menuIsOpen || tour ? null : crazyView(ctrl, ctrl.topPlayerIndex(), 'top'),
      gamebookPlayView ||
        (tour
          ? null
          : h(addChapterId(study, 'div.analyse__tools'), [
              ...(menuIsOpen
                ? [actionMenu(ctrl)]
                : [
                    allowClientEvalForVariant(ctrl.ceval.variant.key) ? cevalView.renderCeval(ctrl) : null,
                    allowClientEvalForVariant(ctrl.ceval.variant.key) && showCevalPvs
                      ? cevalView.renderPvs(variantKey)(ctrl)
                      : null,
                    renderAnalyse(ctrl, concealOf),
                    gamebookEditView || forkView(ctrl, concealOf),
                    retroView(ctrl) || practiceView(ctrl) || explorerView(ctrl),
                  ]),
            ])),
      menuIsOpen || tour || !ctrl.data.hasGameScore
        ? null
        : renderPlayerScore(bottomScore, 'bottom', ctrl.bottomPlayerIndex(), variantKey),
      menuIsOpen || tour ? null : crazyView(ctrl, ctrl.bottomPlayerIndex(), 'bottom'),
      gamebookPlayView || tour ? null : controls(ctrl),
      ctrl.embed || tour
        ? null
        : h(
            'div.analyse__underboard',
            {
              hook:
                ctrl.synthetic || playable(ctrl.data) ? undefined : onInsert(elm => serverSideUnderboard(elm, ctrl)),
            },
            study ? studyView.underboard(ctrl) : [inputs(ctrl)]
          ),
      tour ? null : acplView(ctrl),
      ctrl.embed
        ? null
        : ctrl.studyPractice
        ? studyPracticeView.side(study!)
        : h(
            'aside.analyse__side',
            {
              hook: onInsert(elm => {
                ctrl.opts.$side && ctrl.opts.$side.length && $(elm).replaceWith(ctrl.opts.$side);
                $(elm).append($('.context-streamers').clone().removeClass('none'));
              }),
            },
            ctrl.studyPractice
              ? [studyPracticeView.side(study!)]
              : study
              ? [studyView.side(study)]
              : [
                  ctrl.forecast ? forecastView(ctrl, ctrl.forecast) : null,
                  !ctrl.synthetic && playable(ctrl.data)
                    ? h(
                        'div.back-to-game',
                        h(
                          'a.button.button-empty.text',
                          {
                            attrs: {
                              href: router.game(ctrl.data, ctrl.data.player.playerIndex),
                              'data-icon': 'i',
                            },
                          },
                          ctrl.trans.noarg('backToGame')
                        )
                      )
                    : null,
                ]
          ),
      study && study.relay && relayManager(study.relay),
      ctrl.opts.chat &&
        h('section.mchat', {
          hook: onInsert(_ => {
            const chatOpts = ctrl.opts.chat;
            chatOpts.instance?.then(c => c.destroy());
            chatOpts.parseMoves = true;
            chatOpts.instance = playstrategy.makeChat(chatOpts);
          }),
        }),
      ctrl.embed
        ? null
        : h('div.chat__members.none', {
            hook: onInsert(playstrategy.watchers),
          }),
    ]
  );
}
