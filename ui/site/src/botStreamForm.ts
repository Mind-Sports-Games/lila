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

  $('.bot-stream__matchups').on('click', '.bot-stream__fill', function (this: HTMLButtonElement) {
    const textarea = this.closest('.form-group')!.querySelector('textarea')!,
      keys = this.dataset.variants;
    if (!keys) {
      textarea.value = '';
      return;
    }
    const current = textarea.value.split(/[\s,]+/).filter(Boolean);
    keys.split(' ').forEach(key => {
      if (!current.includes(key)) current.push(key);
    });
    textarea.value = current.join(', ');
  });

  $('.bot-stream__add').on('click', function (this: HTMLButtonElement) {
    const container = document.querySelector('.bot-stream__matchups')!,
      count = container.querySelectorAll('.bot-stream__matchup').length,
      max = parseInt(this.dataset.max!, 10);
    if (count >= max) return;
    const html = document
      .querySelector<HTMLTemplateElement>('.bot-stream__template')!
      .innerHTML.split('__INDEX__')
      .join(String(count))
      .split('__NUMBER__')
      .join(String(count + 1));
    container.insertAdjacentHTML('beforeend', html);
    if (count + 1 >= max) $(this).hide();
  });
});
