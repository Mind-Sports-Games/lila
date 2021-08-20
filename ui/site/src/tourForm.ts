import flatpickr from 'flatpickr';

playstrategy.load.then(() => {
  const $gameLib = $('#form3-gameLib'),
    $chessVariant = $('#form3-chessVariant'),
    $draughtsVariant = $('#form3-draughtsVariant'),
    showPositionChess = () =>
      $('.form3 .position').toggleClass('none', !['1', 'standard'].includes(
        $chessVariant.val() as string
      )),
    showPositionDraughts = () =>
      $('.form3 .position').toggleClass('none', !['1', 'standard'].includes(
        $draughtsVariant.val() as string
      ));

  $chessVariant.on('change', showPositionChess);
  $draughtsVariant.on('change', showPositionDraughts);
  showPositionChess();

  $gameLib.on('change', function (this: HTMLElement) {
    console.log("here2");
    const gameLib = $(this).val();
    $('.form3 .chessVariant').toggle(gameLib == '0');
    $('.form3 .draughtsVariant').toggle(gameLib == '1');
  })
  .trigger('change');

  $('form .conditions a.show').on('click', function (this: HTMLAnchorElement) {
    $(this).remove();
    $('form .conditions').addClass('visible');
  });

  $('.flatpickr').each(function (this: HTMLInputElement) {
    flatpickr(this, {
      minDate: 'today',
      maxDate: new Date(Date.now() + 1000 * 3600 * 24 * 31 * 3),
      dateFormat: 'Z',
      altInput: true,
      altFormat: 'Y-m-d h:i K',
      monthSelectorType: 'static',
      disableMobile: true,
    });
  });
});
