import flatpickr from 'flatpickr';

playstrategy.load.then(() => {
  const $variant = $('#form3-variant'),
    showPosition = () => $('.form3 .position').toggle(['0_1', '1_1'].includes($variant.val() as string)),
    showDrawTables = () => $('.form3 .drawTables').toggle(($variant.val() as string).startsWith('1_'));

  $variant.find('optgroup').each((_, optgroup: HTMLElement) => {
    optgroup.setAttribute('label', optgroup.getAttribute('name') || '');
  });

  $variant.on('change', showPosition);
  $variant.on('change', showDrawTables);
  showPosition();
  showDrawTables();

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
