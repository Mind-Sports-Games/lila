import { h, VNode } from 'snabbdom';
import { bind, dataIcon, iconTag, richHTML, allowServerEvalForVariant } from '../util';
import { view as memberView } from './studyMembers';
import { view as chapterView } from './studyChapters';
import { view as chapterNewFormView } from './chapterNewForm';
import { view as chapterEditFormView } from './chapterEditForm';
import * as commentForm from './commentForm';
import * as glyphForm from './studyGlyph';
import { view as inviteFormView } from './inviteForm';
import { view as studyFormView } from './studyForm';
import { view as studyShareView } from './studyShare';
import { view as multiBoardView } from './multiBoard';
import { view as notifView } from './notif';
import { view as tagsView } from './studyTags';
import { view as topicsView, formView as topicsFormView } from './topics';
import { view as serverEvalView } from './serverEval';
import * as practiceView from './practice/studyPracticeView';
import { playButtons as gbPlayButtons, overrideButton as gbOverrideButton } from './gamebook/gamebookButtons';
import { view as descView } from './description';
import { rounds as relayTourRounds } from './relay/relayTourView';
import AnalyseCtrl from '../ctrl';
import { StudyCtrl, Tab, ToolTab } from './interfaces';
import { MaybeVNodes } from '../interfaces';

interface ToolButtonOpts {
  ctrl: StudyCtrl;
  tab: ToolTab;
  hint: string;
  icon: VNode;
  onClick?: () => void;
  count?: number | string;
}

function toolButton(opts: ToolButtonOpts): VNode {
  return h(
    'span.' + opts.tab,
    {
      attrs: { title: opts.hint },
      class: { active: opts.tab === opts.ctrl.vm.toolTab() },
      hook: bind(
        'mousedown',
        () => {
          if (opts.onClick) opts.onClick();
          opts.ctrl.vm.toolTab(opts.tab);
        },
        opts.ctrl.redraw,
      ),
    },
    [opts.count ? h('count.data-count', { attrs: { 'data-count': opts.count } }) : null, opts.icon],
  );
}

function buttons(root: AnalyseCtrl): VNode {
  const ctrl: StudyCtrl = root.study!,
    canContribute = ctrl.members.canContribute(),
    showSticky = ctrl.data.features.sticky && (canContribute || (ctrl.vm.behind && ctrl.isUpdatedRecently())),
    noarg = root.trans.noarg;
  return h('div.study__buttons', [
    h('div.left-buttons.tabs-horiz', [
      // distinct classes (sync, write) allow snabbdom to differentiate buttons
      showSticky
        ? h(
            'a.mode.sync',
            {
              attrs: { title: noarg('allSyncMembersRemainOnTheSamePosition') },
              class: { on: ctrl.vm.mode.sticky },
              hook: bind('click', ctrl.toggleSticky),
            },
            [ctrl.vm.behind ? h('span.behind', '' + ctrl.vm.behind) : h('i.is'), 'SYNC'],
          )
        : null,
      ctrl.members.canContribute()
        ? h(
            'a.mode.write',
            {
              attrs: { title: noarg('shareChanges') },
              class: { on: ctrl.vm.mode.write },
              hook: bind('click', ctrl.toggleWrite),
            },
            [h('i.is'), 'REC'],
          )
        : null,
      toolButton({
        ctrl,
        tab: 'tags',
        hint: noarg('pgnTags'),
        icon: iconTag('o'),
      }),
      toolButton({
        ctrl,
        tab: 'comments',
        hint: noarg('commentThisPosition'),
        icon: iconTag('c'),
        onClick() {
          ctrl.commentForm.start(ctrl.vm.chapterId, root.path, root.node);
        },
        count: (root.node.comments || []).length,
      }),
      canContribute
        ? toolButton({
            ctrl,
            tab: 'glyphs',
            hint: noarg('annotateWithGlyphs'),
            icon: h('i.glyph-icon'),
            count: (root.node.glyphs || []).length,
          })
        : null,
      allowServerEvalForVariant(ctrl.data.chapter.setup.variant.key)
        ? toolButton({
            ctrl,
            tab: 'serverEval',
            hint: noarg('computerAnalysis'),
            icon: iconTag(''),
            count: root.data.analysis && '✓',
          })
        : null,
      toolButton({
        ctrl,
        tab: 'multiBoard',
        hint: 'Multiboard',
        icon: iconTag(''),
      }),
      toolButton({
        ctrl,
        tab: 'share',
        hint: noarg('shareAndExport'),
        icon: iconTag('$'),
      }),
      !ctrl.relay
        ? h('span.help', {
            attrs: { title: 'Need help? Get the tour!', 'data-icon': '' },
            hook: bind('click', ctrl.startTour),
          })
        : null,
    ]),
    h('div.right', [gbOverrideButton(ctrl)]),
  ]);
}

function metadata(ctrl: StudyCtrl): VNode {
  const d = ctrl.data,
    credit = ctrl.relay?.data.tour.credit,
    title = `${d.name}: ${ctrl.currentChapter().name}`;
  return h('div.study__metadata', [
    h('h2', [
      h('span.name', { attrs: { title } }, [
        title,
        credit ? h('span.credit', { hook: richHTML(credit, false) }) : undefined,
      ]),
      h(
        'span.liking.text',
        {
          class: { liked: d.liked },
          attrs: {
            'data-icon': d.liked ? '' : '',
            title: ctrl.trans.noarg('like'),
          },
          hook: bind('click', ctrl.toggleLike),
        },
        '' + d.likes,
      ),
    ]),
    topicsView(ctrl),
    tagsView(ctrl),
  ]);
}

