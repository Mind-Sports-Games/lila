import { attributesModule, classModule, init } from 'snabbdom';
import boot from './boot';
import PlayStrategyChat from 'chat';
// eslint-disable-next-line no-duplicate-imports
import makeCtrl from './ctrl';
import menuHover from 'common/menuHover';
import view from './view';
import { AnalyseApi, AnalyseOpts } from './interfaces';
import { Chessground } from 'chessground';
import Draughtsground from 'draughtsground';

export const patch = init([classModule, attributesModule]);

export function PlayStrategyAnalyse(opts: AnalyseOpts): AnalyseApi {
  console.log('PlayStrategyAnalyse invoked :)');

  opts.element = document.querySelector('main.analyse') as HTMLElement;
  opts.trans = playstrategy.trans(opts.i18n);

  const ctrl = (playstrategy.analysis = new makeCtrl(opts, redraw));

  const blueprint = view(ctrl);
  opts.element.innerHTML = '';
  let vnode = patch(opts.element, blueprint);

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  menuHover();

  return {
    socketReceive: ctrl.socket.receive,
    path: () => ctrl.path,
    setChapter(id: string) {
      if (ctrl.study) ctrl.study.setChapter(id);
    },
  };
}

console.log('boot analysisBoard');

export { boot };

// that's for the rest of playstrategy to access chessground
// without having to include it a second time
window.Chessground = Chessground;
window.Draughtsground = Draughtsground;
window.PlayStrategyChat = PlayStrategyChat;

(window as any).PlayStrategyAnalyse = PlayStrategyAnalyse; // esbuild
