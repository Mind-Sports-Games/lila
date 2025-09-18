playstrategy.load.then(() => {
  $('#library-section').each(function (this: HTMLElement) {
    const $gamegroups = $('button.gamegroup');
    const $variants = $('button.variant');
    const $variantSection = $('.variants-choice');

    const data = window.libraryChartData;
    const allVariants: string[] = $variants.get().map(el => (el as HTMLButtonElement).value);

    function updateLibraryChart(allowedVariants: string[]) {
      if (window.playstrategy && window.playstrategy.libraryChart && data) {
        playstrategy.libraryChart(data, allowedVariants);
      }
    }

    function updateStatsTable(allowedVariants: string[], isGameGroup: boolean) {
      if (data && data.freq) {
        const monthlyData = data.freq;
        const $statsTable = $('.library-stats-table');
        const $title = $statsTable.find('.library-stats-title');
        const $totalVariants = $statsTable.find('.total-variants .library-stats-value');
        const $totalGames = $statsTable.find('.total-games .library-stats-value');
        const $gamesLastMonth = $statsTable.find('.games-last-month .library-stats-value');
        const $liveGames = $statsTable.find('.live-games');
        const $correspondenceGames = $statsTable.find('.correspondence-games');

        //js months are zero-based!
        const lastFullMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 7);

        if (isGameGroup) {
          $title.text('Game Group Stats');
          $liveGames.hide();
          $correspondenceGames.hide();
        } else {
          $title.text('Overall Game Stats');
          $liveGames.show();
          $correspondenceGames.show();
        }
        const filteredMonthlyData = monthlyData.filter(function (row) {
          return allowedVariants.includes(row[1]);
        });
        const filteredLastMonthData = filteredMonthlyData.filter(function (row) {
          return lastFullMonth.includes(row[0]);
        });

        $totalVariants.text(allowedVariants.length.toString());
        $totalGames.text(filteredMonthlyData.reduce((a, b) => a + b[2], 0).toString());
        $gamesLastMonth.text(filteredLastMonthData.reduce((a, b) => a + b[2], 0).toString());
      }
    }

    $gamegroups.on('click', function (this: HTMLElement) {
      if ($(this).hasClass('selected')) {
        $gamegroups.removeClass('button button-color-choice selected');
        $variantSection.addClass('hidden');
        updateLibraryChart(allVariants);
        updateStatsTable(allVariants, false);
        return;
      }
      $gamegroups.removeClass('button button-color-choice selected');
      $(this).addClass('button button-color-choice selected');
      $variantSection.removeClass('hidden');

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

      const allowedVariants = toShow.map(el => $(el).val() as string);
      updateLibraryChart(allowedVariants);
      updateStatsTable(allowedVariants, true);
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
  });
});
