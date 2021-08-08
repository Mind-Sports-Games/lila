import { RoundOpts, RoundData } from './interfaces';
import { RoundApi, RoundMain } from './main';
import { ChatCtrl } from 'chat';
import { TourPlayer } from 'game';
import { tourStandingCtrl, TourStandingCtrl } from './tourStanding';
import { updateSimulStanding, SimulStanding } from './simulStanding';

export default function(opts: RoundOpts): void {
  const li = window.lidraughts;
  const element = document.querySelector('.round__app') as HTMLElement,
    data: RoundData = opts.data,
    socketParams: any = { userTv: data.userTv && data.userTv.id };
  if (socketParams.userTv && data.userTv && data.userTv.gameId)
      socketParams.gameId = data.userTv.gameId;

  let round: RoundApi, chat: ChatCtrl | undefined;
  if (data.tournament) $('body').data('tournament-id', data.tournament.id);

  li.socket = li.StrongSocket(
    data.url.socket,
    data.player.version, {
      options: { name: 'round' },
      params: socketParams,
      receive(t: string, d: any) {
        round.socketReceive(t, d);
      },
      events: {
        tvSelect(o: any) {
          if (data.tv && data.tv.channel == o.channel) li.reload();
          else $('.tv-channels .' + o.channel + ' .champion').html(
            o.player ? [
              o.player.title ? (o.player.title.endsWith('-64') ? o.player.title.slice(0, o.player.title.length - 3) : o.player.title) : undefined,
              o.player.name,
              o.player.rating
            ].filter(x => x).join('&nbsp') : 'Anonymous');
        },
        end() {
          $.ajax({
            url: [(data.tv ? '/tv' : ''), data.game.id, data.player.color, 'sides'].join('/'),
            success: function(html) {
              const $html = $(html), $meta = $html.find('.game__meta');
              $meta.length && $('.game__meta').replaceWith($meta);
              $('.crosstable').replaceWith($html.find('.crosstable'));
              startTournamentClock();
              li.pubsub.emit('content_loaded');
            }
          });
        },
        tourStanding(s: TourPlayer[]) {
          if (opts.chat && opts.chat.plugin && chat) {
            (opts.chat.plugin as TourStandingCtrl).set(s);
            chat.redraw();
          }
        },
        simulStanding(s: SimulStanding) {
          if (data.simul && data.simul.id == s.id) {
            updateSimulStanding(s, round.trans, round.draughtsResult);
            if (data.simul.nbPlaying != s.g) {
              data.simul.nbPlaying = s.g;
              if (s.g <= 1) round.redraw();
            }
          }
        }
      }
    });

  function startTournamentClock() {
    if (data.tournament) $('.game__tournament .clock').each(function(this: HTMLElement) {
      $(this).clock({
        time: parseFloat($(this).data('time'))
      });
    });
  };

  function getPresetGroup(d: RoundData) {
    if (d.player.spectator) return;
    if (d.steps.length < 4) return 'start';
    else if (d.game.status.id >= 30) return 'end';
    return;
  };

  opts.element = element;
  opts.socketSend = li.socket.send;
  if (!opts.data.tournament && !data.simul && !data.swiss) opts.onChange = (d: RoundData) => {
    if (chat) chat.preset.setGroup(getPresetGroup(d));
  };

  round = (window['LidraughtsRound'] as RoundMain).app(opts);
  if (opts.chat) {
    if (opts.data.tournament?.top) {
      opts.chat.plugin = tourStandingCtrl(opts.data.tournament.top, opts.data.tournament.team, opts.i18n.standing);
      opts.chat.alwaysEnabled = true;
    } else if (!data.simul && !data.swiss) {
      opts.chat.preset = getPresetGroup(data);
      opts.chat.parseMoves = true;
    }
    li.makeChat(opts.chat, function(c) {
      chat = c;
    });
  }
  startTournamentClock();
  $('.round__now-playing .move-on input').change(function() {
    var t = round.moveOn.toggle();
    $('.round__now-playing .move-seq').css('visibility', t ? 'visible' : 'collapse');
  }).prop('checked', round.moveOn.get()).on('click', 'a', function() {
    li.hasToReload = true;
    return true;
  });
  $('.round__now-playing .move-seq input').change(function() {
    round.moveOn.toggleSeq();
  }).prop('checked', round.moveOn.getSeq())
  $('.round__now-playing .move-seq').css('visibility', round.moveOn.get() ? 'visible' : 'collapse');
  if (location.pathname.lastIndexOf('/round-next/', 0) === 0)
    history.replaceState(null, '', '/' + data.game.id);
  $('#zentog').click(() => li.pubsub.emit('zen'));
  li.storage.make('reload-round-tabs').listen(li.reload);
}
