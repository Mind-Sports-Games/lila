import { h, VNode, Hooks } from 'snabbdom';
import TournamentController from '../ctrl';
import { dataIcon } from './util';

const startClock = (time: number): Hooks => ({
  insert: vnode => playstrategy.clockWidget(vnode.elm as HTMLElement, { time }),
});

const oneDayInSeconds = 60 * 60 * 24;

function hasFreq(freq: any, d: any) {
  return d.schedule && d.schedule.freq === freq;
}

function clock(d: any): VNode | undefined {
  if (d.isFinished) return;
  if (d.secondsToFinish)
    if (d.medley && d.secondsToFinish != d.secondsToFinishInterval) {
      return h('div.clock', [
        h('div.time.medley-interval', {
          hook: startClock(d.secondsToFinish),
        }),
        h('div.medley-interval.medley-extra-clock', [
          h('span.time', '('),
          h('span.time.medley-interval-time', {
            hook: startClock(d.secondsToFinishInterval),
          }),
          h('span.time', ')'),
        ]),
      ]);
    } else {
      return h('div.clock', [
        h('div.time', {
          hook: startClock(d.secondsToFinish),
        }),
      ]);
    }
  if (d.secondsToStart) {
    if (d.secondsToStart > oneDayInSeconds)
      return h('div.clock', [
        h('time.timeago.shy', {
          attrs: {
            title: new Date(d.startsAt).toLocaleString(),
            datetime: Date.now() + d.secondsToStart * 1000,
          },
          hook: {
            insert(vnode) {
              (vnode.elm as HTMLElement).setAttribute('datetime', '' + (Date.now() + d.secondsToStart * 1000));
            },
          },
        }),
      ]);
    return h('div.clock.clock-created', [
      h('span.shy', 'Starting in'),
      h('span.time.text', {
        hook: startClock(d.secondsToStart),
      }),
    ]);
  }
  return undefined;
}

function image(d: any): VNode | undefined {
  if (d.isFinished) return;
  if (hasFreq('shield', d) || hasFreq('marathon', d)) return;
  const s = d.spotlight;
  if (s && s.iconImg)
    return h('img.img', {
      attrs: { src: playstrategy.assetUrl('images/' + s.iconImg) },
    });
  return h('i.img', {
    attrs: dataIcon((s && s.iconFont) || 'g'),
  });
}

function title(ctrl: TournamentController) {
  const d = ctrl.data;
  if (hasFreq('marathon', d)) return h('h1', [h('i.fire-trophy', '\\'), d.fullName]);
  if (hasFreq('shield', d))
    return h('h1', [
      h(
        'a.shield-trophy',
        {
          attrs: { href: '/tournament/shields' },
        },
        d.perf.icon,
      ),
      d.fullName,
    ]);
  return h(
    'h1',
    (d.greatPlayer
      ? [
          h(
            'a',
            {
              attrs: {
                href: d.greatPlayer.url,
                target: '_blank',
                rel: 'noopener',
              },
            },
            d.greatPlayer.name,
          ),
          ' Arena',
        ]
      : [d.fullName]
    ).concat(d.private ? [' ', h('span', { attrs: dataIcon('a') })] : []),
  );
}

export default function (ctrl: TournamentController): VNode {
  return h('div.tour__main__header', [image(ctrl.data), title(ctrl), clock(ctrl.data)]);
}
