import * as button from '../view/button';
import * as game from 'game';
import RoundController from '../ctrl';
import { bind, justIcon } from '../util';
import { ClockElements, ClockController, Millis } from './clockCtrl';
import { h, Hooks } from 'snabbdom';
import { Position } from '../interfaces';

const otherEmerg = (millis: Millis, clock: ClockController) => millis < clock.emergMs;
const byoyomiEmerg = (millis: Millis, clock: ClockController, playerIndex: PlayerIndex) =>
  !!clock.byoyomiData &&
  ((millis < clock.emergMs && !clock.isUsingByo(playerIndex)) ||
    (clock.isUsingByo(playerIndex) && millis < clock.byoyomiData.byoEmergeS * 1000));
const isEmerg = (millis: Millis, clock: ClockController, playerIndex: PlayerIndex) =>
  clock.byoyomiData ? byoyomiEmerg(millis, clock, playerIndex) : otherEmerg(millis, clock);

export function renderClock(ctrl: RoundController, player: game.Player, position: Position) {
  const clock = ctrl.clock!,
    millis = clock.millisOf(player.playerIndex),
    isPlayer = ctrl.data.player.playerIndex === player.playerIndex,
    isRunning = player.playerIndex === clock.times.activePlayerIndex,
    showDelayTime = clock.countdownDelay !== undefined && !clock.goneBerserk[player.playerIndex];
  const update = (el: HTMLElement) => {
    const els = clock.elements[player.playerIndex],
      millis = clock.millisOf(player.playerIndex),
      delayMillis = clock.delayMillisOf(player.playerIndex),
      isRunning = player.playerIndex === clock.times.activePlayerIndex;
    els.time = el;
    els.clock = el.parentElement!;
    el.innerHTML = formatClockTime(
      millis,
      delayMillis,
      clock.showTenths(millis),
      isRunning,
      showDelayTime,
      clock.opts.nvui,
    );
    const cl = els.time.classList;
    if (clock.isInDelay(player.playerIndex) && isRunning) {
      cl.remove('notindelay');
      cl.add('indelay');
    } else if (cl.contains('indelay') && isRunning) {
      clock.emergSound.lowtime(); //move to ctrl?
      cl.remove('indelay');
      cl.add('notindelay');
    } else if (cl.contains('notindelay') && !isRunning) cl.remove('notindelay');
  };
  const timeHook: Hooks = {
    insert: vnode => update(vnode.elm as HTMLElement),
    postpatch: (_, vnode) => update(vnode.elm as HTMLElement),
  };
  return h(
    'div.rclock.rclock-' + position,
    {
      class: {
        outoftime: millis <= 0,
        running: isRunning,
        emerg: isEmerg(millis, clock, player.playerIndex),
      },
    },
    clock.opts.nvui
      ? [
          h('div.clock-byo', [
            h('div.time', {
              attrs: { role: 'timer' },
              hook: timeHook,
            }),
          ]),
        ]
      : [
          clock.showBar && game.bothPlayersHavePlayed(ctrl.data) ? showBar(ctrl, player.playerIndex) : undefined,
          h('div.clock-byo', [
            h('div.time', {
              class: {
                hour: millis > 3600 * 1000,
              },
              hook: timeHook,
            }),
            renderByoyomiTime(clock, player.playerIndex, ctrl.goneBerserk[player.playerIndex]),
          ]),
          renderBerserk(ctrl, player.playerIndex, position),
          isPlayer ? goBerserk(ctrl) : button.moretime(ctrl),
          tourRank(ctrl, player.playerIndex, position),
        ],
  );
}

const pad2 = (num: number): string => (num < 10 ? '0' : '') + num;
const sepHigh = '<sep>:</sep>';
const sepLow = '<sep class="low">:</sep>';

const renderByoyomiTime = (clock: ClockController, playerIndex: PlayerIndex, berserk = false) => {
  if (!clock.byoyomiData) return null;
  const byoyomi = clock.byoyomiData.byoyomi;
  const periods = clock.byoyomiData.totalPeriods - clock.byoyomiData.curPeriods[playerIndex];
  const perStr = periods > 1 ? `(${periods}x)` : '';
  return h(
    `div.byoyomi.per${periods}`,
    { berserk: berserk },
    !berserk && byoyomi && periods ? `|${byoyomi}s${perStr}` : '',
  );
};

