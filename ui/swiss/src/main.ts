import { init, VNode, classModule, attributesModule } from 'snabbdom';
import Draughtsground from 'draughtsground';
import { Chessground } from 'chessground';
import { SwissOpts } from './interfaces';
import SwissCtrl from './ctrl';
import PlayStrategyChat from 'chat';

const patch = init([classModule, attributesModule]);

import view from './view/main';

export function start(opts: SwissOpts) {
  const element = document.querySelector('main.swiss') as HTMLElement;

  playstrategy.socket = new playstrategy.StrongSocket('/swiss/' + opts.data.id, opts.data.socketVersion || 0, {
    receive: (t: string, d: any) => ctrl.socket.receive(t, d),
  });
  opts.classes = element.getAttribute('class');
  opts.socketSend = playstrategy.socket.send;
  opts.element = element;
  opts.$side = $('.swiss__side').clone();

  let vnode: VNode;

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  const ctrl = new SwissCtrl(opts, redraw);

  const blueprint = view(ctrl);
  element.innerHTML = '';
  vnode = patch(element, blueprint);

  redraw();
}

// that's for the rest of playstrategy to access chessground
// without having to include it a second time
window.Chessground = Chessground;
window.Draughtsground = Draughtsground;
window.PlayStrategyChat = PlayStrategyChat;
