import flatpickr from 'flatpickr';

playstrategy.load.then(() => {
  const $variant = $('#form3-variant'),
    $medley = $('#form3-medley'),
    $drawTables = $('.form3 .drawTables'),
    $perPairingDrawTables = $('.form3 .perPairingDrawTables'),
    $onePerGameFamily = $('#form3-medleyDefaults_onePerGameFamily'),
    $exoticChessVariants = $('#form3-medleyDefaults_exoticChessVariants'),
    $draughts64Variants = $('#form3-medleyDefaults_draughts64Variants'),
    $bestOfX = $('#form3-xGamesChoice_bestOfX'),
    $playX = $('#form3-xGamesChoice_playX'),
    showPosition = () =>
      $('.form3 .position').toggle(['0_1', '1_1'].includes($variant.val() as string) && !$medley.is(':checked')),
    showDrawTables = () =>
      $drawTables
        .add($perPairingDrawTables)
        .toggle(($variant.val() as string).startsWith('1_') && !$medley.is(':checked')),
    showMedleySettings = () => {
      $('.form3 .medleyMinutes').toggle($medley.is(':checked'));
      $('.form3 .medleyDefaults').toggle($medley.is(':checked'));
      $('.form3 .medleyGameFamily').toggle($medley.is(':checked'));
      $('.form3 .variant').toggle(!$medley.is(':checked'));
      showPosition();
      showDrawTables();
    },
    toggleOff = (selector: Selector) => {
      const $other = $(selector);
      if ($other.is(':checked')) {
        $other.prop('checked', false);
      }
    },
    toggleOnePerGameFamily = () => {
      toggleOff('#form3-medleyDefaults_exoticChessVariants');
      toggleOff('#form3-medleyDefaults_draughts64Variants');
      $('.form3 .medleyGameFamily').toggle($medley.is(':checked'));
    },
    toggleChessVariants = () => {
      toggleOff('#form3-medleyDefaults_onePerGameFamily');
      toggleOff('#form3-medleyDefaults_draughts64Variants');
      $('.form3 .medleyGameFamily').toggle(!$exoticChessVariants.is(':checked'));
    },
    toggleDraughts64Variants = () => {
      toggleOff('#form3-medleyDefaults_onePerGameFamily');
      toggleOff('#form3-medleyDefaults_exoticChessVariants');
      $('.form3 .medleyGameFamily').toggle(!$draughts64Variants.is(':checked'));
    };

  $variant.find('optgroup').each((_, optgroup: HTMLElement) => {
    optgroup.setAttribute('label', optgroup.getAttribute('name') || '');
  });

  $variant.on('change', showPosition);
  $variant.on('change', showDrawTables);
  $drawTables.on('change', () => toggleOff('#form3-perPairingDrawTables'));
  $perPairingDrawTables.on('change', () => toggleOff('#form3-drawTables'));
  $medley.on('change', showMedleySettings);
  $onePerGameFamily.on('change', toggleOnePerGameFamily);
  $exoticChessVariants.on('change', toggleChessVariants);
  $draughts64Variants.on('change', toggleDraughts64Variants);
  showMedleySettings();

  $bestOfX.on('change', () => toggleOff($playX));
  $playX.on('change', () => toggleOff($bestOfX));

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
