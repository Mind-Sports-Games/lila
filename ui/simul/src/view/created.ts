import { h, VNode } from 'snabbdom';
import SimulCtrl from '../ctrl';
import { Applicant, Variant } from '../interfaces';
import xhr from '../xhr';
import * as util from './util';
import modal from 'common/modal';

export default function (showText: (ctrl: SimulCtrl) => VNode) {
  return (ctrl: SimulCtrl) => {
    const candidates = ctrl.candidates().sort(byName),
      accepted = ctrl.accepted().sort(byName),
      isHost = ctrl.createdByMe();
    const variantIconFor = (a: Applicant) => {
      const variant = ctrl.data.variants.find(v => a.variant == v.key);
      return (
        variant &&
        h('td.variant', {
          attrs: {
            'data-icon': variant.icon,
          },
        })
      );
    };
    return [
      h('div.box__top', [
        util.title(ctrl),
        h(
          'div.box__top__actions',
          ctrl.opts.userId
            ? isHost
              ? [startOrCancel(ctrl, accepted), randomButton(ctrl)]
              : ctrl.containsMe()
                ? h(
                    'a.button',
                    {
                      hook: util.bind('click', () => xhr.withdraw(ctrl.data.id)),
                    },
                    ctrl.trans('withdraw'),
                  )
                : h(
                    'a.button.text' + (ctrl.teamBlock() ? '.disabled' : ''),
                    {
                      attrs: {
                        disabled: ctrl.teamBlock(),
                        'data-icon': 'G',
                      },
                      hook: ctrl.teamBlock()
                        ? {}
                        : util.bind('click', () => {
                            if (ctrl.data.variants.length === 1) xhr.join(ctrl.data.id, ctrl.data.variants[0].key);
                            else {
                              const $wrap = modal($('.simul .continue-with'));
                              $wrap.find('a[data-variant]').on('click', function (this: HTMLElement) {
                                modal.close();
                                xhr.join(ctrl.data.id, $(this).data('variant'));
                              });
                              // a family chip shows its games, and a second click hides them again
                              $wrap.find('.simul-join__family').on('click', function (this: HTMLElement) {
                                const group = this.dataset.group,
                                  wasOpen = this.classList.contains('active');
                                $wrap.find('.simul-join__family').removeClass('active');
                                $wrap.find('.simul-join__games').addClass('none');
                                if (!wasOpen) {
                                  this.classList.add('active');
                                  $wrap.find(`.simul-join__games[data-group="${group}"]`).removeClass('none');
                                }
                              });
                            }
                          }),
                    },
                    ctrl.teamBlock() && ctrl.data.team
                      ? ctrl.trans('mustBeInTeam', ctrl.data.team.name)
                      : ctrl.trans('join'),
                  )
            : h(
                'a.button.text',
                {
                  attrs: {
                    'data-icon': 'G',
                    href: '/login?referrer=' + window.location.pathname,
                  },
                },
                ctrl.trans('signIn'),
              ),
        ),
      ]),
      showText(ctrl),
      ctrl.acceptedContainsMe()
        ? h('p.instructions', 'You have been selected! Hold still, the simul is about to begin.')
        : isHost && ctrl.data.applicants.length < 6
          ? h(
              'p.instructions',
              'Share this page URL to let people enter the simul! ' + ctrl.trans('absentSimulApplicants'),
            )
          : h('p.instructions', ctrl.trans('absentSimulApplicants')),
      h(
        'div.halves',
        {
          hook: {
            postpatch(_old, vnode) {
              playstrategy.powertip.manualUserIn(vnode.elm as HTMLElement);
            },
          },
        },
        [
          h(
            'div.half.candidates',
            h(
              'table.slist.slist-pad',
              h(
                'thead',
                h(
                  'tr',
                  h(
                    'th',
                    {
                      attrs: { colspan: 3 },
                    },
                    [h('strong', candidates.length), ' candidate players'],
                  ),
                ),
              ),
              h(
                'tbody',
                candidates.map(applicant => {
                  return h(
                    'tr',
                    {
                      key: applicant.player.id,
                      class: {
                        me: ctrl.opts.userId === applicant.player.id,
                      },
                    },
                    [
                      h('td', util.player(applicant.player)),
                      variantIconFor(applicant),
                      h(
                        'td.action',
                        isHost
                          ? [
                              h('a.button', {
                                attrs: {
                                  'data-icon': 'E',
                                  title: 'Accept',
                                },
                                hook: util.bind('click', () => xhr.accept(applicant.player.id)(ctrl.data.id)),
                              }),
                            ]
                          : [],
                      ),
                    ],
                  );
                }),
              ),
            ),
          ),
          h('div.half.accepted', [
            h(
              'table.slist.user_list',
              h('thead', [
                h(
                  'tr',
                  h(
                    'th',
                    {
                      attrs: { colspan: 3 },
                    },
                    [h('strong', accepted.length), ' accepted players'],
                  ),
                ),
                isHost && candidates.length && !accepted.length
                  ? [h('tr.help', h('th', 'Now you get to accept some players, then start the simul'))]
                  : [],
              ]),
              h(
                'tbody',
                accepted.map(applicant => {
                  return h(
                    'tr',
                    {
                      key: applicant.player.id,
                      class: {
                        me: ctrl.opts.userId === applicant.player.id,
                      },
                    },
                    [
                      h('td', util.player(applicant.player)),
                      variantIconFor(applicant),
                      h(
                        'td.action',
                        isHost
                          ? [
                              h('a.button.button-red', {
                                attrs: {
                                  'data-icon': 'L',
                                },
                                hook: util.bind('click', () => xhr.reject(applicant.player.id)(ctrl.data.id)),
                              }),
                            ]
                          : [],
                      ),
                    ],
                  );
                }),
              ),
            ),
          ]),
        ],
      ),
      ctrl.data.quote
        ? h('blockquote.pull-quote', [h('p', ctrl.data.quote.text), h('footer', ctrl.data.quote.author)])
        : null,
      h('div.continue-with.none', joinChoices(ctrl)),
    ];
  };
}

