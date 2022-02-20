import { h, VNode } from 'snabbdom';
import { fixCrazySan, NotationStyle } from 'chess';
import { moveFromNotationStyle } from 'common/notation';
import { defined } from 'common';
import { view as cevalView, renderEval as normalizeEval } from 'ceval';

export interface Ctx {
  withDots?: boolean;
  showEval: boolean;
  showGlyphs?: boolean;
  variant: Variant;
}

export const plyToTurn = (ply: Ply): number => Math.floor((ply - 1) / 2) + 1;

export const renderGlyph = (glyph: Tree.Glyph): VNode =>
  h(
    'glyph',
    {
      attrs: { title: glyph.name },
    },
    glyph.symbol
  );

const renderEval = (e: string): VNode => h('eval', e.replace('-', '−'));

export function renderIndexText(ply: Ply, withDots?: boolean): string {
  return plyToTurn(ply) + (withDots ? (ply % 2 === 1 ? '.' : '...') : '');
}

export function renderIndex(ply: Ply, withDots?: boolean): VNode {
  return h('index', renderIndexText(ply, withDots));
}

export function renderMove(ctx: Ctx, node: Tree.ParentedNode, style: NotationStyle): VNode[] {
  const variant = ctx.variant;
  const ev = cevalView.getBestEval({ client: node.ceval, server: node.eval });
  const nodes = [
    h('move',
      // TODO: the || '' are probably not correct
      moveFromNotationStyle(style)({
        san: fixCrazySan(node.san || ''),
        uci: node.uci || '',
        fen: node.fen,
        prevFen: node.parent?.fen || '',
      }, variant)
    )
  ];
  if (node.glyphs && ctx.showGlyphs) node.glyphs.forEach(g => nodes.push(renderGlyph(g)));
  if (node.shapes) nodes.push(h('shapes'));
  if (ev && ctx.showEval) {
    if (defined(ev.cp)) nodes.push(renderEval(normalizeEval(ev.cp)));
    else if (defined(ev.mate)) nodes.push(renderEval('#' + ev.mate));
  }
  return nodes;
}

export function renderIndexAndMove(ctx: Ctx, node: Tree.ParentedNode, style: NotationStyle): VNode[] | undefined {
  if (!node.san) return; // initial position
  return [renderIndex(node.ply, ctx.withDots), ...renderMove(ctx, node, style)];
}
