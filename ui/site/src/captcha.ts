import * as xhr from 'common/xhr';
import * as miniBoard from 'common/mini-board';

playstrategy.load.then(() => {
  $('div.captcha').each(function (this: HTMLElement) {
    const $captcha = $(this),
      $board = $captcha.find('.mini-board'),
      $input = $captcha.find('input').val('');
    // init is idempotent and memoised per node, so this both guarantees the board
    // exists and reuses the one the site boot may already have started.
    miniBoard.init($board[0] as HTMLElement).then(cg => {
      const fen = cg.getFen(),
        destsObj = $board.data('moves'),
        dests = new Map();
      for (const k in destsObj) dests.set(k, destsObj[k].match(/.{2}/g));
      cg.set({
        turnPlayerIndex: cg.state.orientation,
        movable: {
          free: false,
          dests,
          playerIndex: cg.state.orientation,
          events: {
            after(orig: string, dest: string) {
              $captcha.removeClass('success failure');
              submit(orig + ' ' + dest);
            },
          },
        },
      });

      const submit = function (solution: string) {
        $input.val(solution);
        xhr.text(xhr.url($captcha.data('check-url'), { solution })).then(data => {
          $captcha.toggleClass('success', data == '1').toggleClass('failure', data != '1');
          if (data == '1') cg.stop();
          else
            setTimeout(
              () =>
                cg.set({
                  fen: fen,
                  turnPlayerIndex: cg.state.orientation,
                  movable: { dests },
                }),
              300,
            );
        });
      };
    });
  });
});
