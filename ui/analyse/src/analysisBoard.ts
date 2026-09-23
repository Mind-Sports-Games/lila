import { type VNode, attributesModule, classModule, init } from 'snabbdom';
import boot from './boot';
import PlayStrategyChat from 'chat';
// eslint-disable-next-line no-duplicate-imports
import makeCtrl from './ctrl';
import menuHover from 'common/menuHover';
import view from './view';
import { AnalyseApi, AnalyseOpts } from './interfaces';

export const patch = init([classModule, attributesModule]);

export function PlayStrategyAnalyse(opts: AnalyseOpts): AnalyseApi {
  opts.element = document.querySelector('main.analyse') as HTMLElement;
  opts.trans = playstrategy.trans(opts.i18n);

  let vnode: VNode | undefined;

  const ctrl = (playstrategy.analysis = new makeCtrl(opts, redraw));

  const blueprint = view(ctrl);
  opts.element.innerHTML = '';
  vnode = patch(opts.element, blueprint);

  ctrl.controlConfig.onInit?.();

  function redraw() {
    if (vnode) vnode = patch(vnode, view(ctrl));
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

export { boot };

window.PlayStrategyChat = PlayStrategyChat;

(window as any).PlayStrategyAnalyse = PlayStrategyAnalyse; // esbuild
