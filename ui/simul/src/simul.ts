import { init, VNode, classModule, attributesModule } from 'snabbdom';
import { SimulOpts } from './interfaces';
import Draughtsground from 'draughtsground';
import { Chessground } from 'chessground';
import SimulCtrl from './ctrl';
import PlayStrategyChat from 'chat';

const patch = init([classModule, attributesModule]);

import view from './view/main';

export function PlayStrategySimul(opts: SimulOpts) {
  const element = document.querySelector('main.simul') as HTMLElement;

  playstrategy.socket = new playstrategy.StrongSocket(`/simul/${opts.data.id}/socket/v4`, opts.socketVersion, {
    receive: (t: string, d: any) => ctrl.socket.receive(t, d),
  });
  opts.socketSend = playstrategy.socket.send;
  opts.element = element;
  opts.$side = $('.simul__side').clone();

  let vnode: VNode;

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  const ctrl = new SimulCtrl(opts, redraw);

  const blueprint = view(ctrl);
  element.innerHTML = '';
  vnode = patch(element, blueprint);

  redraw();
}

// that's for the rest of playstrategy to access the chat
window.PlayStrategyChat = PlayStrategyChat;
window.Chessground = Chessground;
window.Draughtsground = Draughtsground;

(window as any).PlayStrategySimul = PlayStrategySimul; // esbuild
