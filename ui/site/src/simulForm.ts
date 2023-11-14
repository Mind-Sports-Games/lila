import flatpickr from 'flatpickr';

playstrategy.load.then(() => {
  const $useByoyomi = $('#form3-clock_useByoyomi'),
    $useBronsteinDelay = $('#form3-clock_useBronsteinDelay'),
    $useSimpleDelay = $('#form3-clock_useSimpleDelay'),
    hideByoyomiSettings = () => {
      $('.form3 .byoyomiClock').toggle($useByoyomi.is(':checked'));
      $('.form3 .byoyomiPeriods').toggle($useByoyomi.is(':checked'));
    },
    toggleByoyomiSettings = () => {
      $useBronsteinDelay.prop('checked', false);
      $useSimpleDelay.prop('checked', false);
      hideByoyomiSettings();
    },
    toggleBronstein = () => {
      $useByoyomi.prop('checked', false);
      hideByoyomiSettings();
      $useSimpleDelay.prop('checked', false);
    },
    toggleSimpleDelay = () => {
      $useByoyomi.prop('checked', false);
      hideByoyomiSettings();
      $useBronsteinDelay.prop('checked', false);
    };

  $useByoyomi.on('change', toggleByoyomiSettings);
  $useBronsteinDelay.on('change', toggleBronstein);
  $useSimpleDelay.on('change', toggleSimpleDelay);
  hideByoyomiSettings();

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
