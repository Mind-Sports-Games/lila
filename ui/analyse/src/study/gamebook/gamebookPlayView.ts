import { h, VNode } from 'snabbdom';
import GamebookPlayCtrl, { Feedback } from './gamebookPlayCtrl';
import { bind, dataIcon, iconTag, richHTML } from '../../util';
// eslint-disable-next-line no-duplicate-imports
import { State } from './gamebookPlayCtrl';

const defaultComments: Record<Feedback, string | undefined> = {
  play: 'What would you play in this position?',
  end: 'Congratulations! You completed this lesson.',
  bad: undefined,
  good: undefined,
};

export function render(ctrl: GamebookPlayCtrl): VNode {
  const state = ctrl.state,
    comment = state.comment || defaultComments[state.feedback];

  return h(
    'div.gamebook',
    {
      hook: { insert: _ => playstrategy.loadHashedCssPath('analyse.gamebook.play') },
    },
    [
      comment
        ? h(
            'div.comment',
            {
              class: { hinted: state.showHint },
            },
            [h('div.content', { hook: richHTML(comment) }), hintZone(ctrl)],
          )
        : undefined,
      h('div.floor', [
        renderFeedback(ctrl, state),
        h('img.mascot', {
          attrs: {
            width: 120,
            height: 120,
            src: playstrategy.assetUrl('images/mascot/octopus.svg'),
          },
        }),
      ]),
    ],
  );
}

function hintZone(ctrl: GamebookPlayCtrl) {
  const state = ctrl.state,
    clickHook = () => ({
      hook: bind('click', ctrl.hint, ctrl.redraw),
    });
  if (state.showHint) return h('div', clickHook(), [h('div.hint', { hook: richHTML(state.hint!) })]);
  if (state.hint) return h('a.hint', clickHook(), 'Get a hint');
  return undefined;
}

function renderFeedback(ctrl: GamebookPlayCtrl, state: State) {
  const fb = state.feedback,
    playerIndex = ctrl.root.turnPlayerIndex();
  if (fb === 'bad')
    return h(
      'div.feedback.act.bad' + (state.comment ? '.com' : ''),
      {
        hook: bind('click', ctrl.retry),
      },
      [iconTag('P'), h('span', 'Retry')],
    );
  if (fb === 'good' && state.comment)
    return h(
      'div.feedback.act.good.com',
      {
        hook: bind('click', ctrl.next),
      },
      [h('span.text', { attrs: dataIcon('G') }, 'Next'), h('kbd', '<space>')],
    );
  if (fb === 'end') return renderEnd(ctrl);
  return h(
    'div.feedback.info.' + fb + (state.init ? '.init' : ''),
    h(
      'div',
      fb === 'play'
        ? [
            h('div.no-square', h('piece.k-piece.' + playerIndex)),
            h('div.instruction', [
              h('strong', ctrl.trans.noarg('yourTurn')),
              h('em', ctrl.trans('findTheBestMoveForPlayerIndex', playerIndex)),
            ]),
          ]
        : ['Good move!'],
    ),
  );
}

function renderEnd(ctrl: GamebookPlayCtrl) {
  const study = ctrl.root.study!,
    nextChapter = study.nextChapter();
  return h('div.feedback.end', [
    nextChapter
      ? h(
          'a.next.text',
          {
            attrs: dataIcon('G'),
            hook: bind('click', () => study.setChapter(nextChapter.id)),
          },
          'Next chapter',
        )
      : undefined,
    h(
      'a.retry',
      {
        attrs: dataIcon('P'),
        hook: bind('click', () => ctrl.root.userJump(''), ctrl.redraw),
      },
      'Play again',
    ),
    h(
      'a.analyse',
      {
        attrs: dataIcon('A'),
        hook: bind('click', () => study.setGamebookOverride('analyse'), ctrl.redraw),
      },
      'Analyse',
    ),
  ]);
}
