import { h, VNode } from 'snabbdom';
import { fixCrazySan } from 'stratutils';
import { defined } from 'common';
import { view as cevalView, renderEval as normalizeEval } from 'ceval';
import { fullTurnNodesFromNode } from './util';
import { NotationStyle } from 'stratops/variants/types';
import { variantClassFromKey } from 'stratops/variants/util';
import { GameFamily as BackgammonFamily } from 'stratops/variants/backgammon/GameFamily';
import { GameFamily as DameoFamily } from 'stratops/variants/dameo/GameFamily';
import { GameFamily as EntropyFamily } from 'stratops/variants/entropy/GameFamily';

export interface Ctx {
  withDots?: boolean;
  showEval: boolean;
  showGlyphs?: boolean;
  variant: Variant;
}

//only used for single action games
export const plyToTurn = (ply: Ply, variantKey: VariantKey = 'standard'): number =>
  variantKey === 'amazons' ? Math.floor((ply - 1) / 4) + 1 : Math.floor((ply - 1) / 2) + 1;

export const nodeToTurn = (node: Tree.ParentedNode): number => Math.floor((node.parent?.turnCount ?? 0) / 2) + 1;

export const renderGlyph = (glyph: Tree.Glyph): VNode =>
  h(
    'glyph',
    {
      attrs: { title: glyph.name },
    },
    glyph.symbol,
  );

const renderEval = (e: string): VNode => h('eval', e.replace('-', '−'));

export function renderIndexText(node: Tree.ParentedNode, withDots?: boolean): string {
  return nodeToTurn(node) + (withDots ? (node.playedPlayerIndex === 'p1' ? '.' : '...') : '');
}

export function renderIndex(node: Tree.ParentedNode, withDots?: boolean): VNode {
  return h('index', renderIndexText(node, withDots));
}

export function renderMove(ctx: Ctx, node: Tree.ParentedNode): VNode[] {
  const ev = cevalView.getBestEval({ client: node.ceval, server: node.eval });
  const variantClass = variantClassFromKey(ctx.variant.key);
  const nodes = [
    h(
      'move',
      ctx.variant.key === 'entropy'
        ? notationOfTurn(ctx.variant, fullTurnNodesFromNode(node), variantClass.getNotationStyle())
        : variantClass.computeMoveNotation({
            san: fixCrazySan(node.san || ''),
            uci: node.uci || '',
            fen: node.fen,
            prevFen: node.parent?.fen || '',
          }),
    ),
  ];
  if (node.glyphs && ctx.showGlyphs) node.glyphs.forEach(g => nodes.push(renderGlyph(g)));
  if (node.shapes) nodes.push(h('shapes'));
  if (ev && ctx.showEval) {
    if (defined(ev.cp)) nodes.push(renderEval(normalizeEval(ev.cp)));
    else if (defined(ev.mate)) nodes.push(renderEval('#' + ev.mate));
  }
  return nodes;
}

export function notationOfTurn(variant: Variant, turnNodes: Tree.ParentedNode[], notation: NotationStyle): string {
  const variantClass = variantClassFromKey(variant.key);
  return combinedNotationOfTurn(
    turnNodes.map(n =>
      variantClass.computeMoveNotation({
        san: fixCrazySan(n.san || ''),
        uci: n.uci || '',
        fen: n.fen,
        prevFen: n.parent?.fen || '',
      }),
    ),
    notation,
    variant.key,
    turnNodes.map(n => n.uci || ''),
  );
}

export function combinedNotationOfTurn(
  actionNotations: string[],
  notation: NotationStyle,
  variantKey?: VariantKey,
  ucis: string[] = [],
): string {
  // an entropy draw is shown by the drop that follows it; a line stopped after the draw shows the draw
  if (variantKey === 'entropy') return EntropyFamily.combinedNotation(actionNotations) || ucis.join(' ');
  return notation === NotationStyle.bkg
    ? BackgammonFamily.combinedNotation(actionNotations)
    : notation === NotationStyle.dmo
      ? DameoFamily.combinedNotation(actionNotations)
      : actionNotations.join(' ');
}

export function renderFullMove(ctx: Ctx, node: Tree.ParentedNode, style: NotationStyle): VNode[] {
  const fullTurnNodes: Tree.ParentedNode[] = fullTurnNodesFromNode(node);
  const ev = cevalView.getBestEval({ client: node.ceval, server: node.eval });
  const nodes = [h('move', notationOfTurn(ctx.variant, fullTurnNodes, style))];
  if (node.glyphs && ctx.showGlyphs) node.glyphs.forEach(g => nodes.push(renderGlyph(g)));
  if (fullTurnNodes.filter(n => n.shapes !== undefined).length > 0) nodes.push(h('shapes'));
  if (ev && ctx.showEval) {
    if (defined(ev.cp)) nodes.push(renderEval(normalizeEval(ev.cp)));
    else if (defined(ev.mate)) nodes.push(renderEval('#' + ev.mate));
  }
  return nodes;
}

export function renderIndexAndMove(ctx: Ctx, node: Tree.ParentedNode): VNode[] | undefined {
  if (!node.san && !node.uci) return; // initial position
  return [renderIndex(node, ctx.withDots), ...renderMove(ctx, node)];
}
