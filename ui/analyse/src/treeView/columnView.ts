import { h, VNode } from 'snabbdom';
import { isEmpty } from 'common';
import { fixCrazySan } from 'stratutils';
import { path as treePath, ops as treeOps } from 'tree';
import * as moveView from '../moveView';
import { authorText as commentAuthorText } from '../study/studyComments';
import AnalyseCtrl from '../ctrl';
import { MaybeVNodes, ConcealOf, Conceal } from '../interfaces';
import {
  nonEmpty,
  mainHook,
  nodeClasses,
  findCurrentPath,
  renderInlineCommentsOf,
  truncateComment,
  retroLine,
  Ctx as BaseCtx,
  Opts as BaseOpts,
} from './treeView';
import { enrichText, innerHTML, parentedNodes, parentedNode, fullTurnNodesFromNode } from '../util';
import { variantClassFromKey } from 'stratops/variants/util';

interface Ctx extends BaseCtx {
  concealOf: ConcealOf;
}
interface Opts extends BaseOpts {
  conceal?: Conceal;
  noConceal?: boolean;
}

function emptyMove(conceal?: Conceal): VNode {
  const c: { conceal?: true; hide?: true } = {};
  if (conceal) c[conceal] = true;
  return h(
    'move.empty',
    {
      class: c,
    },
    '...',
  );
}

function renderChildrenOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): MaybeVNodes | undefined {
  const cs = parentedNodes(node.children, node),
    main = cs[0];
  if (!main) return;
  const conceal = opts.noConceal ? null : opts.conceal || ctx.concealOf(true)(opts.parentPath + main.id, main);
  if (conceal === 'hide') return;
  if (opts.isMainline) {
    // isContinuation: main is a mid-turn continuation (same player played node and plays main).
    // Used to suppress the P1 turn index before main.
    const isContinuation = !!node.parent && node.playedPlayerIndex === node.playerIndex;
    const isP1 = main.playedPlayerIndex === 'p1' && !isContinuation;
    const commentTags = renderMainlineCommentsOf(ctx, main, conceal, true).filter(nonEmpty);
    if (!cs[1] && isEmpty(commentTags) && !main.forceVariation)
      return ((isP1 ? [moveView.renderIndex(main, false)] : []) as MaybeVNodes).concat(
        renderMoveAndChildrenOf(ctx, main, {
          parentPath: opts.parentPath,
          isMainline: true,
          conceal,
        }) || [],
      );
    // Collect all nodes of main's full turn so we can render complete turn notation
    // and recurse into the next turn from the correct end-of-turn node.
    const nodesOfFullTurn = main.forceVariation ? [main] : fullTurnNodesFromNode(main);
    const lastNodeOfFullMove = nodesOfFullTurn[nodesOfFullTurn.length - 1];
    const fullTurnPath = opts.parentPath + nodesOfFullTurn.map(n => n.id).join('');
    // If the full turn itself has inner branching (e.g. same first move but alternative second
    // moves), those variations must appear in the interrupt alongside any outer ones and comments.
    const innerBranchingIdx = main.forceVariation
      ? -1
      : nodesOfFullTurn.findIndex(n => n.children.length > 1 && n.playedPlayerIndex === n.playerIndex);
    const innerVariationsLine =
      innerBranchingIdx >= 0
        ? (() => {
            const branchingNode = nodesOfFullTurn[innerBranchingIdx];
            const branchingCs = parentedNodes(branchingNode.children, branchingNode);
            const branchingParentPath =
              opts.parentPath +
              nodesOfFullTurn
                .slice(0, innerBranchingIdx + 1)
                .map(n => n.id)
                .join('');
            const prefixForVariations = nodesOfFullTurn.slice(0, innerBranchingIdx + 1);
            return renderLines(
              ctx,
              branchingCs.slice(1),
              { parentPath: branchingParentPath, isMainline: false, conceal, noConceal: !conceal },
              prefixForVariations,
            );
          })()
        : null;
    const mainChildren = main.forceVariation
      ? undefined
      : renderChildrenOf(ctx, lastNodeOfFullMove, {
          parentPath: fullTurnPath,
          isMainline: true,
          conceal,
        });
    const passOpts = {
      parentPath: opts.parentPath,
      isMainline: !main.forceVariation,
      conceal,
    };
    return (isP1 ? [moveView.renderIndex(main, false)] : ([] as MaybeVNodes))
      .concat(
        main.forceVariation ? [] : [renderFullMoveOf(ctx, main, passOpts), isP1 ? emptyMove(passOpts.conceal) : null],
      )
      .concat([
        h(
          'interrupt',
          commentTags
            .concat(
              renderLines(ctx, main.forceVariation ? cs : cs.slice(1), {
                parentPath: opts.parentPath,
                isMainline: passOpts.isMainline,
                conceal,
                noConceal: !conceal,
              }),
            )
            .concat(innerVariationsLine ? [innerVariationsLine] : []),
        ),
      ] as MaybeVNodes)
      .concat(
        !mainChildren
          ? []
          : lastNodeOfFullMove.playerIndex === 'p2'
            ? [moveView.renderIndex(lastNodeOfFullMove, false), emptyMove(passOpts.conceal)]
            : [],
      )
      .concat(mainChildren || []);
  }
  if (!cs[1]) return renderMoveAndChildrenOf(ctx, main, opts);
  return renderInlined(ctx, cs, opts) || [renderLines(ctx, cs, opts)];
}

