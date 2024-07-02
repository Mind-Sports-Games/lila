import * as xhr from 'common/xhr';
import PlayStrategyChat from 'chat';

window.PlayStrategyChat = PlayStrategyChat;

interface TeamOpts {
  id: string;
  socketVersion: number;
  chat?: any;
}

export default function PlayStrategyTeam(opts: TeamOpts) {
  playstrategy.socket = new playstrategy.StrongSocket('/team/' + opts.id, opts.socketVersion);

  if (opts.chat) playstrategy.makeChat(opts.chat);

  $('#team-subscribe').on('change', function (this: HTMLInputElement) {
    $(this)
      .parents('form')
      .each(function (this: HTMLFormElement) {
        xhr.formToXhr(this);
      });
  });
}

(window as any).PlayStrategyTeam = PlayStrategyTeam; // esbuild
