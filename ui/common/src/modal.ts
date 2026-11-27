import { h, VNode, VNodeChildren } from 'snabbdom';

export interface SnabModal {
  class?: string;
  content: VNodeChildren;
  onInsert?: (el: HTMLElement) => void;
  onClose(): void;
  noClickAway?: boolean;
}

export function snabModal(d: SnabModal): VNode {
  const listen = (event: string, f: (e: Event) => void) => ({
    insert: (vnode: VNode) => (vnode.elm as HTMLElement).addEventListener(event, f),
  });
  return h('div#modal-overlay', d.noClickAway ? {} : { hook: listen('mousedown', d.onClose) }, [
    h(
      'div#modal-wrap' + (d.class ? '.' + d.class : ''),
      {
        hook: {
          insert: vnode => {
            const el = vnode.elm as HTMLElement;
            el.addEventListener('mousedown', e => e.stopPropagation());
            d.onInsert?.(el);
          },
        },
      },
      [h('span.close', { attrs: { 'data-icon': 'L' }, hook: listen('click', d.onClose) }), h('div', d.content)],
    ),
  ]);
}

export default function modal(content: Cash, cls?: string, onClose?: () => void) {
  modal.close();
  const $wrap: any = $('<div id="modal-wrap"><span class="close" data-icon="L"></span></div>');
  const $overlay = $(`<div id="modal-overlay" class="${cls}">`).on('click', modal.close);
  $wrap.appendTo($overlay);
  content.clone().removeClass('none').appendTo($wrap);
  modal.onClose = onClose;
  $wrap.find('.close').on('click', modal.close);
  $wrap.on('click', (e: Event) => e.stopPropagation());
  $('body').addClass('overlayed').prepend($overlay);
  return $wrap;
}
modal.close = () => {
  $('body').removeClass('overlayed');
  $('#modal-overlay').each(function (this: HTMLElement) {
    if (modal.onClose) modal.onClose();
    $(this).remove();
  });
  delete modal.onClose;
};
modal.onClose = undefined as (() => void) | undefined;