function renderInlined(ctx: Ctx, nodes: Tree.ParentedNode[], opts: Opts): MaybeVNodes | undefined {
  // only 2 branches
  if (!nodes[1] || nodes[2]) return;
  // only if second branch has no sub-branches
  if (treeOps.hasBranching(nodes[1], 6)) return;
  return renderMoveAndChildrenOf(ctx, nodes[0], {
    parentPath: opts.parentPath,
    isMainline: false,
    noConceal: opts.noConceal,
    inline: nodes[1],
  });
}

function renderLines(
  ctx: Ctx,
  nodes: Tree.ParentedNode[],
  opts: Opts,
  turnPrefixNodes: Tree.ParentedNode[] = [],
): VNode {
  return h(
    'lines',
    {
      class: { single: !nodes[1] },
    },
    nodes.map(n => {
      return (
        retroLine(ctx, n) ||
        h(
          'line',
          renderMoveAndChildrenOf(
            ctx,
            n,
            {
              parentPath: opts.parentPath,
              isMainline: false,
              withIndex: true,
              noConceal: opts.noConceal,
              truncate: n.comp && !treePath.contains(ctx.ctrl.path, opts.parentPath + n.id) ? 3 : undefined,
            },
            turnPrefixNodes,
          ),
        )
      );
    }),
  );
}

function renderFullMoveOf(
  ctx: Ctx,
  node: Tree.ParentedNode,
  opts: Opts,
  turnPrefixNodes: Tree.ParentedNode[] = [],
): VNode {
  return opts.isMainline
    ? renderMainlineFullMoveOf(ctx, node, opts)
    : renderVariationFullMoveOf(ctx, node, opts, turnPrefixNodes);
}

function renderMainlineFullMoveOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): VNode {
  const path = opts.parentPath + node.id,
    fullTurnPath =
      opts.parentPath +
      fullTurnNodesFromNode(node)
        .map(n => n.id)
        .join(''),
    classes = nodeClasses(ctx, node, path, fullTurnPath);
  if (opts.conceal) classes[opts.conceal as string] = true;
  return h(
    'move',
    {
      attrs: { p: path },
      class: classes,
    },
    moveView.renderFullMove(
      { variant: ctx.ctrl.data.game.variant, ...ctx },
      node,
      variantClassFromKey(ctx.ctrl.data.game.variant.key).getNotationStyle(),
    ),
  );
}

