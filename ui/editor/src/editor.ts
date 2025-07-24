import { init, attributesModule, eventListenersModule, classModule, propsModule } from 'snabbdom';
import EditorCtrl from './ctrl';
import menuHover from 'common/menuHover';
import view from './view';
import { Chessground } from 'chessground';

const patch = init([classModule, attributesModule, propsModule, eventListenersModule]);

export default function PlayStrategyEditor(element: HTMLElement, config: Editor.Config): PlayStrategyEditor {
  const ctrl = new EditorCtrl(config, redraw);
  element.innerHTML = '';
  const inner = document.createElement('div');
  element.appendChild(inner);
  let vnode = patch(inner, view(ctrl));

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  menuHover();

  return {getFen: ctrl.getFenFromSetup.bind(ctrl),
    setOrientation: ctrl.setOrientation.bind(ctrl),
  };
}

// that's for the rest of playstrategy to access chessground
// without having to include it a second time
window.Chessground = Chessground;

(window as any).PlayStrategyEditor = PlayStrategyEditor; // esbuild
