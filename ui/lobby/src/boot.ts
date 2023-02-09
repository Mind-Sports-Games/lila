import * as xhr from 'common/xhr';
import main from './main';
import modal from 'common/modal';
import { ChatCtrl } from 'chat';
import { LobbyOpts } from './interfaces';
import { numberFormat } from 'common/number';

export default function PlayStrategyLobby(opts: LobbyOpts) {
  opts.element = document.querySelector('.lobby__app') as HTMLElement;
  opts.pools = [
    // mirrors modules/pool/src/main/PoolList.scala (ids)
    {
      id: '1+0-standard',
      lim: 1,
      inc: 0,
      perf: 'Blitz',
      variant: 'standard',
      variantDisplayName: 'Chess',
      variantId: '0_1',
    },
    {
      id: '3+2-standard',
      lim: 3,
      inc: 2,
      perf: 'Blitz',
      variant: 'standard',
      variantDisplayName: 'Chess',
      variantId: '0_1',
    },
    {
      id: '3+2-international',
      lim: 3,
      inc: 2,
      perf: 'International',
      variant: 'international',
      variantDisplayName: 'Draughts',
      variantId: '1_1',
    },
    {
      id: '3+2-linesOfAction',
      lim: 3,
      inc: 2,
      perf: 'Lines Of Action',
      variant: 'linesOfAction',
      variantDisplayName: 'Lines Of Action',
      variantId: '2_11',
    },
    { id: '5+3-shogi', lim: 5, inc: 3, perf: 'Shogi', variant: 'shogi', variantDisplayName: 'Shogi', variantId: '3_1' },
    {
      id: '3+2-xiangqi',
      lim: 3,
      inc: 2,
      perf: 'Xiangqi',
      variant: 'xiangqi',
      variantDisplayName: 'Xiangqi',
      variantId: '4_2',
    },
    {
      id: '3+2-flipello',
      lim: 3,
      inc: 2,
      perf: 'Flipello',
      variant: 'flipello',
      variantDisplayName: 'Othello',
      variantId: '5_6',
    },
    {
      id: '3+2-amazons',
      lim: 3,
      inc: 2,
      perf: 'Amazons',
      variant: 'amazons',
      variantDisplayName: 'Amazons',
      variantId: '8_1',
    },
    { id: '3+2-oware', lim: 3, inc: 2, perf: 'Oware', variant: 'oware', variantDisplayName: 'Oware', variantId: '6_1' },
    {
      id: '3+2-togyzkumalak',
      lim: 3,
      inc: 2,
      perf: 'Togyzkumalak',
      variant: 'togyzkumalak',
      variantDisplayName: 'Togyzkumalak',
      variantId: '7_1',
    },
  ];
  const nbRoundSpread = spreadNumber('#nb_games_in_play > strong', 8),
    nbUserSpread = spreadNumber('#nb_connected_players > strong', 10),
    getParameterByName = (name: string) => {
      const match = RegExp('[?&]' + name + '=([^&]*)').exec(location.search);
      return match && decodeURIComponent(match[1].replace(/\+/g, ' '));
    };
  playstrategy.socket = new playstrategy.StrongSocket('/lobby/socket/v5', opts.chatSocketVersion, {
    receive(t: string, d: any) {
      lobby.socketReceive(t, d);
    },
    events: {
      n(_: string, msg: any) {
        nbUserSpread(msg.d);
        setTimeout(() => nbRoundSpread(msg.r), playstrategy.socket.pingInterval() / 2);
      },
      reload_timeline() {
        xhr.text('/timeline').then(html => {
          $('.timeline').html(html);
          playstrategy.contentLoaded();
        });
      },
      featured(o: { html: string }) {
        $('.lobby__tv').html(o.html);
        playstrategy.contentLoaded();
      },
      redirect(e: RedirectTo) {
        lobby.leavePool();
        lobby.setRedirecting();
        playstrategy.redirect(e);
      },
      fen(e: any) {
        lobby.gameActivity(e.id);
      },
    },
  });
  playstrategy.StrongSocket.firstConnect.then(() => {
    const gameId = getParameterByName('hook_like');
    if (!gameId) return;
    const ratingRange = lobby.setup.stores.hook.get()?.ratingRange;
    xhr.text(`/setup/hook/${playstrategy.sri}/like/${gameId}?rr=${ratingRange || ''}`, { method: 'post' });
    lobby.setTab('real_time');
    history.replaceState(null, '', '/');
  });

  opts.blindMode = $('body').hasClass('blind-mode');
  opts.trans = playstrategy.trans(opts.i18n);
  opts.socketSend = playstrategy.socket.send;
  const lobby = main(opts);

  const $startButtons = $('.lobby__start'),
    clickEvent = opts.blindMode ? 'click' : 'mousedown';

  $startButtons
    .find('a:not(.disabled)')
    .on(clickEvent, function (this: HTMLAnchorElement) {
      $(this).addClass('active').siblings().removeClass('active');
      playstrategy.loadCssPath('lobby.setup');
      lobby.leavePool();
      let url = this.href;
      if (this.dataset.hrefAddon) {
        url += this.dataset.hrefAddon;
        delete this.dataset.hrefAddon;
      }
      fetch(url, {
        ...xhr.defaultInit,
        headers: xhr.xhrHeader,
      }).then(res =>
        res.text().then(text => {
          if (res.ok) {
            lobby.setup.prepareForm(
              modal($(text), 'game-setup', () => $startButtons.find('.active').removeClass('active'))
            );
            playstrategy.contentLoaded();
          } else {
            alert(text);
            playstrategy.reload();
          }
        })
      );
    })
    .on('click', e => e.preventDefault());

  if (['#ai', '#friend', '#hook'].includes(location.hash)) {
    $startButtons
      .find('.config_' + location.hash.replace('#', ''))
      .each(function (this: HTMLElement) {
        this.dataset.hrefAddon = location.search;
      })
      .trigger(clickEvent);

    if (location.hash === '#hook') {
      if (/time=realTime/.test(location.search)) lobby.setTab('real_time');
      else if (/time=correspondence/.test(location.search)) lobby.setTab('seeks');
    }

    history.replaceState(null, '', '/');
  }

  suggestBgSwitch();

  const chatOpts = opts.chat;
  if (chatOpts) {
    chatOpts.instance = playstrategy.makeChat(chatOpts) as Promise<ChatCtrl>;
  }
}

