import view from './view';

import { init, VNode, classModule, attributesModule } from 'snabbdom';

import { Ctrl } from './interfaces';

const patch = init([classModule, attributesModule]);

export function PlayStrategyTournamentCalendar(element: HTMLElement, env: any) {
  // enrich tournaments
  env.data.tournaments.forEach((t: any) => {
    if (!t.bounds) {
      // Scheduled tournaments carry a random 0-59s start jitter (Tournament.scheduleAs) so they
      // do not all start on the same tick. Bars are positioned to the minute, so drop those
      // seconds before deriving bounds: otherwise the jitter decides at random whether a 24h
      // yearly still overlaps the midnight arena that follows it, and lane packing wanders.
      const start = new Date(t.startsAt);
      start.setSeconds(0, 0);
      t.bounds = {
        start,
        end: new Date(start.getTime() + t.minutes * 60 * 1000),
      };
    }
  });

  const ctrl: Ctrl = {
    data: env.data,
    trans: playstrategy.trans(env.i18n),
  };

  let vnode: VNode;
  function redraw() {
    vnode = patch(vnode || element, view(ctrl));
  }

  redraw();
}

(window as any).PlayStrategyTournamentCalendar = PlayStrategyTournamentCalendar; // esbuild
