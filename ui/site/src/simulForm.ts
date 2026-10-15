import flatpickr from 'flatpickr';

playstrategy.load.then(() => {
  const $useByoyomi = $('#form3-clock_useByoyomi'),
    $useBronsteinDelay = $('#form3-clock_useBronsteinDelay'),
    $useSimpleDelay = $('#form3-clock_useSimpleDelay'),
    hideByoyomiSettings = () => {
      $('.form3 .byoyomiClock').toggle($useByoyomi.is(':checked'));
      $('.form3 .byoyomiPeriods').toggle($useByoyomi.is(':checked'));
    },
    toggleDelayIncrement = () => {
      const useDelay = $useBronsteinDelay.is(':checked') || $useSimpleDelay.is(':checked');
      $('.form3 .clockDelay').toggle(useDelay);
      $('.form3 .clockIncrement').toggle(!useDelay);
    },
    toggleByoyomiSettings = () => {
      toggleDelayIncrement();
      $useBronsteinDelay.prop('checked', false);
      $useSimpleDelay.prop('checked', false);
      hideByoyomiSettings();
    },
    toggleBronstein = () => {
      toggleDelayIncrement();
      $useByoyomi.prop('checked', false);
      hideByoyomiSettings();
      $useSimpleDelay.prop('checked', false);
    },
    toggleSimpleDelay = () => {
      toggleDelayIncrement();
      $useByoyomi.prop('checked', false);
      hideByoyomiSettings();
      $useBronsteinDelay.prop('checked', false);
    };

  $useByoyomi.on('change', toggleByoyomiSettings);
  $useBronsteinDelay.on('change', toggleBronstein);
  $useSimpleDelay.on('change', toggleSimpleDelay);
  hideByoyomiSettings();
  toggleDelayIncrement();

  $('form .conditions a.show').on('click', function (this: HTMLAnchorElement) {
    $(this).remove();
    $('form .conditions').addClass('visible');
  });

  // a family chip is a radio, which a second click would not close: this one does
  document.querySelectorAll<HTMLLabelElement>('.simul-form .variants__family-name').forEach(label =>
    label.addEventListener('click', e => {
      const radio = document.getElementById(label.htmlFor) as HTMLInputElement | null;
      if (radio?.checked) {
        e.preventDefault();
        radio.checked = false;
      }
    }),
  );

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
