import { Chessground } from 'chessground';
import RacerCtrl from '../ctrl';
import { makeCgOpts } from 'puz/run';
import { makeConfig as makeCgConfig } from 'puz/view/chessground';
import { h, VNode } from 'snabbdom';
import { INITIAL_BOARD_FEN } from 'stratops/fen';

export const renderBoard = (ctrl: RacerCtrl) =>
  h('div.puz-board.main-board', [
    renderGround(ctrl),
    ctrl.promotion.view(),
    ctrl.countdownSeconds() ? renderCountdown(ctrl.countdownSeconds() ?? 0) : undefined,
  ]);

const renderGround = (ctrl: RacerCtrl): VNode =>
  h('div.cg-wrap', {
    hook: {
      insert: vnode =>
        ctrl.ground(
          Chessground(
            vnode.elm as HTMLElement,
            makeCgConfig(
              ctrl.isRacing() && ctrl.isPlayer()
                ? makeCgOpts(ctrl.run, true)
                : {
                    fen: INITIAL_BOARD_FEN,
                    orientation: ctrl.run.pov,
                    myPlayerIndex: ctrl.run.pov,
                    movable: { playerIndex: ctrl.run.pov },
                  },
              ctrl.pref,
              ctrl.userMove,
            ),
          ),
        ),
    },
  });

const renderCountdown = (seconds: number) =>
  h('div.racer__countdown', [
    h('div.racer__countdown__lights', [
      h('light.red', {
        class: { active: seconds > 4 },
      }),
      h('light.orange', {
        class: { active: seconds == 3 || seconds == 4 },
      }),
      h('light.green', {
        class: { active: seconds <= 2 },
      }),
    ]),
    h('div.racer__countdown__seconds', seconds),
  ]);