function formatClockTime(
  time: Millis,
  delay: Millis,
  showTenths: boolean,
  isRunning: boolean,
  showDelayTime: boolean,
  nvui: boolean,
) {
  const displayDate = new Date(Math.max(0, time - delay));
  const tickDate = new Date(time);
  if (nvui)
    return (
      (time >= 3600000 ? Math.floor(time / 3600000) + 'H:' : '') +
      displayDate.getUTCMinutes() +
      'M:' +
      displayDate.getUTCSeconds() +
      'S'
    );
  const displayMillis = displayDate.getUTCMilliseconds(),
    tickMillis = tickDate.getUTCMilliseconds(),
    sep = isRunning && tickMillis < 500 ? sepLow : sepHigh,
    baseStr = pad2(displayDate.getUTCMinutes()) + sep + pad2(displayDate.getUTCSeconds()),
    delayString = '<delay>' + pad2(Math.ceil(delay / 1000)) + '</delay>';

  if (time >= 3600000) {
    const hours = pad2(Math.floor(time / 3600000));
    return hours + sepHigh + baseStr;
  } else if (showTenths) {
    let tenthsStr = Math.floor(displayMillis / 100).toString();
    if (!isRunning && time < 1000) {
      tenthsStr += '<huns>' + (Math.floor(displayMillis / 10) % 10) + '</huns>';
    }
    let delayStr = '';
    if (showDelayTime) delayStr += delayString;
    return baseStr + '<tenths><sep>.</sep>' + tenthsStr + '</tenths>' + delayStr;
  } else if (showDelayTime) {
    return baseStr + delayString;
  } else {
    return baseStr;
  }
}

function showBar(ctrl: RoundController, playerIndex: PlayerIndex) {
  const clock = ctrl.clock!;
  const update = (el: HTMLElement) => {
    if (el.animate !== undefined) {
      let anim = clock.elements[playerIndex].barAnim;
      if (
        (playerIndex === clock.times.activePlayerIndex && clock.isUsingByo(playerIndex)) ||
        anim === undefined ||
        !anim.effect ||
        (anim.effect as KeyframeEffect).target !== el
      ) {
        anim = el.animate([{ transform: 'scale(1)' }, { transform: 'scale(0, 1)' }], {
          duration: clock.barTime[playerIndex],
          fill: 'both',
        });
        clock.elements[playerIndex].barAnim = anim;
      }
      const remaining = clock.millisOf(playerIndex);
      anim.currentTime = clock.barTime[playerIndex] - remaining;
      if (playerIndex === clock.times.activePlayerIndex) {
        // Calling play after animations finishes restarts anim
        if (remaining > 0) anim.play();
      } else anim.pause();
    } else {
      clock.elements[playerIndex].bar = el;
      el.style.transform = 'scale(' + clock.timeRatio(clock.millisOf(playerIndex), playerIndex) + ',1)';
    }
  };
  return h('div.bar', {
    class: { berserk: !!ctrl.goneBerserk[playerIndex] },
    hook: {
      insert: vnode => update(vnode.elm as HTMLElement),
      postpatch: (_, vnode) => update(vnode.elm as HTMLElement),
    },
  });
}

export function updateElements(clock: ClockController, els: ClockElements, millis: Millis, playerIndex: PlayerIndex) {
  const delayMillis = clock.delayMillisOf(playerIndex),
    showDelayTime = clock.countdownDelay !== undefined && !clock.goneBerserk[playerIndex],
    isRunning = playerIndex === clock.times.activePlayerIndex;
  if (els.time) {
    els.time.innerHTML = formatClockTime(
      millis,
      delayMillis,
      clock.showTenths(millis),
      true,
      showDelayTime,
      clock.opts.nvui,
    );
    const cl = els.time.classList;
    if (clock.isInDelay(playerIndex) && isRunning) {
      cl.remove('notindelay');
      cl.add('indelay');
    } else if (cl.contains('indelay') && isRunning) {
      clock.emergSound.lowtime(); //move to ctrl?
      cl.remove('indelay');
      cl.add('notindelay');
    } else if (cl.contains('notindelay') && !isRunning) cl.remove('notindelay');
  }
  if (els.bar) els.bar.style.transform = 'scale(' + clock.timeRatio(millis, playerIndex) + ',1)';
  if (els.clock) {
    const cl = els.clock.classList;
    if (isEmerg(millis, clock, playerIndex)) cl.add('emerg');
    else if (cl.contains('emerg')) cl.remove('emerg');
  }
}

function showBerserk(ctrl: RoundController, playerIndex: PlayerIndex): boolean {
  return !!ctrl.goneBerserk[playerIndex] && ctrl.data.game.turns <= 1 && game.playable(ctrl.data);
}

function renderBerserk(ctrl: RoundController, playerIndex: PlayerIndex, position: Position) {
  return showBerserk(ctrl, playerIndex) ? h('div.berserked.' + position, justIcon('`')) : null;
}

function goBerserk(ctrl: RoundController) {
  const clock = ctrl.clock!;
  const isByoyomi = !!clock.byoyomiData;
  if (!game.berserkableBy(ctrl.data)) return;
  if (ctrl.goneBerserk[ctrl.data.player.playerIndex]) return;
  return h('button.fbt.go-berserk', {
    attrs: {
      title: `GO BERSERK! Half the time, no increment/delay,${isByoyomi ? ' no byoyomi,' : ''} bonus point`,
      'data-icon': '`',
    },
    hook: bind('click', ctrl.goBerserk),
  });
}

function tourRank(ctrl: RoundController, playerIndex: PlayerIndex, position: Position) {
  const d = ctrl.data,
    ranks = d.tournament?.ranks || d.swiss?.ranks;
  return ranks && !showBerserk(ctrl, playerIndex)
    ? h(
        'div.tour-rank.' + position,
        {
          attrs: { title: 'Current tournament rank' },
        },
        '#' + ranks[playerIndex],
      )
    : null;
}
