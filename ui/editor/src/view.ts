import { h, VNode } from 'snabbdom';
import { MouchEvent, NumberPair } from 'chessground/types';
import { dragNewPiece } from 'chessground/drag';
import { eventPosition, opposite } from 'chessground/util';
import { Rules } from 'stratops/types';
import { parseFen } from 'stratops/fen';
import EditorCtrl from './ctrl';
import chessground from './chessground';
import { Selected, CastlingToggle, EditorState } from './interfaces';

function castleCheckBox(ctrl: EditorCtrl, id: CastlingToggle, label: string, reversed: boolean): VNode {
  const input = h('input', {
    attrs: {
      type: 'checkbox',
      checked: ctrl.castlingToggles[id],
    },
    on: {
      change(e) {
        ctrl.setCastlingToggle(id, (e.target as HTMLInputElement).checked);
      },
    },
  });
  return h('label', reversed ? [input, label] : [label, input]);
}

function optgroup(name: string, opts: VNode[]): VNode {
  return h('optgroup', { attrs: { label: name } }, opts);
}

function studyButton(ctrl: EditorCtrl, state: EditorState): VNode {
  return h(
    'form',
    {
      attrs: {
        method: 'post',
        action: '/study/as',
      },
    },
    [
      h('input', { attrs: { type: 'hidden', name: 'orientation', value: ctrl.bottomPlayerIndex() } }),
      h('input', { attrs: { type: 'hidden', name: 'variant', value: ctrl.rules } }),
      h('input', { attrs: { type: 'hidden', name: 'fen', value: state.legalFen || '' } }),
      h(
        'button',
        {
          attrs: {
            type: 'submit',
            'data-icon': '4',
            disabled: !state.legalFen,
          },
          class: {
            button: true,
            'button-empty': true,
            text: true,
            disabled: !state.legalFen,
          },
        },
        ctrl.trans.noarg('toStudy'),
      ),
    ],
  );
}

function variant2option(key: Rules, name: string, ctrl: EditorCtrl): VNode {
  return h(
    'option',
    {
      attrs: {
        value: key,
        selected: key == ctrl.rules,
      },
    },
    `${ctrl.trans.noarg('variant')} | ${name}`,
  );
}

const allVariants: Array<[Rules, string]> = [
  ['chess', 'Standard'],
  ['antichess', 'Antichess'],
  ['atomic', 'Atomic'],
  ['crazyhouse', 'Crazyhouse'],
  ['horde', 'Horde'],
  ['kingofthehill', 'King of the Hill'],
  ['racingkings', 'Racing Kings'],
  ['3check', 'Three-check'],
  ['5check', 'Five-check'],
  ['monster', 'Monster'],
];

