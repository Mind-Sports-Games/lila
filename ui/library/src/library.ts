playstrategy.load.then(() => {
  $('#library-section').each(function (this: HTMLElement) {
    const $gamegroups = $('button.gamegroup');
    const $variants = $('a.variant');
    const $variantSection = $('.variants-choice');

    const data = window.libraryChartData;
    const allVariants: string[] = $variants.get().map(el => el.getAttribute('data-value') ?? '');

    function updateLibraryChart(allowedVariants: string[], isOverallStats: boolean) {
      if (window.playstrategy && window.playstrategy.libraryChart && data) {
        if (isOverallStats) {
          //aggregate data by game group
          const dataByGameGroup = data.freq.map(row => [row[0], gameGroupOfFamily(row[1].split('_')[0]), row[2]]);
          const aggregatedDataByGameGroup: Array<[string, string, number]> = [];
          const groupMap = new Map<string, number>();
          dataByGameGroup.forEach(([month, gameGroup, count]) => {
            const key = `${month}|${gameGroup}`;
            groupMap.set(key, (groupMap.get(key) || 0) + count);
          });
          groupMap.forEach((sum, key) => {
            const [month, gameGroup] = key.split('|');
            aggregatedDataByGameGroup.push([month, gameGroup, sum]);
          });
          const newData = {
            ...data,
            freq: aggregatedDataByGameGroup,
          };
          playstrategy.libraryChart(newData);
        } else {
          playstrategy.libraryChart(data, allowedVariants);
        }
      }
    }

    // the server derives this from GameGroup.variants; the chart keys its series by group id,
    // which is not the value the group buttons carry
    function gameGroupOfFamily(familyId: string): string {
      return data?.gameGroupOfFamily?.[familyId] ?? familyId;
    }

    function updateStatsTable(allowedVariants: string[], isOverallStats: boolean, groupName: string = '') {
      if (data && data.freq) {
        const monthlyData = data.freq;
        const $statsTable = $('.library-stats-table');
        const $title = $statsTable.find('.library-stats-title');
        const $totalVariants = $statsTable.find('.total-variants .library-stats-value');
        const $totalVariantsTitle = $statsTable.find('.total-variants .library-stats-term');
        const $totalGames = $statsTable.find('.total-games .library-stats-value');
        const $gamesLastMonth = $statsTable.find('.games-last-month .library-stats-value');
        const $liveGames = $statsTable.find('.live-games');
        const $correspondenceGames = $statsTable.find('.correspondence-games');

        //js months are zero-based!
        const lastFullMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 7);

        if (isOverallStats) {
          $title.text('Overall Game Stats');
          $totalVariantsTitle.text('Total Game Variants');
          $liveGames.show();
          $correspondenceGames.show();
        } else {
          $title.text(`${groupName} Group Stats`);
          $totalVariantsTitle.text(`Total ${groupName} Variants`);
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
      if ($(this).hasClass('selected')) {
        $gamegroups.removeClass('button button-color-choice selected');
        $variantSection.addClass('hidden');
        updateLibraryChart(allVariants, true);
        updateStatsTable(allVariants, true);
        return;
      }
      $gamegroups.removeClass('button button-color-choice selected');
      $(this).addClass('button button-color-choice selected');
      $variantSection.removeClass('hidden');

      const gameGroup = $(this).val() as string;

      const toShow: HTMLElement[] = [];
      const toHide: HTMLElement[] = [];
      $variants.each(function (this: HTMLElement) {
        // the server writes the group on each variant, so the pairs that do not follow the
        // family id - oware under mancala, dameo under draughts - need no rule here
        if ($(this).attr('data-gamegroup') === gameGroup) {
          toShow.push($(this)[0]);
        } else {
          toHide.push($(this)[0]);
        }
      });
      $(toShow).show().removeAttr('style'); //remove unwated display: block added by show()
      $(toHide).hide();

      const allowedVariants = toShow.map(el => $(el).attr('data-value') as string);
      updateLibraryChart(allowedVariants, false);
      updateStatsTable(allowedVariants, false, $(this).text().trim());
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

    //initial load
    updateLibraryChart(allVariants, true);
  });
});
