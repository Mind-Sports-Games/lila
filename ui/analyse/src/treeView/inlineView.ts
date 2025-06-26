import { h, VNode } from 'snabbdom';
import { fixCrazySan } from 'stratutils';
import { path as treePath, ops as treeOps } from 'tree';
import * as moveView from '../moveView';
import AnalyseCtrl from '../ctrl';
import { MaybeVNodes } from '../interfaces';
import { mainHook, nodeClasses, findCurrentPath, renderInlineCommentsOf, retroLine, Ctx, Opts } from './treeView';
import { parentedNode, parentedNodes, parentedNodesFromOrdering, fullTurnNodesFromNode } from '../util';
import { variantClassFromKey } from 'stratops/variants/util';

function renderChildrenOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): MaybeVNodes | undefined {
  const cs = parentedNodes(node.children, node),
    main = cs[0];
  if (!main) return;
  if (opts.isMainline) {
    if (!cs[1] && !main.forceVariation)
      return renderMoveAndChildrenOf(ctx, main, {
        parentPath: opts.parentPath,
        isMainline: true,
        withIndex: opts.withIndex,
      });
    return (
      renderInlined(ctx, cs, opts) || [
        ...(main.forceVariation
          ? []
          : [
              renderMoveOf(ctx, main, {
                parentPath: opts.parentPath,
                isMainline: true,
                withIndex: opts.withIndex,
              }),
              ...renderInlineCommentsOf(ctx, main),
            ]),
        h(
          'interrupt',
          renderLines(ctx, main.forceVariation ? cs : cs.slice(1), {
            parentPath: opts.parentPath,
            isMainline: true,
          }),
        ),
        ...(main.forceVariation
          ? []
          : renderChildrenOf(ctx, main, {
              parentPath: opts.parentPath + main.id,
              isMainline: true,
              withIndex: true,
            }) || []),
      ]
    );
  }
  if (!cs[1]) return renderMoveAndChildrenOf(ctx, main, opts);
  return renderInlined(ctx, cs, opts) || [renderLines(ctx, cs, opts)];
}

function renderInlined(ctx: Ctx, nodes: Tree.ParentedNode[], opts: Opts): MaybeVNodes | undefined {
  // only 2 branches
  if (!nodes[1] || nodes[2] || nodes[0].forceVariation) return;
  // only if second branch has no sub-branches
  if (treeOps.hasBranching(nodes[1], 6)) return;
  return renderMoveAndChildrenOf(ctx, nodes[0], {
    parentPath: opts.parentPath,
    isMainline: opts.isMainline,
    inline: nodes[1],
  });
}

function renderLines(ctx: Ctx, nodes: Tree.Node[], opts: Opts): VNode {
  return h(
    'lines',
    parentedNodesFromOrdering(nodes).map(n => {
      return (
        retroLine(ctx, n) ||
        h(
          'line',
          renderMoveAndChildrenOf(ctx, n, {
            parentPath: opts.parentPath,
            isMainline: false,
            withIndex: true,
            truncate: n.comp && !treePath.contains(ctx.ctrl.path, opts.parentPath + n.id) ? 3 : undefined,
          }),
        )
      );
    }),
  );
}

function renderMoveAndChildrenOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): MaybeVNodes {
  const nodesOfFullTurn = fullTurnNodesFromNode(node);
  const lastNodeOfFullMove = nodesOfFullTurn[nodesOfFullTurn.length - 1];
  const path =
      opts.parentPath +
      nodesOfFullTurn
        .map(n => {
          return n.id;
        })
        .join(''),
    comments = renderInlineCommentsOf(ctx, node);
  if (opts.truncate === 0) return [h('move', { attrs: { p: path } }, '[...]')];
  //check if childen within a full move turn of many actions and render the variation
  const cs = parentedNodes(node.children, node);
  if (node.children.length > 1 && node.playedPlayerIndex === node.playerIndex) {
    return ([renderFullMoveOf(ctx, node, opts)] as MaybeVNodes)
      .concat(comments)
      .concat(opts.inline ? renderInline(ctx, parentedNode(opts.inline, node), opts) : null)
      .concat([
        h(
          'interrupt',
          renderLines(ctx, cs.slice(1), {
            parentPath: opts.parentPath + node.id,
            isMainline: false,
          }),
        ),
      ] as MaybeVNodes)
      .concat(
        renderChildrenOf(ctx, lastNodeOfFullMove, {
          parentPath: path,
          isMainline: opts.isMainline,
          truncate: opts.truncate ? opts.truncate - 1 : undefined,
          withIndex: !!comments[0],
        }) || [],
      );
  }
  return ([renderFullMoveOf(ctx, node, opts)] as MaybeVNodes)
    .concat(comments)
    .concat(opts.inline ? renderInline(ctx, parentedNode(opts.inline, node), opts) : null)
    .concat(
      renderChildrenOf(ctx, lastNodeOfFullMove, {
        parentPath: path,
        isMainline: opts.isMainline,
        truncate: opts.truncate ? opts.truncate - 1 : undefined,
        withIndex: !!comments[0],
      }) || [],
    );
}

function renderInline(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): VNode {
  return h(
    'inline',
    renderMoveAndChildrenOf(ctx, node, {
      withIndex: true,
      parentPath: opts.parentPath,
      isMainline: false,
    }),
  );
}

function renderFullMoveOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): VNode {
  const variant = ctx.ctrl.data.game.variant;
  const notation = variantClassFromKey(variant.key).getNotationStyle();
  const fullTurnNodes: Tree.ParentedNode[] = fullTurnNodesFromNode(node);
  const path = opts.parentPath + node.id,
    content: MaybeVNodes = [
      opts.withIndex || node.playedPlayerIndex === 'p1' ? moveView.renderIndex(node, true) : null,
      // TODO: the || '' are probably not correct
      moveView.combinedNotationOfTurn(
        fullTurnNodes.map(n => {
          return variantClassFromKey(variant.key).computeMoveNotation({
            san: fixCrazySan(n.san || ''),
            uci: n.uci || '',
            fen: n.fen,
            prevFen: n.parent?.fen || '',
          });
        }),
        notation,
      ),
    ];
  if (node.glyphs && ctx.showGlyphs) node.glyphs.forEach(g => content.push(moveView.renderGlyph(g)));
  return h(
    'move',
    {
      attrs: { p: path },
      class: nodeClasses(ctx, node, path),
    },
    content,
  );
}

function renderMoveOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): VNode {
  const variant = ctx.ctrl.data.game.variant;
  const path = opts.parentPath + node.id,
    content: MaybeVNodes = [
      opts.withIndex || node.ply & 1 ? moveView.renderIndex(node, true) : null,
      // TODO: the || '' are probably not correct
      variantClassFromKey(variant.key).computeMoveNotation({
        san: fixCrazySan(node.san || ''),
        uci: node.uci || '',
        fen: node.fen,
        prevFen: node.parent?.fen || '',
      }),
    ];
  if (node.glyphs && ctx.showGlyphs) node.glyphs.forEach(g => content.push(moveView.renderGlyph(g)));
  return h(
    'move',
    {
      attrs: { p: path },
      class: nodeClasses(ctx, node, path),
    },
    content,
  );
}

export default function (ctrl: AnalyseCtrl): VNode {
  const ctx: Ctx = {
    ctrl,
    truncateComments: false,
    showComputer: ctrl.showComputer() && !ctrl.retro,
    showGlyphs: !!ctrl.study || ctrl.showComputer(),
    showEval: !!ctrl.study || ctrl.showComputer(),
    currentPath: findCurrentPath(ctrl),
  };
  return h(
    'div.tview2.tview2-inline',
    {
      hook: mainHook(ctrl),
    },
    [
      ...renderInlineCommentsOf(ctx, ctrl.tree.root),
      ...(renderChildrenOf(ctx, parentedNode(ctrl.tree.root), {
        parentPath: '',
        isMainline: true,
      }) || []),
    ],
  );
}