function controls(ctrl: EditorCtrl, state: EditorState): VNode {
  const position2option = function (pos: Editor.OpeningPosition): VNode {
    return h(
      'option',
      {
        attrs: {
          value: pos.epd || pos.fen,
          'data-fen': pos.fen,
        },
      },
      pos.eco ? `${pos.eco} ${pos.name}` : pos.name,
    );
  };
  const buttonStart = (icon?: string) =>
    h(
      `a.button.button-empty${icon ? '.text' : ''}`,
      { on: { click: ctrl.startPosition }, attrs: icon ? { 'data-icon': icon } : {} },
      ctrl.trans.noarg('startPosition'),
    );
  const buttonClear = (icon?: string) =>
    h(
      `a.button.button-empty${icon ? '.text' : ''}`,
      { on: { click: ctrl.clearBoard }, attrs: icon ? { 'data-icon': icon } : {} },
      ctrl.trans.noarg('clearBoard'),
    );
  return h('div.board-editor__tools', [
    ...(ctrl.cfg.embed || !ctrl.cfg.positions
      ? []
      : [
          h('div', [
            h(
              'select.positions',
              {
                props: {
                  value: state.fen.split(' ').slice(0, 4).join(' '),
                },
                on: {
                  change(e) {
                    const el = e.target as HTMLSelectElement;
                    let value = el.selectedOptions[0].getAttribute('data-fen');
                    if (value == 'prompt') value = (prompt('Paste FEN') || '').trim();
                    if (!value || !ctrl.setFen(value)) el.value = '';
                  },
                },
              },
              [
                optgroup(ctrl.trans.noarg('setTheBoard'), [
                  h(
                    'option',
                    {
                      attrs: {
                        selected: true,
                      },
                    },
                    `- ${ctrl.trans.noarg('boardEditor')}  -`,
                  ),
                  ...ctrl.extraPositions.map(position2option),
                ]),
                optgroup(ctrl.trans.noarg('popularOpenings'), ctrl.cfg.positions.map(position2option)),
              ],
            ),
          ]),
        ]),
    h('div.metadata', [
      h(
        'div.playerindex',
        h(
          'select',
          {
            on: {
              change(e) {
                ctrl.setTurn((e.target as HTMLSelectElement).value as PlayerIndex);
              },
            },
          },
          ['p1', 'p2'].map(function (key) {
            return h(
              'option',
              {
                attrs: {
                  value: key,
                  selected: ctrl.turn === key,
                },
              },
              ctrl.trans('playerIndexPlays', key == 'p1' ? 'White' : 'Black'),
            );
          }),
        ),
      ),
      h('div.castling', [
        h('strong', ctrl.trans.noarg('castling')),
        h('div', [
          castleCheckBox(ctrl, 'K', ctrl.trans.noarg('whiteCastlingKingside'), !!ctrl.options.inlineCastling),
          castleCheckBox(ctrl, 'Q', 'O-O-O', true),
        ]),
        h('div', [
          castleCheckBox(ctrl, 'k', ctrl.trans.noarg('blackCastlingKingside'), !!ctrl.options.inlineCastling),
          castleCheckBox(ctrl, 'q', 'O-O-O', true),
        ]),
      ]),
    ]),
    ...(ctrl.cfg.embed
      ? [h('div.actions', [buttonStart(), buttonClear()])]
      : [
          h('div', [
            h(
              'select',
              {
                attrs: { id: 'variants' },
                on: {
                  change(e) {
                    ctrl.setRules((e.target as HTMLSelectElement).value as Rules);
                  },
                },
              },
              allVariants.map(x => variant2option(x[0], x[1], ctrl)),
            ),
          ]),
          h('div.actions', [
            buttonStart('P'),
            buttonClear('q'),
            h(
              'a.button.button-empty.text',
              {
                attrs: { 'data-icon': 'B' },
                on: {
                  click() {
                    ctrl.chessground!.toggleOrientation();
                    ctrl.redraw();
                  },
                },
              },
              ctrl.trans.noarg('flipBoard'),
            ),
            h(
              'a',
              {
                attrs: {
                  'data-icon': 'A',
                  rel: 'nofollow',
                  ...(state.legalFen ? { href: ctrl.makeAnalysisUrl(state.legalFen) } : {}),
                },
                class: {
                  button: true,
                  'button-empty': true,
                  text: true,
                  disabled: !state.legalFen,
                },
              },
              ctrl.trans.noarg('analysis'),
            ),
            h(
              'a',
              {
                attrs: {
                  href: '/?fen=' + state.legalFen + '#friend',
                  rel: 'nofollow',
                },
                class: {
                  button: true,
                  'button-empty': true,
                  disabled: !state.playable,
                },
              },
              [h('span.text', { attrs: { 'data-icon': 'U' } }, ctrl.trans.noarg('continueFromHere'))],
            ),
            studyButton(ctrl, state),
          ]),
        ]),
  ]);
}

function inputs(ctrl: EditorCtrl, fen: string): VNode | undefined {
  if (ctrl.cfg.embed) return;
  return h('div.copyables', [
    h('p', [
      h('strong', 'FEN'),
      h('input.copyable', {
        attrs: {
          spellcheck: false,
        },
        props: {
          value: fen,
        },
        on: {
          change(e) {
            const el = e.target as HTMLInputElement;
            ctrl.setFen(el.value.trim());
            el.reportValidity();
          },
          input(e) {
            const el = e.target as HTMLInputElement;
            const valid = parseFen('chess')(el.value.trim()).isOk;
            el.setCustomValidity(valid ? '' : 'Invalid FEN');
          },
          blur(e) {
            const el = e.target as HTMLInputElement;
            el.value = ctrl.getFen();
            el.setCustomValidity('');
          },
        },
      }),
    ]),
    h('p', [
      h('strong.name', 'URL'),
      h('input.copyable.autoselect', {
        attrs: {
          readonly: true,
          spellcheck: false,
          value: ctrl.makeUrl(ctrl.cfg.baseUrl, fen),
        },
      }),
    ]),
  ]);
}

