import * as xhr from 'common/xhr';

interface ChallengeOpts {
  socketUrl: string;
  xhrUrl: string;
  owner: boolean;
  data: any;
}

export default function PlayStrategyChallenge(opts: ChallengeOpts) {
  const selector = '.challenge-page';
  let accepting: boolean;

  playstrategy.socket = new playstrategy.StrongSocket(opts.socketUrl, opts.data.socketVersion, {
    events: {
      reload() {
        xhr.text(opts.xhrUrl).then(html => {
          $(selector).replaceWith($(html).find(selector));
          init();
          playstrategy.contentLoaded($(selector)[0]);
        });
      },
    },
  });

  function init() {
    if (!accepting)
      $('#challenge-redirect').each(function (this: HTMLAnchorElement) {
        location.href = this.href;
      });
    $(selector)
      .find('form.accept')
      .on('submit', function (this: HTMLFormElement) {
        accepting = true;
        $(this).html('<span class="ddloader"></span>');
      });
    $(selector)
      .find('form.xhr')
      .on('submit', function (this: HTMLFormElement, e) {
        e.preventDefault();
        xhr.formToXhr(this);
        $(this).html('<span class="ddloader"></span>');
      });
    $(selector)
      .find('input.friend-autocomplete')
      .each(function (this: HTMLInputElement) {
        const input = this;
        playstrategy.userComplete().then(uac =>
          uac({
            input: input,
            friend: true,
            tag: 'span',
            focus: true,
            onSelect: () => setTimeout(() => (input.parentNode as HTMLFormElement).submit(), 100),
          }),
        );
      });
  }

  init();

  function pingNow() {
    if (document.getElementById('ping-challenge')) {
      playstrategy.socket.send('ping');
      setTimeout(pingNow, 9000);
    }
  }

  pingNow();
}

(window as any).PlayStrategyChallenge = PlayStrategyChallenge; // esbuild
