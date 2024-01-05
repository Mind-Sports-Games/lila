import { h, VNode } from 'snabbdom';
import { isEmpty } from 'common';
import { fixCrazySan, notationStyle } from 'stratutils';
import { moveFromNotationStyle } from 'common/notation';
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
import { enrichText, innerHTML, parentedNodes, parentedNode } from '../util';

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
    '...'
  );
}

function renderChildrenOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): MaybeVNodes | undefined {
  const cs = parentedNodes(node.children, node),
    variant = ctx.ctrl.data.game.variant,
    main = cs[0];
  if (!main) return;
  const conceal = opts.noConceal ? null : opts.conceal || ctx.concealOf(true)(opts.parentPath + main.id, main);
  if (conceal === 'hide') return;
  if (opts.isMainline) {
    //TODO multiaction. There is a lot of 'ply % 2' in the code which is not safe for multiaction games. These need to be changed as part of upgrading Step and analysis to multiaction
    const isP1 = main.ply % 2 === 1,
      commentTags = renderMainlineCommentsOf(ctx, main, conceal, true).filter(nonEmpty);
    if (!cs[1] && isEmpty(commentTags) && !main.forceVariation)
      return ((isP1 ? [moveView.renderIndex(main.ply, variant.key, false)] : []) as MaybeVNodes).concat(
        renderMoveAndChildrenOf(ctx, main, {
          parentPath: opts.parentPath,
          isMainline: true,
          conceal,
        }) || []
      );
    const mainChildren = main.forceVariation
      ? undefined
      : renderChildrenOf(ctx, main, {
          parentPath: opts.parentPath + main.id,
          isMainline: true,
          conceal,
        });
    const passOpts = {
      parentPath: opts.parentPath,
      isMainline: !main.forceVariation,
      conceal,
    };
    return (isP1 ? [moveView.renderIndex(main.ply, variant.key, false)] : ([] as MaybeVNodes))
      .concat(main.forceVariation ? [] : [renderMoveOf(ctx, main, passOpts), isP1 ? emptyMove(passOpts.conceal) : null])
      .concat([
        h(
          'interrupt',
          commentTags.concat(
            renderLines(ctx, main.forceVariation ? cs : cs.slice(1), {
              parentPath: opts.parentPath,
              isMainline: passOpts.isMainline,
              conceal,
              noConceal: !conceal,
            })
          )
        ),
      ] as MaybeVNodes)
      .concat(
        isP1 && mainChildren ? [moveView.renderIndex(main.ply, variant.key, false), emptyMove(passOpts.conceal)] : []
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

function renderLines(ctx: Ctx, nodes: Tree.ParentedNode[], opts: Opts): VNode {
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
          renderMoveAndChildrenOf(ctx, n, {
            parentPath: opts.parentPath,
            isMainline: false,
            withIndex: true,
            noConceal: opts.noConceal,
            truncate: n.comp && !treePath.contains(ctx.ctrl.path, opts.parentPath + n.id) ? 3 : undefined,
          })
        )
      );
    })
  );
}

function renderMoveOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): VNode {
  return opts.isMainline ? renderMainlineMoveOf(ctx, node, opts) : renderVariationMoveOf(ctx, node, opts);
}

function renderMainlineMoveOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): VNode {
  const path = opts.parentPath + node.id,
    classes = nodeClasses(ctx, node, path);
  if (opts.conceal) classes[opts.conceal as string] = true;
  return h(
    'move',
    {
      attrs: { p: path },
      class: classes,
    },
    moveView.renderMove(
      { variant: ctx.ctrl.data.game.variant, ...ctx },
      node,
      notationStyle(ctx.ctrl.data.game.variant.key)
    )
  );
}

function renderVariationMoveOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): VNode {
  const variant = ctx.ctrl.data.game.variant;
  const notation = notationStyle(variant.key);
  const withIndex = opts.withIndex || node.ply % 2 === 1,
    path = opts.parentPath + node.id,
    content: MaybeVNodes = [
      withIndex ? moveView.renderIndex(node.ply, variant.key, true) : null,
      // TODO: the || '' are probably not correct
      moveFromNotationStyle(notation)(
        {
          san: fixCrazySan(node.san || ''),
          uci: node.uci || '',
          fen: node.fen,
          prevFen: node.parent?.fen || '',
        },
        variant
      ),
    ],
    classes = nodeClasses(ctx, node, path);
  if (opts.conceal) classes[opts.conceal as string] = true;
  if (node.glyphs) node.glyphs.forEach(g => content.push(moveView.renderGlyph(g)));
  return h(
    'move',
    {
      attrs: { p: path },
      class: classes,
    },
    content
  );
}

function renderMoveAndChildrenOf(ctx: Ctx, node: Tree.ParentedNode, opts: Opts): MaybeVNodes {
  const path = opts.parentPath + node.id;
  if (opts.truncate === 0)
    return [
      h(
        'move',
        {
          attrs: { p: path },
        },
        [h('index', '[...]')]
      ),
    ];
  return ([renderMoveOf(ctx, node, opts)] as MaybeVNodes)
    .concat(renderInlineCommentsOf(ctx, node))
    .concat(opts.inline ? renderInline(ctx, parentedNode(opts.inline, node), opts) : null)
    .concat(
      renderChildrenOf(ctx, node, {
        parentPath: path,
        isMainline: opts.isMainline,
        noConceal: opts.noConceal,
        truncate: opts.truncate ? opts.truncate - 1 : undefined,
      }) || []
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
    })
  );
}

function renderMainlineCommentsOf(ctx: Ctx, node: Tree.Node, conceal: Conceal, withPlayerIndex: boolean): MaybeVNodes {
  if (!ctx.ctrl.showComments || isEmpty(node.comments)) return [];

  const playerIndexClass = withPlayerIndex ? (node.ply % 2 === 0 ? '.p2 ' : '.p1 ') : '';

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
    ([
      isEmpty(commentTags) ? null : h('interrupt', commentTags),
      root.ply & 1 ? moveView.renderIndex(root.ply, ctrl.data.game.variant.key, false) : null,
      root.ply & 1 ? emptyMove() : null,
    ] as MaybeVNodes).concat(
      renderChildrenOf(ctx, root, {
        parentPath: '',
        isMainline: true,
      }) || []
    )
  );
}
