playstrategy.load.then(() => {
  $('#library-section').each(function (this: HTMLElement) {
    const $gamegroups = $('button.gamegroup');
    const $variants = $('button.variant');

    function updateLibraryChart(allowedVariants: string[]) {
      if (window.playstrategy && window.playstrategy.libraryChart && window.libraryChartData) {
        playstrategy.libraryChart(window.libraryChartData, allowedVariants);
      }
    }

    function updateStatsTable(allowedVariants: string[]) {
      if (window.playstrategy && window.playstrategy.libraryChart && window.libraryChartData) {
        const monthlyData = window.libraryChartData.freq;
        const $statsTable = $('.library-stats-table');
        const $title = $statsTable.find('.library-stats-title');
        const $totalVariants = $statsTable.find('.total-variants .library-stats-value');
        const $totalGames = $statsTable.find('.total-games .library-stats-value');
        const $gamesLastMonth = $statsTable.find('.games-last-month .library-stats-value');
        const $liveGames = $statsTable.find('.live-games');
        const $correspondenceGames = $statsTable.find('.correspondence-games');

        const gameTypes = Array.from(
          new Set(
            monthlyData.map(function (row) {
              return row[1];
            }),
          ),
        );
        //js months are zero-based!
        const lastFullMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 7);

        if (gameTypes.length === allowedVariants.length) {
          $title.text('Overall Game Stats');
          $liveGames.show();
          $correspondenceGames.show();
        } else {
          $title.text('Game Group Stats');
          $liveGames.hide();
          $correspondenceGames.hide();
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

      const allowedVariants = toShow.map(el => $(el).val() as string);
      updateLibraryChart(allowedVariants);

      updateStatsTable(allowedVariants);
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
