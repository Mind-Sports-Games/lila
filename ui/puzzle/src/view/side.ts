import { Controller, Puzzle, PuzzleGame, MaybeVNode } from '../interfaces';
import { dataIcon, onInsert, bind } from '../util';
import { h, VNode } from 'snabbdom';
import { numberFormat } from 'common/number';
import PuzzleStreak from '../streak';

export function puzzleBox(ctrl: Controller): VNode {
  const data = ctrl.getData();
  return h('div.puzzle__side__metas', [puzzleInfos(ctrl, data.puzzle), gameInfos(ctrl, data.game, data.puzzle)]);
}

function puzzleInfos(ctrl: Controller, puzzle: Puzzle): VNode {
  const variant = ctrl.getData().game.variant.key;
  return h(
    'div.infos.puzzle',
    {
      attrs: dataIcon('-'),
    },
    [
      h('div', [
        ctrl.streak
          ? null
          : h(
              'p',
              ctrl.trans.vdom(
                'puzzleId',
                h(
                  'a',
                  {
                    attrs: {
                      href: `/training/${variant}/${puzzle.id}`,
                      ...(ctrl.streak ? { target: '_blank' } : {}),
                    },
                  },
                  '#' + puzzle.id,
                ),
              ),
            ),
        h(
          'p',
          ctrl.trans.vdom(
            'ratingX',
            !ctrl.streak && ctrl.vm.mode === 'play'
              ? h('span.hidden', ctrl.trans.noarg('hidden'))
              : h('strong', puzzle.rating),
          ),
        ),
        h('p', ctrl.trans.vdom('playedXTimes', h('strong', numberFormat(puzzle.plays)))),
      ]),
    ],
  );
}

function gameInfos(ctrl: Controller, game: PuzzleGame, puzzle: Puzzle): VNode {
  const gameName = `${game.clock} • ${game.perf.name}`;
  return h(
    'div.infos',
    {
      attrs: dataIcon(ctrl.vm.perfIcon),
    },
    [
      h('div', [
        h(
          'p',
          ctrl.trans.vdom(
            'fromGameLink',
            ctrl.vm.mode == 'play'
              ? h('span', gameName)
              : h(
                  'a',
                  {
                    attrs: { href: `/${game.id}/${ctrl.vm.pov}#${puzzle.initialPly}` },
                  },
                  gameName,
                ),
          ),
        ),
        h(
          'div.players',
          game.players.map(p =>
            h(
              //TODO: when puzzle has different game families it should use playerColor not playerIndex
              'div.player.playerIndex-icon.is.text.' + p.playerIndex,
              p.userId != 'anon'
                ? h(
                    'a.user-link.ulpt',
                    {
                      attrs: { href: '/@/' + p.userId },
                    },
                    p.title && p.title != 'BOT' ? [h('span.utitle', p.title), ' ' + p.name] : p.name,
                  )
                : p.name,
            ),
          ),
        ),
      ]),
    ],
  );
}

const renderStreak = (streak: PuzzleStreak, noarg: TransNoArg) =>
  h(
    'div.puzzle__side__streak',
    streak.data.index == 0
      ? h('div.puzzle__side__streak__info', [
          h(
            'h1.text',
            {
              attrs: dataIcon('}'),
            },
            'Puzzle Streak',
          ),
          h('p', noarg('streakDescription')),
        ])
      : h(
          'div.puzzle__side__streak__score.text',
          {
            attrs: dataIcon('}'),
          },
          streak.data.index,
        ),
  );

export const userBox = (ctrl: Controller): VNode => {
  const data = ctrl.getData();
  if (!data.user)
    return h('div.puzzle__side__user', [
      h('p', ctrl.trans.noarg('toGetPersonalizedPuzzles')),
      h('a.button', { attrs: { href: '/signup' } }, ctrl.trans.noarg('signUp')),
    ]);
  const diff = ctrl.vm.round?.ratingDiff;
  return h('div.puzzle__side__user', [
    h(
      'div.puzzle__side__user__rating',
      ctrl.trans.vdom(
        'yourPuzzleRatingX',
        h('strong', { attrs: dataIcon(ctrl.vm.perfIcon) }, [
          data.user.rating - (diff || 0),
          data.user.provisional ? h('span.provisional', '?') : null,
          ...(diff && diff > 0 ? [' ', h('good.rp', '+' + diff)] : []),
          ...(diff && diff < 0 ? [' ', h('bad.rp', '−' + -diff)] : []),
        ]),
      ),
    ),
  ]);
};

export const streakBox = (ctrl: Controller) =>
  h('div.puzzle__side__user', renderStreak(ctrl.streak!, ctrl.trans.noarg));

// While we have 1 large bucket of puzzles we don't want to show difficulty selector
// const difficulties: [PuzzleDifficulty, number][] = [
//   ['easiest', -600],
//   ['easier', -300],
//   ['normal', 0],
//   ['harder', 300],
//   ['hardest', 600],
// ];

const variants: [string, string][] = [
  ['standard', 'Chess'],
  ['kingOfTheHill', 'King of the Hill'],
  ['atomic', 'Atomic'],
  ['horde', 'Horde'],
  ['racingKings', 'Racing Kings'],
  ['linesOfAction', 'Lines of Action'],
];

