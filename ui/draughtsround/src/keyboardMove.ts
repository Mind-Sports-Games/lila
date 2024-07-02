import { h } from 'snabbdom';
import * as cg from 'draughtsground/types';
import { Step, Redraw } from './interfaces';
import RoundController from './ctrl';
import { ClockController } from './clock/clockCtrl';
import { onInsert } from './util';

export type KeyboardMoveHandler = (fen: Fen, dests?: cg.Dests, captLen?: number, yourMove?: boolean) => void;

export interface KeyboardMove {
  update(step: Step, yourMove?: boolean): void;
  registerHandler(h: KeyboardMoveHandler): void;
  hasFocus(): boolean;
  setFocus(v: boolean): void;
  san(orig: cg.Key, dest: cg.Key): void;
  select(key: cg.Key): void;
  hasSelected(): cg.Key | undefined;
  confirmMove(): void;
  usedSan: boolean;
  jump(delta: number): void;
  justSelected(): boolean;
  clock(): ClockController | undefined;
  resign(v: boolean, immediately?: boolean): void;
}

export function ctrl(root: RoundController, step: Step, redraw: Redraw): KeyboardMove {
  let focus = false;
  let handler: KeyboardMoveHandler | undefined;
  let preHandlerBuffer = step.fen;
  let lastSelect = performance.now();
  const dgState = root.draughtsground.state;
  const select = (key: cg.Key): void => {
    if (dgState.selected === key) root.draughtsground.cancelMove();
    else {
      root.draughtsground.selectSquare(key, true);
      lastSelect = performance.now();
    }
  };
  let usedSan = false;
  return {
    update(step, yourMove?: boolean) {
      if (handler) handler(step.fen, dgState.movable.dests, dgState.movable.captLen, yourMove);
      else preHandlerBuffer = step.fen;
    },
    registerHandler(h: KeyboardMoveHandler) {
      handler = h;
      if (preHandlerBuffer) handler(preHandlerBuffer, dgState.movable.dests, dgState.movable.captLen);
    },
    hasFocus: () => focus,
    setFocus(v) {
      focus = v;
      redraw();
    },
    san(orig, dest) {
      usedSan = true;
      root.draughtsground.cancelMove();
      select(orig);
      select(dest);
    },
    select,
    hasSelected: () => dgState.selected,
    confirmMove() {
      root.submitMove(true);
    },
    usedSan,
    jump(delta: number) {
      root.userJump(root.ply + delta);
      redraw();
    },
    justSelected() {
      return performance.now() - lastSelect < 500;
    },
    clock: () => root.clock,
    resign: root.resign,
  };
}

export function render(ctrl: KeyboardMove) {
  return h('div.keyboard-move', [
    h('input', {
      attrs: {
        spellcheck: false,
        autocomplete: false,
      },
      hook: onInsert(input =>
        playstrategy
          .loadModule('round.keyboardMove') // TODO: this is likely the wrong name.
          .then(() => ctrl.registerHandler(playstrategy.keyboardMove({ input, ctrl }))),
      ),
    }),
    ctrl.hasFocus()
      ? h('em', 'Enter moves (14x3, 5-10) or squares (1403, 0510), or type / to focus chat')
      : h('strong', 'Press <enter> to focus'),
  ]);
}
