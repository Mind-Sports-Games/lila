playstrategy.load.then(() => {
  $('#library-section').each(function (this: HTMLElement) {
    const $gamegroups = $('button.gamegroup');
    const $variants = $('button.variant');

    $gamegroups.on('click', function (this: HTMLElement) {
      $gamegroups.removeClass('button button-color-choice selected');
      $(this).addClass('button button-color-choice selected');

      const gameFamily = $(this).val() as string;

      const toShow: HTMLElement[] = [];
      const toHide: HTMLElement[] = [];
      $variants.each(function (this: HTMLElement) {
        const gfOfVariant = ($(this).val() as string).split('_')[0];
        const additionalMatches = gfOfVariant === '6' && gameFamily === '7'; //add oware to mancala group
        if (gfOfVariant === gameFamily || additionalMatches) {
          toShow.push($(this)[0]);
        } else {
          toHide.push($(this)[0]);
        }
      });
      $(toShow).show().removeAttr('style'); //remove unwated display: block added by show()
      $(toHide).hide();
    });

    $variants.on('click', function (this: HTMLElement, e) {
      e.preventDefault();
      const href = $(this).attr('href');
      if (href) window.location.href = href;
    });
    $variants.on('mouseenter', function (this: HTMLElement) {
      $(this).addClass('button button-color-choice');
    });
    $variants.on('mouseleave', function (this: HTMLElement) {
      $(this).removeClass('button button-color-choice');
    });
    $gamegroups.on('mouseenter', function (this: HTMLElement) {
      if (!$(this).hasClass('selected')) {
        $(this).addClass('button button-color-choice');
      }
    });
    $gamegroups.on('mouseleave', function (this: HTMLElement) {
      if (!$(this).hasClass('selected')) {
        $(this).removeClass('button button-color-choice');
      }
    });

    // Initialise the page by clicking the first game group button
    if ($gamegroups.length > 0) {
      $gamegroups.first().trigger('click');
    }
  });
});
