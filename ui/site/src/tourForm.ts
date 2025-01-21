import flatpickr from 'flatpickr';

playstrategy.load.then(() => {
  const $variant = $('#form3-variant'),
    $rated = $('#form3-rated'),
    $medley = $('#form3-medley'),
    $handicapped = $('#form3-variantSettings_handicaps_handicapped'),
    $mcmahon = $('#form3-mcmahon_mcmahon'),
    $drawTables = $('.form3 .drawTables'),
    $perPairingDrawTables = $('.form3 .perPairingDrawTables'),
    $onePerGameFamily = $('#form3-medleyDefaults_onePerGameFamily'),
    $exoticChessVariants = $('#form3-medleyDefaults_exoticChessVariants'),
    $draughts64Variants = $('#form3-medleyDefaults_draughts64Variants'),
    $bestOfX = $('#form3-xGamesChoice_bestOfX'),
    $playX = $('#form3-xGamesChoice_playX'),
    $useMatchScore = $('#form3-xGamesChoice_matchScore'),
    $useByoyomi = $('#form3-clock_useByoyomi'),
    $useBronsteinDelay = $('#form3-clock_useBronsteinDelay'),
    $useSimpleDelay = $('#form3-clock_useSimpleDelay'),
    showPosition = () =>
      $('.form3 .position').toggle(['0_1', '1_1'].includes($variant.val() as string) && !$medley.is(':checked')),
    showDrawTables = () =>
      // NOTE: as this script is loaded both on /tournament/manager AND tournament/manager/<id>, $variant.val() could be undefined so I added "|| ''" for startsWith() to not create an error.
      $drawTables
        .add($perPairingDrawTables)
        .toggle((($variant.val() as string) || '').startsWith('1_') && !$medley.is(':checked')),
    showMedleySettings = () => {
      $('.form3 .medleyMinutes').toggle($medley.is(':checked'));
      $('.form3 .medleyIntervalOptions').toggle($medley.is(':checked'));
      $('.form3 .medleyDefaults').toggle($medley.is(':checked'));
      $('.form3 .medleyGameFamily').toggle($medley.is(':checked'));
      $('.form3 .duration').toggle(!$medley.is(':checked'));
      $('.form3 .variant').toggle(!$medley.is(':checked'));
      showPosition();
      showDrawTables();
    },
    showInputRatings = () => {
      $('.form3 .inputPlayerRatings').toggle($handicapped.is(':checked') || $mcmahon.is(':checked'));
    },
    showVariantOptions = () => {
      const isBackgammon = (($variant.val() as string) || '').startsWith('10_');
      const isGo = (($variant.val() as string) || '').startsWith('9_');
      $('.form3 .backgammonPoints').toggle(isBackgammon);
      $('.form3 .mcmahon').toggle(isGo);
      $('.form3 .handicapped').toggle(isGo);
      $('.form3 .mcmahonCutoff').toggle(isGo);
      if (!isGo) {
        $mcmahon.prop('checked', false);
        $handicapped.prop('checked', false);
      }
      showInputRatings();
    },
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
    },
    matchSelectors = (selector1: Selector, selector2: Selector) => {
      const $sel1 = $(selector1);
      const $sel2 = $(selector2);
      if ($sel1.is(':checked')) {
        $sel2.prop('checked', true);
      } else {
        $sel2.prop('checked', false);
      }
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
  $variant.on('change', showVariantOptions);
  $handicapped.on('change', () => {
    toggleOff($medley);
    toggleOff($rated);
    toggleOff($mcmahon);
    showMedleySettings();
    showInputRatings();
  });
  $mcmahon.on('change', () => {
    toggleOff($medley);
    toggleOff($rated);
    toggleOff($handicapped);
    showMedleySettings();
    showInputRatings();
  });
  $drawTables.on('change', () => toggleOff('#form3-drawTables_perPairingDrawTables'));
  $perPairingDrawTables.on('change', () => toggleOff('#form3-drawTables_drawTables'));
  $medley.on('change', showMedleySettings);
  $onePerGameFamily.on('change', toggleOnePerGameFamily);
  $exoticChessVariants.on('change', toggleChessVariants);
  $draughts64Variants.on('change', toggleDraughts64Variants);
  showVariantOptions();
  showMedleySettings();
  showInputRatings();
  $bestOfX.on('change', () => {
    toggleOff($playX);
    toggleOff($useMatchScore);
  });
  $playX.on('change', () => toggleOff($bestOfX));
  $playX.on('change', () => matchSelectors($playX, $useMatchScore));

  $useByoyomi.on('change', toggleByoyomiSettings);
  $useBronsteinDelay.on('change', toggleBronstein);
  $useSimpleDelay.on('change', toggleSimpleDelay);
  hideByoyomiSettings();
  toggleDelayIncrement();

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
