import { h, VNode } from 'snabbdom';
import { SnabModal, snabModal } from 'common/modal';

export const modal = (d: SnabModal): VNode =>
  snabModal({ ...d, class: d.class ? `study__modal.${d.class}` : 'study__modal' });

export function button(name: string): VNode {
  return h(
    'div.form-actions.single',
    h(
      'button.button',
      {
        attrs: { type: 'submit' },
      },
      name,
    ),
  );
}
