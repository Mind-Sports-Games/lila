import flatpickr from 'flatpickr';

playstrategy.load.then(() => {
  const $variant = $('#form3-variant'),
    $drawTables = $('.form3 .drawTables'),
    $perPairingDrawTables = $('.form3 .perPairingDrawTables'),
    showPosition = () => $('.form3 .position').toggle(['0_1', '1_1'].includes($variant.val() as string)),
    showDrawTables = () => $drawTables.add($perPairingDrawTables).toggle(($variant.val() as string).startsWith('1_')),
    toggleOther = (selector: Selector) => () => {
      const $other = $(selector);
      if($other.is(":checked")) {
        $other.prop('checked', false);
      }
    };

  $variant.find('optgroup').each((_, optgroup: HTMLElement) => {
    optgroup.setAttribute('label', optgroup.getAttribute('name') || '');
  });

  $variant.on('change', showPosition);
  $variant.on('change', showDrawTables);
  $drawTables.on('change', toggleOther("#form3-perPairingDrawTables"));
  $perPairingDrawTables.on('change', toggleOther("#form3-drawTables"));
  showPosition();
  showDrawTables();

  $('form .conditions a.show').on('click', function(this: HTMLAnchorElement) {
    $(this).remove();
    $('form .conditions').addClass('visible');
  });

  $('.flatpickr').each(function(this: HTMLInputElement) {
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
