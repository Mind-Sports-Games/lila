import { h, VNode } from 'snabbdom';
import { MouchEvent, NumberPair } from 'chessground/types';
import { dragNewPiece } from 'chessground/drag';
import { eventPosition, opposite } from 'chessground/util';
import { parseFen } from 'stratops/fen';
import EditorCtrl from './ctrl';
import chessground from './chessground';
import { Selected, CastlingToggle, EditorState } from './interfaces';
import { VariantKey as stratopsVariantKey } from 'stratops/variants/types';
import { variantClassFromKey } from 'stratops/variants/util';
import { makeSquare, parseSquare } from 'stratops';

function capitalizeFirstLetter(val: string) {
  return val.charAt(0).toUpperCase() + val.slice(1);
}

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
      h('input', { attrs: { type: 'hidden', name: 'variant', value: ctrl.variantKey } }),
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

function isChessRules(variantKey: VariantKey): boolean {
  return variantClassFromKey(variantKey).family == 'chess';
}

function variant2option(key: stratopsVariantKey, name: string, ctrl: EditorCtrl): VNode {
  return h(
    'option',
    {
      attrs: {
        value: key,
        selected: key == ctrl.variantKey,
      },
    },
    `${ctrl.trans.noarg('variant')} | ${name}`,
  );
}

const allVariants: Array<[stratopsVariantKey, string]> = [
  [stratopsVariantKey.standard, 'Standard'],
  [stratopsVariantKey.antichess, 'Antichess'],
  [stratopsVariantKey.atomic, 'Atomic'],
  [stratopsVariantKey.crazyhouse, 'Crazyhouse'],
  [stratopsVariantKey.horde, 'Horde'],
  [stratopsVariantKey.kingOfTheHill, 'King of the Hill'],
  [stratopsVariantKey.racingKings, 'Racing Kings'],
  [stratopsVariantKey.threeCheck, 'Three-check'],
  [stratopsVariantKey.fiveCheck, 'Five-check'],
  [stratopsVariantKey.monster, 'Monster'],
  [stratopsVariantKey.minibreakthroughtroyka, 'Mini Breakthrough'],
  [stratopsVariantKey.breakthroughtroyka, 'Breakthrough'],
  [stratopsVariantKey.linesOfAction, 'Lines of Action'],
  [stratopsVariantKey.scrambledEggs, 'Scrambled Eggs'],
  [stratopsVariantKey.flipello, 'Othello'],
  [stratopsVariantKey.flipello10, 'Grand Othello'],
  [stratopsVariantKey.antiflipello, 'AntiOthello'],
  [stratopsVariantKey.xiangqi, 'Xiangqi'],
  [stratopsVariantKey.minixiangqi, 'Mini Xiangqi'],
  [stratopsVariantKey.amazons, 'Amazons'],
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
                isChessRules(ctrl.variantKey) && ctrl.standardInitialPosition
                  ? optgroup(ctrl.trans.noarg('popularOpenings'), ctrl.cfg.positions.map(position2option))
                  : null,
              ],
            ),
          ]),
        ]),
    renderMetadata(ctrl, state),
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
                    ctrl.changeVariant((e.target as HTMLSelectElement).value as VariantKey);
                    ctrl.startPosition();
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
                  href: state.playable ? '/?fen=' + state.legalFen + '#game' : '#',
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