// The games a player can join with, family by family as in the simul form: a family of several games
// is a chip that shows them, a family of one game is a chip that joins with it.
const joinChoices = (ctrl: SimulCtrl): VNode[] => {
  const groups = new Map<string, Variant[]>();
  ctrl.data.variants.forEach(v => {
    const key = v.group?.key ?? v.key;
    groups.set(key, [...(groups.get(key) ?? []), v]);
  });
  const several = [...groups.entries()].filter(([, vs]) => vs.length > 1);
  const gameChip = (v: Variant) =>
    h('a.simul-join__chip.text', { attrs: { 'data-variant': v.key, 'data-icon': v.icon } }, v.name);
  // all in one family: its games straight away
  if (several.length === 1 && groups.size === 1) return [h('div.simul-join__row', several[0][1].map(gameChip))];
  return [
    h('div.simul-join__row', [
      ...several.map(([key, vs]) =>
        h(
          'span.simul-join__chip.simul-join__family.text',
          { attrs: { 'data-group': key, 'data-icon': vs[0].icon } },
          vs[0].group?.name ?? key,
        ),
      ),
    ]),
    h(
      'div.simul-join__row.simul-join__singles',
      [...groups.values()].filter(vs => vs.length === 1).map(vs => gameChip(vs[0])),
    ),
    ...several.map(([key, vs]) =>
      h('div.simul-join__row.simul-join__games.none', { attrs: { 'data-group': key } }, vs.map(gameChip)),
    ),
  ];
};

const byName = (a: Applicant, b: Applicant) => (a.player.name > b.player.name ? 1 : -1);

const randomButton = (ctrl: SimulCtrl) =>
  ctrl.candidates().length
    ? h(
        'a.button.text',
        {
          attrs: {
            'data-icon': 'E',
          },
          hook: util.bind('click', () => {
            const candidates = ctrl.candidates();
            const randomCandidate = candidates[Math.floor(Math.random() * candidates.length)];
            xhr.accept(randomCandidate.player.id)(ctrl.data.id);
          }),
        },
        'Accept random candidate',
      )
    : null;

const startOrCancel = (ctrl: SimulCtrl, accepted: Applicant[]) =>
  accepted.length > 1
    ? h(
        'a.button.button-green.text',
        {
          attrs: {
            'data-icon': 'G',
          },
          hook: util.bind('click', () => xhr.start(ctrl.data.id)),
        },
        `Start (${accepted.length})`,
      )
    : h(
        'a.button.button-red.text',
        {
          attrs: {
            'data-icon': 'L',
          },
          hook: util.bind('click', () => {
            if (confirm('Delete this simul?')) xhr.abort(ctrl.data.id);
          }),
        },
        ctrl.trans('cancel'),
      );
