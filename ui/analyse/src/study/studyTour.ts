import AnalyseCtrl from '../ctrl';
import { Tab } from './interfaces';

export function study(ctrl: AnalyseCtrl) {
  if (!ctrl.study?.data.chapter.gamebook)
    playstrategy.loadScriptCJS('javascripts/study/tour.js').then(() => {
      window.playstrategy['studyTour']({
        userId: ctrl.opts.userId,
        isContrib: ctrl.study!.members.canContribute(),
        isOwner: ctrl.study!.members.isOwner(),
        setTab: (tab: Tab) => {
          ctrl.study!.vm.tab(tab);
          ctrl.redraw();
        },
      });
    });
}

export function chapter(setTab: (tab: string) => void) {
  playstrategy.loadScriptCJS('javascripts/study/tour-chapter.js').then(() => {
    window.playstrategy['studyTourChapter']({
      setTab,
    });
  });
}