function renderVariationFullMoveOf(
  ctx: Ctx,
  node: Tree.ParentedNode,
  opts: Opts,
  turnPrefixNodes: Tree.ParentedNode[] = [],
): VNode {
  // turnPrefixNodes: shared nodes earlier in the same turn (e.g. dice roll + first move when
  // this variation branches at the second move). Prepended so the full turn notation is correct.
  const fullTurnNodes: Tree.ParentedNode[] = [...turnPrefixNodes, ...fullTurnNodesFromNode(node)];
  const variant = ctx.ctrl.data.game.variant;
  const variantClass = variantClassFromKey(variant.key);
  const notation = variantClass.getNotationStyle();
  const withIndex = opts.withIndex || node.playedPlayerIndex === 'p1',
    path = opts.parentPath + node.id,
    fullTurnPath =
      opts.parentPath +
      fullTurnNodesFromNode(node)
        .map(n => n.id)
        .join(''),
    content: MaybeVNodes = [
      withIndex ? moveView.renderIndex(node, true) : null,
      // TODO: the || '' are probably not correct
      moveView.combinedNotationOfTurn(
        fullTurnNodes.map(n => {
          return variantClass.computeMoveNotation({
            san: fixCrazySan(n.san || ''),
            uci: n.uci || '',
            fen: n.fen,
            prevFen: n.parent?.fen || '',
          });
        }),
        notation,
      ),
    ],
    classes = nodeClasses(ctx, node, path, fullTurnPath);
  if (opts.conceal) classes[opts.conceal as string] = true;
  if (node.glyphs) node.glyphs.forEach(g => content.push(moveView.renderGlyph(g)));
  return h(
    'move',
    {
      attrs: { p: path },
      class: classes,
    },
    content,
  );
}

function renderMoveAndChildrenOf(
  ctx: Ctx,
  node: Tree.ParentedNode,
  opts: Opts,
  turnPrefixNodes: Tree.ParentedNode[] = [],
): MaybeVNodes {
  const nodesOfFullTurn = fullTurnNodesFromNode(node);
  const lastNodeOfFullMove = nodesOfFullTurn[nodesOfFullTurn.length - 1];
  const path = opts.parentPath + nodesOfFullTurn.map(n => n.id).join('');
  if (opts.truncate === 0)
    return [
      h(
        'move',
        {
          attrs: { p: path },
        },
        [h('index', '[...]')],
      ),
    ];
  const conceal = opts.noConceal ? null : opts.conceal || ctx.concealOf(true)(opts.parentPath + node.id, node);
  const commentTags = renderMainlineCommentsOf(ctx, node, conceal, true).filter(nonEmpty);
  const isP1 = node.playedPlayerIndex === 'p1';
  // Find the first node within the full turn that has multiple children (a branching point).
  // This handles both branching at the turn root (e.g. different first moves after a dice roll)
  // and branching mid-turn (e.g. same first move but different second move).
  const branchingNodeIdx = nodesOfFullTurn.findIndex(
    n => n.children.length > 1 && n.playedPlayerIndex === n.playerIndex,
  );
  if (branchingNodeIdx >= 0) {
    const branchingNode = nodesOfFullTurn[branchingNodeIdx];
    const branchingCs = parentedNodes(branchingNode.children, branchingNode);
    // Path to the branching node itself — used as parentPath for its children's variations.
    const branchingParentPath =
      opts.parentPath +
      nodesOfFullTurn
        .slice(0, branchingNodeIdx + 1)
        .map(n => n.id)
        .join('');
    // Prefix for variations: the shared nodes up to and including the branching node.
    // Variations need this to display the full turn notation (e.g. "65: 8/3 7/1" not just "65: 7/1").
    const prefixForVariations = [...turnPrefixNodes, ...nodesOfFullTurn.slice(0, branchingNodeIdx + 1)];
    return ([renderFullMoveOf(ctx, node, opts, turnPrefixNodes)] as MaybeVNodes)
      .concat(renderInlineCommentsOf(ctx, node))
      .concat(opts.inline ? renderInline(ctx, parentedNode(opts.inline, node), opts) : null)
      .concat([
        h(
          'interrupt',
          commentTags.concat(
            renderLines(
              ctx,
              branchingCs.slice(1),
              {
                parentPath: branchingParentPath,
                isMainline: false,
                conceal,
                noConceal: !conceal,
              },
              prefixForVariations,
            ),
          ),
        ),
      ] as MaybeVNodes)
      .concat(
        opts.isMainline && isP1 && lastNodeOfFullMove.children.length > 0
          ? [moveView.renderIndex(node, false), emptyMove(opts.conceal)]
          : [],
      )
      .concat(
        renderChildrenOf(ctx, lastNodeOfFullMove, {
          parentPath: path,
          isMainline: opts.isMainline,
          noConceal: opts.noConceal,
          truncate: opts.truncate ? opts.truncate - 1 : undefined,
        }) || [],
      );
  }
  return ([renderFullMoveOf(ctx, node, opts, turnPrefixNodes)] as MaybeVNodes)
    .concat(renderInlineCommentsOf(ctx, node))
    .concat(opts.inline ? renderInline(ctx, parentedNode(opts.inline, node), opts) : null)
    .concat(
      renderChildrenOf(ctx, lastNodeOfFullMove, {
        parentPath: path,
        isMainline: opts.isMainline,
        noConceal: opts.noConceal,
        truncate: opts.truncate ? opts.truncate - 1 : undefined,
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
      noConceal: opts.noConceal,
      truncate: opts.truncate,
    }),
  );
}

