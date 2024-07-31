import { h, VNode } from 'snabbdom';
import { defined, prop, Prop } from 'common';
import { storedProp, StoredProp } from 'common/storage';
import * as xhr from 'common/xhr';
import { allowAnalysisForVariant, isChess } from 'common/analysis';
import { bind, bindSubmit, spinner, option, onInsert } from '../util';
import { variants as xhrVariants, importPgn } from './studyXhr';
import * as modal from '../modal';
import { chapter as chapterTour } from './studyTour';
import { ChapterData, ChapterMode, Orientation, StudyChapterMeta } from './interfaces';
import { Redraw } from '../interfaces';
import AnalyseCtrl from '../ctrl';
import { StudySocketSend } from '../socket';

export const modeChoices = [
  ['normal', 'normalAnalysis'],
  ['practice', 'practiceWithComputer'],
  ['conceal', 'hideNextMoves'],
  ['gamebook', 'interactiveLesson'],
];

export const nonBrowserAnalysisModeChoices = [['normal', 'normalAnalysis']];

export const fieldValue = (e: Event, id: string) =>
  ((e.target as HTMLElement).querySelector('#chapter-' + id) as HTMLInputElement)?.value;

export interface StudyChapterNewFormCtrl {
  root: AnalyseCtrl;
  vm: {
    variants: Variant[];
    open: boolean;
    initial: Prop<boolean>;
    tab: StoredProp<string>;
    editor: PlayStrategyEditor | null;
    editorFen: Prop<Fen | null>;
    variantKey: Prop<VariantKey | null>;
    isDefaultName: boolean;
  };
  open(): void;
  openInitial(): void;
  close(): void;
  toggle(): void;
  submit(d: Omit<ChapterData, 'initial'>): void;
  chapters: Prop<StudyChapterMeta[]>;
  startTour(): void;
  multiPgnMax: number;
  redraw: Redraw;
}

export function ctrl(
  send: StudySocketSend,
  chapters: Prop<StudyChapterMeta[]>,
  setTab: () => void,
  root: AnalyseCtrl,
): StudyChapterNewFormCtrl {
  const multiPgnMax = 20;

  const vm = {
    variants: [],
    open: false,
    initial: prop(false),
    tab: storedProp('study.form.tab', 'init'),
    editor: null,
    editorFen: prop(null),
    variantKey: prop(null),
    isDefaultName: true,
  };

  function loadVariants() {
    if (!vm.variants.length)
      xhrVariants().then(function (vs) {
        vm.variants = vs;
        root.redraw();
      });
  }

  function open() {
    vm.open = true;
    loadVariants();
    vm.initial(false);
  }
  function close() {
    vm.open = false;
  }

  return {
    vm,
    open,
    root,
    openInitial() {
      open();
      vm.initial(true);
    },
    close,
    toggle() {
      if (vm.open) close();
      else open();
    },
    submit(d) {
      const study = root.study!;
      const dd = {
        ...d,
        sticky: study.vm.mode.sticky,
        initial: vm.initial(),
      };
      if (!dd.pgn) send('addChapter', dd);
      else importPgn(study.data.id, dd);
      close();
      setTab();
    },
    chapters,
    startTour: () =>
      chapterTour(tab => {
        vm.tab(tab);
        root.redraw();
      }),
    multiPgnMax,
    redraw: root.redraw,
  };
}