function renderMetadata(ctrl: EditorCtrl, state: EditorState): VNode {
  const variant = variantClassFromKey(ctrl.variantKey);
  const coords = variant.getPiecesCoordinates(ctrl.getFenFromSetup(), ctrl.turn);
  const lastMoveValue =
    (ctrl.variantKey === stratopsVariantKey.amazons || ctrl.variantKey === stratopsVariantKey.monster) &&
    ctrl.lastAction &&
    coords.map(c => c.coord).includes(makeSquare(ctrl.rules)(ctrl.lastAction.to))
      ? makeSquare(ctrl.rules)(ctrl.lastAction.to)
      : '';

  return h('div.metadata', [
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
        Object.keys(variant.playerColors).map(function (key) {
          return h(
            'option',
            {
              attrs: {
                value: key,
                selected: ctrl.turn === key,
              },
            },
            ctrl.trans('playerIndexPlays', capitalizeFirstLetter(variant.playerColors[key])),
          );
        }),
      ),
    ),
    variant.allowCastling
      ? h('div.castling', [
          h('strong', ctrl.trans.noarg('castling')),
          h('div', [
            castleCheckBox(ctrl, 'K', ctrl.trans.noarg('whiteCastlingKingside'), !!ctrl.options.inlineCastling),
            castleCheckBox(ctrl, 'Q', 'O-O-O', true),
          ]),
          h('div', [
            castleCheckBox(ctrl, 'k', ctrl.trans.noarg('blackCastlingKingside'), !!ctrl.options.inlineCastling),
            castleCheckBox(ctrl, 'q', 'O-O-O', true),
          ]),
        ])
      : null,
    // Copied the en passant HTML generation logic from lichess (AGPL-3):
    // https://github.com/lichess-org/lila/commit/2017682f3871d90ab68db7c3495d4886c9c9fcfe
    variant.allowEnPassant()
      ? h('div.enpassant', [
          h('label', { attrs: { for: 'enpassant-select' } }, 'En passant'),
          h(
            'select#enpassant-select',
            {
              on: {
                change(e) {
                  ctrl.setEnPassant(parseSquare(ctrl.rules)((e.target as HTMLSelectElement).value));
                  if(ctrl.variantKey == stratopsVariantKey.monster) {
                    ctrl.setLastAction(undefined);
                  }
                },
              },
              props: { value: ctrl.epSquare ? makeSquare(ctrl.rules)(ctrl.epSquare) : '' },
            },
            ['', ...[ctrl.turn === 'p2' ? 3 : 6].flatMap(r => 'abcdefgh'.split('').map(f => f + r))].map(key =>
              h(
                'option',
                {
                  attrs: {
                    value: key,
                    selected: (key ? parseSquare(ctrl.rules)(key) : undefined) === ctrl.epSquare,
                    hidden: Boolean(key && !state.enPassantOptions.includes(key)),
                    disabled: Boolean(key && !state.enPassantOptions.includes(key)) /*Safari*/,
                  },
                },
                key,
              ),
            ),
          ),
        ])
      : null,
    ctrl.variantKey == stratopsVariantKey.amazons
    || (ctrl.variantKey == stratopsVariantKey.monster && ctrl.turn == 'p1')
      ? h('div.lastAction', [
          h('label', { attrs: { for: 'last-action-select' } }, 'Last Move'),
          h(
            'select#last-action-select',
            {
              key: coords.map(c => c.coord).join(','), // force a re-render if the list of options change
              on: {
                change(e) {
                  ctrl.setLastAction(parseSquare(ctrl.rules)((e.target as HTMLSelectElement).value));
                  if(ctrl.variantKey == stratopsVariantKey.monster) {
                    ctrl.setEnPassant(undefined);
                  }
                },
              },
              props: { value: lastMoveValue },
            },
            [undefined, ...coords].map(c =>
              h(
                'option',
                {
                  attrs: {
                    value: c?.coord ?? '',
                    selected: c ? ctrl.lastAction?.to === parseSquare(ctrl.rules)(c.coord) : '',
                  },
                },
                c?.coord ?? '',
              ),
            ),
          ),
        ])
      : null,
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
            const valid = parseFen(ctrl.rules)(el.value.trim()).isOk;
            el.setCustomValidity(valid ? '' : 'Invalid FEN');
          },
          blur(e) {
            const el = e.target as HTMLInputElement;
            el.value = ctrl.getFenFromSetup();
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

  let pieces = ['k-piece', 'q-piece', 'r-piece', 'b-piece', 'n-piece', 'p-piece'].map(function (role) {
    return [playerIndex, role];
  });
  if (
    [
      'breakthroughtroyka',
      'minibreakthroughtroyka',
      'flipello',
      'flipello10',
      'antiflipello',
      'octagonflipello',
    ].includes(ctrl.variantKey)
  ) {
    pieces = ['p-piece'].map(function (role) {
      return [playerIndex, role];
    });
  }
  if (['linesOfAction', 'scrambledEggs'].includes(ctrl.variantKey)) {
    pieces = ['l-piece'].map(function (role) {
      return [playerIndex, role];
    });
  }
  if (ctrl.variantKey == 'xiangqi') {
    pieces = ['k-piece', 'a-piece', 'c-piece', 'r-piece', 'b-piece', 'n-piece', 'p-piece'].map(function (role) {
      return [playerIndex, role];
    });
  }
  if (ctrl.variantKey == 'minixiangqi') {
    pieces = ['k-piece', 'c-piece', 'r-piece', 'n-piece', 'p-piece'].map(function (role) {
      return [playerIndex, role];
    });
  }
  if (ctrl.variantKey == 'amazons') {
    pieces = ['q-piece', 'p-piece'].map(function (role) {
      return [playerIndex, role];
    });
  }

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

function makeCursor(selected: Selected, variantKey: VariantKey): string {
  if (selected === 'pointer') return 'pointer';

  const name = selected === 'trash' ? 'trash' : selected.join('-');

  if (selected !== 'trash') {
    if (!isChessRules(variantKey)) {
      return '';
    }
  }

  const url = playstrategy.assetUrl('cursors/' + name + '.cur');

  return `url('${url}'), default !important`;
}

export default function (ctrl: EditorCtrl): VNode {
  const state = ctrl.getState();
  const playerIndex = ctrl.bottomPlayerIndex();

  return h(
    'div.board-editor' + '.variant-' + ctrl.variantKey,
    {
      attrs: {
        style: `cursor: ${makeCursor(ctrl.selected(), ctrl.variantKey)}`,
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
