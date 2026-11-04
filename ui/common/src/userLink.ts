import { h, VNode } from 'snabbdom';

export const userLink = (username: string): VNode =>
  h('a.user-link.ulpt', { attrs: { href: '/@/' + username } }, username);