function renderMainlineCommentsOf(ctx: Ctx, node: Tree.Node, conceal: Conceal, withPlayerIndex: boolean): MaybeVNodes {
  if (!ctx.ctrl.showComments || isEmpty(node.comments)) return [];

  const playerIndexClass = withPlayerIndex ? `.${node.playedPlayerIndex} ` : '';

  return node.comments!.map(comment => {
    if (comment.by === 'playstrategy' && !ctx.showComputer) return;
    let sel = 'comment' + playerIndexClass;
    if (comment.text.startsWith('Inaccuracy.')) sel += '.inaccuracy';
    else if (comment.text.startsWith('Mistake.')) sel += '.mistake';
    else if (comment.text.startsWith('Blunder.')) sel += '.blunder';
    if (conceal) sel += '.' + conceal;
    const by = node.comments![1] ? `<span class="by">${commentAuthorText(comment.by)}</span>` : '',
      truncated = truncateComment(comment.text, 400, ctx);
    return h(sel, {
      hook: innerHTML(truncated, text => by + enrichText(text)),
    });
  });
}

const emptyConcealOf: ConcealOf = function () {
  return function () {
    return null;
  };
};

export default function (ctrl: AnalyseCtrl, concealOf?: ConcealOf): VNode {
  const root = parentedNode(ctrl.tree.root);
  const ctx: Ctx = {
    ctrl,
    truncateComments: !ctrl.embed,
    concealOf: concealOf || emptyConcealOf,
    showComputer: ctrl.showComputer() && !ctrl.retro,
    showGlyphs: !!ctrl.study || ctrl.showComputer(),
    showEval: ctrl.showComputer(),
    currentPath: findCurrentPath(ctrl),
  };
  const commentTags = renderMainlineCommentsOf(ctx, root, false, false);
  return h(
    'div.tview2.tview2-column',
    {
      hook: mainHook(ctrl),
    },
    (
      [
        isEmpty(commentTags) ? null : h('interrupt', commentTags),
        root.ply & 1 ? moveView.renderIndex(root, false) : null,
        root.ply & 1 ? emptyMove() : null,
      ] as MaybeVNodes
    ).concat(
      renderChildrenOf(ctx, root, {
        parentPath: '',
        isMainline: true,
      }) || [],
    ),
  );
}
