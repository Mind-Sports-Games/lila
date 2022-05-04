import flatpickr from 'flatpickr';

playstrategy.load.then(() => {
  const $variant = $('#form3-variant'),
    $medley = $('#form3-medley'),
    showPosition = () =>
      $('.form3 .position').toggle(['0_1', '1_1'].includes($variant.val() as string) && !$medley.is(':checked')),
    showDrawTables = () =>
      $('.form3 .drawTables').toggle(($variant.val() as string).startsWith('1_') && !$medley.is(':checked')),
    showGameLogics = () => {
      $('.form3 .medleyGameFamily').toggle($medley.is(':checked'));
      $('.form3 .variant').toggle(!$medley.is(':checked'));
      showPosition();
      showDrawTables();
    };

  $variant.find('optgroup').each((_, optgroup: HTMLElement) => {
    optgroup.setAttribute('label', optgroup.getAttribute('name') || '');
  });

  $variant.on('change', showPosition);
  $variant.on('change', showDrawTables);
  $medley.on('change', showGameLogics);
  showGameLogics();

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