// can be 'pointer', 'trash', or [playerIndex, role]
function selectedToClass(s: Selected): string {
  return s === 'pointer' || s === 'trash' ? s : s.join(' ');
}

let lastTouchMovePos: NumberPair | undefined;

function sparePieces(
  ctrl: EditorCtrl,
  playerIndex: PlayerIndex,
  _orientation: Orientation,
  position: 'top' | 'bottom',
): VNode {
  const selectedClass = selectedToClass(ctrl.selected());

  const pieces = ['k-piece', 'q-piece', 'r-piece', 'b-piece', 'n-piece', 'p-piece'].map(function (role) {
    return [playerIndex, role];
  });

  return h(
    'div',
    {
      attrs: {
        class: ['spare', 'spare-' + position, 'spare-' + playerIndex].join(' '),
      },
    },
    ['pointer', ...pieces, 'trash'].map((s: Selected) => {
      const className = selectedToClass(s);
      const attrs = {
        class: className,
        ...(s !== 'pointer' && s !== 'trash'
          ? {
              'data-playerIndex': s[0],
              'data-role': s[1],
            }
          : {}),
      };
      const selectedSquare =
        selectedClass === className &&
        (!ctrl.chessground ||
          !ctrl.chessground.state.draggable.current ||
          !ctrl.chessground.state.draggable.current.newPiece);
      return h(
        'div',
        {
          class: {
            'no-square': true,
            pointer: s === 'pointer',
            trash: s === 'trash',
            'selected-square': selectedSquare,
          },
          on: {
            mousedown: onSelectSparePiece(ctrl, s, 'mouseup'),
            touchstart: onSelectSparePiece(ctrl, s, 'touchend'),
            touchmove: e => {
              lastTouchMovePos = eventPosition(e as any);
            },
          },
        },
        [h('div', [h('piece', { attrs })])],
      );
    }),
  );
}

function onSelectSparePiece(ctrl: EditorCtrl, s: Selected, upEvent: string): (e: MouchEvent) => void {
  return function (e: MouchEvent): void {
    e.preventDefault();
    if (s === 'pointer' || s === 'trash') {
      ctrl.selected(s);
      ctrl.redraw();
    } else {
      ctrl.selected('pointer');

      dragNewPiece(
        ctrl.chessground!.state,
        {
          playerIndex: s[0],
          role: s[1],
        },
        e,
        true,
      );

      document.addEventListener(
        upEvent,
        (e: MouchEvent) => {
          const eventPos = eventPosition(e) || lastTouchMovePos;
          if (eventPos && ctrl.chessground!.getKeyAtDomPos(eventPos)) ctrl.selected('pointer');
          else ctrl.selected(s);
          ctrl.redraw();
        },
        { once: true },
      );
    }
  };
}

function makeCursor(selected: Selected): string {
  if (selected === 'pointer') return 'pointer';

  const name = selected === 'trash' ? 'trash' : selected.join('-');
  const url = playstrategy.assetUrl('cursors/' + name + '.cur');

  return `url('${url}'), default !important`;
}

export default function (ctrl: EditorCtrl): VNode {
  const state = ctrl.getState();
  const playerIndex = ctrl.bottomPlayerIndex();

  return h(
    'div.board-editor' + '.variant-' + convertRulesToCGVariant(ctrl.rules),
    {
      attrs: {
        style: `cursor: ${makeCursor(ctrl.selected())}`,
      },
    },
    [
      sparePieces(ctrl, opposite(playerIndex), playerIndex, 'top'),
      h('div.main-board', [chessground(ctrl)]),
      sparePieces(ctrl, playerIndex, playerIndex, 'bottom'),
      controls(ctrl, state),
      inputs(ctrl, state.fen),
    ],
  );
}

function convertRulesToCGVariant(rule: Rules): string {
  switch (rule) {
    case 'chess':
      return 'standard';
    case 'kingofthehill':
      return 'kingOfTheHill';
    case 'racingkings':
      return 'racingKings';
    case '3check':
      return 'threeCheck';
    case '5check':
      return 'fiveCheck';
    default:
      return rule;
  }
}