export function replay(ctrl: Controller): MaybeVNode {
  const replay = ctrl.getData().replay;
  const variant = ctrl.getData().game.variant.key;
  if (!replay) return;
  const i = replay.i + (ctrl.vm.mode == 'play' ? 0 : 1);
  return h('div.puzzle__side__replay', [
    h(
      'a',
      {
        attrs: {
          href: `/training/${variant}/dashboard/${replay.days}`,
        },
      },
      ['« ', `Replaying ${ctrl.trans.noarg(ctrl.getData().theme.key)} puzzles`],
    ),
    h('div.puzzle__side__replay__bar', {
      attrs: {
        style: `--p:${replay.of ? Math.round((100 * i) / replay.of) : 1}%`,
        'data-text': `${i} / ${replay.of}`,
      },
    }),
  ]);
}

export function config(ctrl: Controller): MaybeVNode {
  const id = 'puzzle-toggle-autonext';
  return h('div.puzzle__side__config', [
    h('div.puzzle__side__config__jump', [
      h('div.switch', [
        h(`input#${id}.cmn-toggle.cmn-toggle--subtle`, {
          attrs: {
            type: 'checkbox',
            checked: ctrl.autoNext(),
          },
          hook: {
            insert: vnode =>
              (vnode.elm as HTMLElement).addEventListener('change', () => ctrl.autoNext(!ctrl.autoNext())),
          },
        }),
        h('label', { attrs: { for: id } }),
      ]),
      h('label', { attrs: { for: id } }, ctrl.trans.noarg('jumpToNextPuzzleImmediately')),
    ]),
    // While we have 1 large bucket of puzzles we don't want to show difficulty selector
    // !ctrl.getData().replay && !ctrl.streak && ctrl.difficulty
    //   ? h(
    //       'form.puzzle__side__config__difficulty',
    //       {
    //         attrs: {
    //           action: `/training/${ctrl.getData().game.variant.key}/difficulty/${ctrl.getData().theme.key}`,
    //           method: 'post',
    //         },
    //       },
    //       [
    //         h(
    //           'label',
    //           {
    //             attrs: { for: 'puzzle-difficulty' },
    //           },
    //           ctrl.trans.noarg('difficultyLevel'),
    //         ),
    //         h(
    //           'select#puzzle-difficulty.puzzle__difficulty__selector',
    //           {
    //             attrs: { name: 'difficulty' },
    //             hook: onInsert(elm =>
    //               elm.addEventListener('change', () => (elm.parentNode as HTMLFormElement).submit()),
    //             ),
    //           },
    //           difficulties.map(([key, delta]) =>
    //             h(
    //               'option',
    //               {
    //                 attrs: {
    //                   value: key,
    //                   selected: key == ctrl.difficulty,
    //                   title:
    //                     !!delta &&
    //                     ctrl.trans.plural(
    //                       delta < 0 ? 'nbPointsBelowYourPuzzleRating' : 'nbPointsAboveYourPuzzleRating',
    //                       Math.abs(delta),
    //                     ),
    //                 },
    //               },
    //               [ctrl.trans.noarg(key), delta ? ` (${delta > 0 ? '+' : ''}${delta})` : ''],
    //             ),
    //           ),
    //         ),
    //       ],
    //     )
    //   : null,
    !ctrl.getData().replay && !ctrl.streak
      ? !ctrl.getData().user
        ? // For anonymous users, use a select and redirect on change
          h('div.puzzle__side__config__variant', [
            h('label', { attrs: { for: 'puzzle-variant' } }, ctrl.trans.noarg('variant')),
            h(
              'select#puzzle-variant.puzzle__variant__selector',
              {
                attrs: { name: 'variant' },
                hook: onInsert(elm =>
                  elm.addEventListener('change', () => {
                    const variant = (elm as HTMLSelectElement).value;
                    window.location.href = `/training/${variant}`;
                  }),
                ),
              },
              variants.map(([variantKey, variantName]) =>
                h(
                  'option',
                  {
                    attrs: {
                      value: variantKey,
                      selected: variantKey == ctrl.getData().game.variant.key,
                    },
                  },
                  ctrl.trans.noarg(variantName),
                ),
              ),
            ),
          ])
        : // For logged-in users, render the form as before
          h(
            'form.puzzle__side__config__variant',
            {
              attrs: {
                action: `/training/set-variant/mix`,
                method: 'post',
              },
            },
            [
              h('label', { attrs: { for: 'puzzle-variant' } }, ctrl.trans.noarg('variant')),
              h(
                'select#puzzle-variant.puzzle__variant__selector',
                {
                  attrs: { name: 'variant' },
                  hook: onInsert(elm =>
                    elm.addEventListener('change', () => (elm.parentNode as HTMLFormElement).submit()),
                  ),
                },
                variants.map(([variantKey, variantName]) =>
                  h(
                    'option',
                    {
                      attrs: {
                        value: variantKey,
                        selected: variantKey == ctrl.getData().game.variant.key,
                      },
                    },
                    ctrl.trans.noarg(variantName),
                  ),
                ),
              ),
            ],
          )
      : null,
    h(
      'a.puzzle__side__config__zen',
      {
        hook: bind('click', () => playstrategy.pubsub.emit('zen')),
        attrs: {
          title: 'Keyboard: z',
        },
      },
      ctrl.trans.noarg('zenMode'),
    ),
  ]);
}