export function side(ctrl: StudyCtrl): VNode {
  const activeTab = ctrl.vm.tab(),
    tourShow = ctrl.relay?.tourShow;

  const makeTab = (key: Tab, name: string) =>
    h(
      'span.' + key,
      {
        class: { active: !tourShow?.active && activeTab === key },
        hook: bind(
          'mousedown',
          () => {
            tourShow?.disable();
            ctrl.vm.tab(key);
          },
          ctrl.redraw,
        ),
      },
      name,
    );

  const tourTab =
    tourShow &&
    h(
      'span.relay-tour.text',
      {
        class: { active: tourShow.active },
        hook: bind(
          'mousedown',
          () => {
            tourShow.active = true;
          },
          ctrl.redraw,
        ),
        attrs: {
          'data-icon': '',
        },
      },
      'Broadcast',
    );

  const tabs = h('div.tabs-horiz', [
    tourTab,
    makeTab('chapters', ctrl.trans.plural(ctrl.relay ? 'nbGames' : 'nbChapters', ctrl.chapters.size())),
    !tourTab || ctrl.members.canContribute() || ctrl.data.admin
      ? makeTab('members', ctrl.trans.plural('nbMembers', ctrl.members.size()))
      : null,
    ctrl.members.isOwner()
      ? h(
          'span.more',
          {
            hook: bind('click', () => ctrl.form.open(!ctrl.form.open()), ctrl.redraw),
          },
          [iconTag('[')],
        )
      : null,
  ]);

  const content = tourShow?.active ? relayTourRounds(ctrl) : (activeTab === 'members' ? memberView : chapterView)(ctrl);

  return h('div.study__side', [tabs, content]);
}

export function contextMenu(ctrlAna: AnalyseCtrl, path: Tree.Path, node: Tree.Node): VNode[] {
  const ctrl = ctrlAna.study!;
  const canComment = ctrlAna.controlConfig.isNodeCommentable?.(node) ?? true;
  const canGlyph = ctrlAna.controlConfig.isNodeAnnotatable?.(node) ?? true;
  return ctrl.vm.mode.write
    ? [
        canComment
          ? h(
              'a',
              {
                attrs: dataIcon('c'),
                hook: bind('click', () => {
                  ctrl.vm.toolTab('comments');
                  ctrl.commentForm.start(ctrl.currentChapter()!.id, path, node);
                }),
              },
              ctrl.trans.noarg('commentThisMove'),
            )
          : null,
        canGlyph
          ? h(
              'a.glyph-icon',
              {
                hook: bind('click', () => {
                  ctrl.vm.toolTab('glyphs');
                  ctrl.userJump(path);
                }),
              },
              ctrl.trans.noarg('annotateWithGlyphs'),
            )
          : null,
      ]
    : [];
}

export function overboard(ctrl: StudyCtrl) {
  if (ctrl.chapters.newForm.vm.open) return chapterNewFormView(ctrl.chapters.newForm);
  if (ctrl.chapters.editForm.current()) return chapterEditFormView(ctrl.chapters.editForm);
  if (ctrl.members.inviteForm.open()) return inviteFormView(ctrl.members.inviteForm);
  if (ctrl.topics.open()) return topicsFormView(ctrl.topics, ctrl.members.myId);
  if (ctrl.form.open()) return studyFormView(ctrl.form);
  return undefined;
}

export function underboard(ctrlAna: AnalyseCtrl): MaybeVNodes {
  if (ctrlAna.embed) return [];
  if (ctrlAna.studyPractice) return practiceView.underboard(ctrlAna.study!);
  const ctrl = ctrlAna.study!,
    toolTab = ctrl.vm.toolTab();
  if (ctrl.gamebookPlay()) return [gbPlayButtons(ctrlAna), descView(ctrl, true), descView(ctrl, false), metadata(ctrl)];
  let panel;
  switch (toolTab) {
    case 'tags':
      panel = metadata(ctrl);
      break;
    case 'comments': {
      const canComment = ctrlAna.controlConfig.isNodeCommentable?.(ctrlAna.node) ?? true;
      if (!ctrl.vm.mode.write)
        panel = commentForm.viewDisabled(
          ctrlAna,
          ctrl.members.canContribute() ? 'Press REC to comment moves' : 'Only the study members can comment on moves',
        );
      else if (!canComment) panel = commentForm.viewDisabled(ctrlAna, 'Comments are only visible on dice roll moves');
      else panel = commentForm.view(ctrlAna);
      break;
    }
    case 'glyphs': {
      const canGlyph = ctrlAna.controlConfig.isNodeAnnotatable?.(ctrlAna.node) ?? true;
      panel = ctrlAna.path
        ? ctrl.vm.mode.write
          ? canGlyph
            ? glyphForm.view(ctrl.glyphForm)
            : glyphForm.viewDisabled('Glyphs cannot be applied to automatic actions')
          : glyphForm.viewDisabled('Press REC to annotate moves')
        : glyphForm.viewDisabled('Select a move to annotate');
      break;
    }
    case 'serverEval':
      panel = serverEvalView(ctrl.serverEval);
      break;
    case 'share':
      panel = studyShareView(ctrl.share);
      break;
    case 'multiBoard':
      panel = multiBoardView(ctrl.multiBoard, ctrl);
      break;
  }
  return [notifView(ctrl.notif), descView(ctrl, true), descView(ctrl, false), buttons(ctrlAna), panel];
}
