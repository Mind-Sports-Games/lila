import * as keyboard from '../keyboard';
import * as util from '../util';
import RoundController from '../ctrl';
import { h, VNode } from 'snabbdom';
import { plyStep } from '../round';
import { Position, MaterialDiff, MaterialDiffSide } from '../interfaces';
import { read as fenRead } from 'draughtsground/fen';
import { render as keyboardMove } from '../keyboardMove';
import { render as renderGround } from '../ground';
import { renderTable } from './table';

function renderMaterial(material: MaterialDiffSide, score: number, position: Position) {
  const children: VNode[] = [];
  let role: string, i: number;
  for (role in material) {
    if (material[role] > 0) {
      const content: VNode[] = [];
      for (i = 0; i < material[role]; i++) content.push(h('mpiece.d' + role));
      children.push(h('div', content));
    }
  }
  if (score > 0) children.push(h('score', '+' + score));
  return h('div.material.material-' + position, children);
}

function wheel(ctrl: RoundController, e: WheelEvent): void {
  if (!ctrl.isPlaying()) {
    e.preventDefault();
    if (e.deltaY > 0) keyboard.next(ctrl);
    else if (e.deltaY < 0) keyboard.prev(ctrl);
    ctrl.redraw();
  }
}

const emptyMaterialDiff: MaterialDiff = {
  p1: {},
  p2: {},
};

export function main(ctrl: RoundController): VNode {
  const d = ctrl.data,
    cgState = ctrl.draughtsground && ctrl.draughtsground.state,
    topPlayerIndex = d[ctrl.flip ? 'player' : 'opponent'].playerIndex,
    bottomPlayerIndex = d[ctrl.flip ? 'opponent' : 'player'].playerIndex;
  let material: MaterialDiff,
    score = 0;
  if (d.pref.showCaptured) {
    const pieces = cgState ? cgState.pieces : fenRead(plyStep(ctrl.data, ctrl.ply).fen);
    material = util.getMaterialDiff(pieces);
    score = util.getScore(pieces) * (bottomPlayerIndex === 'p1' ? 1 : -1);
  } else material = emptyMaterialDiff;

  return ctrl.nvui
    ? ctrl.nvui.render(ctrl)
    : h(
        'div.round__app.variant-' + d.game.variant.key + '.is' + ctrl.data.game.variant.board.key,
        {
          class: { 'move-confirm': !!(ctrl.moveToSubmit || ctrl.dropToSubmit) },
        },
        [
          h(
            'div.round__app__board.main-board' + (ctrl.data.pref.blindfold ? '.blindfold' : ''),
            {
              hook:
                'ontouchstart' in window
                  ? undefined
                  : util.bind('wheel', (e: WheelEvent) => wheel(ctrl, e), undefined, false),
            },
            [renderGround(ctrl)],
          ),
          renderMaterial(material[topPlayerIndex], -score, 'top'),
          ...renderTable(ctrl),
          renderMaterial(material[bottomPlayerIndex], score, 'bottom'),
          ctrl.keyboardMove ? keyboardMove(ctrl.keyboardMove) : null,
        ],
      );
}
