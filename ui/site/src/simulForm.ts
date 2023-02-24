import flatpickr from 'flatpickr';

playstrategy.load.then(() => {
  const $useByoyomi = $('#form3-clock_useByoyomi'),
    showByoyomiSettings = () => {
      $('.form3 .byoyomiClock').toggle($useByoyomi.is(':checked'));
      $('.form3 .byoyomiPeriods').toggle($useByoyomi.is(':checked'));
    };

  $useByoyomi.on('change', showByoyomiSettings);
  showByoyomiSettings();

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