function edittab(ctrl: StudyChapterNewFormCtrl): VNode {
  const currentChapter = ctrl.root.study!.data.chapter;
  return h(
    'div.board-editor-wrap',
    {
      hook: {
        insert(vnode) {
          Promise.all([
            playstrategy.loadModule('editor'),
            xhr.json(xhr.url('/editor.json', { fen: ctrl.root.node.fen })),
          ]).then(([_, data]) => {
            data.embed = true;
            data.options = {
              inlineCastling: true,
              orientation: currentChapter.setup.orientation,
              onChange: ctrl.vm.editorFen,
            };
            ctrl.vm.editor = window.PlayStrategyEditor!(vnode.elm as HTMLElement, data);
            ctrl.vm.editorFen(ctrl.vm.editor.getFen());
          });
        },
        destroy: _ => {
          ctrl.vm.editor = null;
        },
      },
    },
    [spinner()],
  );
}
function gametab(ctrl: StudyChapterNewFormCtrl): VNode {
  const trans = ctrl.root.trans;
  const noarg = trans.noarg;
  return h('div.form-group', [
    h(
      'label.form-label',
      {
        attrs: { for: 'chapter-game' },
      },
      trans('loadAGameFromX', 'playstrategy.org'),
    ),
    h('textarea#chapter-game.form-control', {
      attrs: { placeholder: noarg('urlOfTheGame') },
    }),
  ]);
}
function fentab(ctrl: StudyChapterNewFormCtrl): VNode {
  const trans = ctrl.root.trans;
  const noarg = trans.noarg;
  return h('div.form-group', [
    h('input#chapter-fen.form-control', {
      attrs: {
        value: '',
        placeholder: noarg('loadAPositionFromFen'),
      },
    }),
  ]);
}

