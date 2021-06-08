import * as xhr from 'common/xhr';
import PlaystrategyChat from 'chat';

window.PlaystrategyChat = PlaystrategyChat;

interface TeamOpts {
  id: string;
  socketVersion: number;
  chat?: any;
}

export default function (opts: TeamOpts) {
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
