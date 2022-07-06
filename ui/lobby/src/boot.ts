import * as xhr from 'common/xhr';
import main from './main';
import modal from 'common/modal';
import { ChatCtrl } from 'chat';
import { LobbyOpts } from './interfaces';
import { numberFormat } from 'common/number';

export default function PlayStrategyLobby(opts: LobbyOpts) {
  opts.element = document.querySelector('.lobby__app') as HTMLElement;
  opts.pools = [
    // mirrors modules/pool/src/main/PoolList.scala
    
    // { id: '1+0', lim: 1, inc: 0, perf: 'Bullet' },
    // { id: '2+1', lim: 2, inc: 1, perf: 'Bullet' },
    // { id: '3+0', lim: 3, inc: 0, perf: 'Blitz' },
    // { id: '3+2', lim: 3, inc: 2, perf: 'Blitz' },
    // { id: '5+0', lim: 5, inc: 0, perf: 'Blitz' },
    // { id: '5+3', lim: 5, inc: 3, perf: 'Blitz' },
    // { id: '10+0', lim: 10, inc: 0, perf: 'Rapid' },
    // { id: '10+5', lim: 10, inc: 5, perf: 'Rapid' },
    // { id: '15+10', lim: 15, inc: 10, perf: 'Rapid' },
    // { id: '30+0', lim: 30, inc: 0, perf: 'Classical' },
    // { id: '30+20', lim: 30, inc: 20, perf: 'Classical' },
    { id: '1+0-chess', lim: 1, inc: 0, perf: 'Chess', variant: 'standard' },
    { id: '3+2-chess', lim: 3, inc: 2, perf: 'Chess', variant: 'standard' },
    { id: '3+2-international', lim: 3, inc: 2, perf: 'Draughts', variant: 'international' },
    { id: '3+2-linesOfAction', lim: 3, inc: 2, perf: 'LinesOfAction', variant: 'linesOfAction' },
    { id: '3+2-shogi', lim: 3, inc: 2, perf: 'Shogi', variant: 'shogi' },
    { id: '3+2-xiangqi', lim: 3, inc: 2, perf: 'Xiangqi', variant: 'xiangqi' },
    { id: '3+2-flipello', lim: 3, inc: 2, perf: 'Flipello', variant: 'flipello' },
    { id: '3+2-oware', lim: 3, inc: 2, perf: 'Oware', variant: 'oware' },
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