function suggestBgSwitch() {
  const m = window.matchMedia('(prefers-color-scheme: dark)');
  if (m.media == 'not all') return;
  const current = document.body.getAttribute('data-theme');
  if (m.matches == (current == 'dark')) return;

  let dasher: Promise<any>;
  const getDasher = (): Promise<any> => {
    dasher =
      dasher ||
      playstrategy
        .loadModule('dasher')
        .then(() => window.PlayStrategyDasher(document.createElement('div'), { playing: false }));
    return dasher;
  };

  $('.bg-switch')
    .addClass('active')
    .on('click', () =>
      getDasher().then(dasher =>
        dasher.subs.background.set(document.body.classList.contains('dark') ? 'light' : 'dark')
      )
    );
}

function spreadNumber(selector: string, nbSteps: number) {
  const el = document.querySelector(selector) as HTMLElement;
  let previous = parseInt(el.getAttribute('data-count')!);
  const display = (prev: number, cur: number, it: number) => {
    el.textContent = numberFormat(Math.round((prev * (nbSteps - 1 - it) + cur * (it + 1)) / nbSteps));
  };
  let timeouts: number[] = [];
  return (nb: number, overrideNbSteps?: number) => {
    if (!el || (!nb && nb !== 0)) return;
    if (overrideNbSteps) nbSteps = Math.abs(overrideNbSteps);
    timeouts.forEach(clearTimeout);
    timeouts = [];
    const interv = Math.abs(playstrategy.socket.pingInterval() / nbSteps);
    const prev = previous || nb;
    previous = nb;
    for (let i = 0; i < nbSteps; i++) timeouts.push(setTimeout(() => display(prev, nb, i), Math.round(i * interv)));
  };
}