function pgntab(ctrl: StudyChapterNewFormCtrl): VNode {
  const trans = ctrl.root.trans;
  return h('div.form-groupabel', [
    h('textarea#chapter-pgn.form-control', {
      attrs: { placeholder: trans.plural('pasteYourPgnTextHereUpToNbGames', ctrl.multiPgnMax) },
    }),
    window.FileReader
      ? h('input#chapter-pgn-file.form-control', {
          attrs: {
            type: 'file',
            accept: '.pgn',
          },
          hook: bind('change', e => {
            const file = (e.target as HTMLInputElement).files![0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = function () {
              (document.getElementById('chapter-pgn') as HTMLTextAreaElement).value = reader.result as string;
            };
            reader.readAsText(file);
          }),
        })
      : null,
  ]);
}

export function view(ctrl: StudyChapterNewFormCtrl): VNode {
  const trans = ctrl.root.trans;
  const activeTab = ctrl.vm.tab();
  const makeTab = function (key: string, name: string, title: string) {
    return h(
      'span.' + key,
      {
        class: { active: activeTab === key },
        attrs: { title },
        hook: bind('click', () => ctrl.vm.tab(key), ctrl.root.redraw),
      },
      name,
    );
  };
  const gameOrPgn = activeTab === 'game' || activeTab === 'pgn';
  const currentChapter = ctrl.root.study!.data.chapter;
  const mode = currentChapter.practice
    ? 'practice'
    : defined(currentChapter.conceal)
    ? 'conceal'
    : currentChapter.gamebook
    ? 'gamebook'
    : 'normal';
  const noarg = trans.noarg;
  const onlyForAnalysisVariants = (node: VNode | null): VNode | null =>
    allowAnalysisForVariant(ctrl.vm.variantKey() ?? 'standard') ? node : null;
  const onlyForChessVariants = (node: VNode | null): VNode | null =>
    isChess(ctrl.vm.variantKey() ?? 'standard') ? node : null;

  return modal.modal({
    class: 'chapter-new',
    onClose() {
      ctrl.close();
      ctrl.redraw();
    },
    noClickAway: true,
    content: [
      activeTab === 'edit'
        ? null
        : h('h2', [
            noarg('newChapter'),
            h('i.help', {
              attrs: { 'data-icon': '' },
              hook: bind('click', ctrl.startTour),
            }),
          ]),
      h(
        'form.form3',
        {
          hook: bindSubmit(e => {
            ctrl.submit({
              name: fieldValue(e, 'name'),
              game: fieldValue(e, 'game'),
              variant: fieldValue(e, 'variant') as VariantKey,
              pgn: fieldValue(e, 'pgn'),
              orientation: fieldValue(e, 'orientation') as Orientation,
              mode: fieldValue(e, 'mode') as ChapterMode,
              fen: fieldValue(e, 'fen') || (ctrl.vm.tab() === 'edit' ? ctrl.vm.editorFen() : null),
              isDefaultName: ctrl.vm.isDefaultName,
            });
          }, ctrl.redraw),
        },
        [
          h('div.form-group', [
            h(
              'label.form-label',
              {
                attrs: { for: 'chapter-name' },
              },
              noarg('name'),
            ),
            h('input#chapter-name.form-control', {
              attrs: {
                minlength: 2,
                maxlength: 80,
              },
              hook: onInsert<HTMLInputElement>(el => {
                if (!el.value) {
                  el.value = trans('chapterX', ctrl.vm.initial() ? 1 : ctrl.chapters().length + 1);
                  el.onchange = function () {
                    ctrl.vm.isDefaultName = false;
                  };
                  el.select();
                  el.focus();
                }
              }),
            }),
          ]),
          h('div.tabs-horiz', [
            makeTab('init', noarg('empty'), noarg('startFromInitialPosition')),
            onlyForChessVariants(makeTab('edit', noarg('editor'), noarg('startFromCustomPosition'))),
            onlyForAnalysisVariants(makeTab('game', 'URL', noarg('loadAGameByUrl'))),
            onlyForAnalysisVariants(makeTab('fen', 'FEN', noarg('loadAPositionFromFen'))),
            onlyForChessVariants(makeTab('pgn', 'PGN', noarg('loadAGameFromPgn'))),
          ]),
          onlyForChessVariants(activeTab === 'edit' ? edittab(ctrl) : null),
          onlyForAnalysisVariants(activeTab === 'game' ? gametab(ctrl) : null),
          onlyForAnalysisVariants(activeTab === 'fen' ? fentab(ctrl) : null),
          onlyForChessVariants(activeTab === 'pgn' ? pgntab(ctrl) : null),
          h('div.form-split', [
            h('div.form-group.form-half', [
              h(
                'label.form-label',
                {
                  attrs: { for: 'chapter-variant' },
                },
                noarg('Variant'),
              ),
              h(
                'select#chapter-variant.form-control',
                {
                  attrs: { disabled: gameOrPgn },
                  hook: {
                    init: () => ctrl.vm.variantKey(currentChapter.setup.variant.key),
                    ...bind('change', e => {
                      const select = e.target as HTMLInputElement;
                      ctrl.vm.variantKey(select.value as VariantKey);
                      ctrl.redraw();
                    }),
                  },
                },
                gameOrPgn
                  ? [h('option', noarg('automatic'))]
                  : ctrl.vm.variants
                      .filter(v => allowAnalysisForVariant(v.key))
                      .map(v => option(v.key, currentChapter.setup.variant.key, v.name)),
              ),
            ]),
            h('div.form-group.form-half', [
              h(
                'label.form-label',
                {
                  attrs: { for: 'chapter-orientation' },
                },
                noarg('orientation'),
              ),
              h(
                'select#chapter-orientation.form-control',
                {
                  hook: bind('change', e => {
                    ctrl.vm.editor &&
                      ctrl.vm.editor.setOrientation((e.target as HTMLInputElement).value as PlayerIndex);
                  }),
                },
                ['p1', 'p2'].map(function (playerIndex) {
                  return option(playerIndex, currentChapter.setup.orientation, noarg(playerIndex));
                }),
              ),
            ]),
          ]),
          h('div.form-group', [
            h(
              'label.form-label',
              {
                attrs: { for: 'chapter-mode' },
              },
              noarg('analysisMode'),
            ),
            h(
              'select#chapter-mode.form-control',
              (isChess(ctrl.vm.variantKey() ?? 'standard') ? modeChoices : nonBrowserAnalysisModeChoices).map(c =>
                option(c[0], mode, noarg(c[1])),
              ),
            ),
          ]),
          modal.button(noarg('createChapter')),
        ],
      ),
    ],
  });
}
