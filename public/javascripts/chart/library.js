playstrategy.libraryChart = function (data, allowedVariants) {
  const trans = playstrategy.trans(data.i18n);
  playstrategy.loadScriptCJS('javascripts/chart/common.js').then(function () {
    playstrategy.chartCommon('highchart').then(function () {
      var disabled = { enabled: false };
      var noText = { text: null };
      $('#library_chart').each(function () {
        // Filter variants by allowedVariants if provided
        var freq = allowedVariants
          ? data.freq.filter(function (row) {
              return allowedVariants.includes(row[1]);
            })
          : data.freq;
        // Collect all unique game types
        var gameTypes = Array.from(
          new Set(
            freq.map(function (row) {
              return row[1];
            }),
          ),
        );
        // Collect all months
        var allMonths = Array.from(
          new Set(
            freq.map(function (row) {
              return row[0];
            }),
          ),
        );
        allMonths.sort();
        var variantNames = data.variantNames || {};
        // Prepare cumulative data for each game type
        var series = gameTypes.map(function (type, idx) {
          var filtered = freq.filter(function (row) {
            return row[1] === type;
          });
          var cumul = [];
          var sum = 0;
          for (var i = 0; i < filtered.length; i++) {
            sum += filtered[i][2];
            cumul.push([filtered[i][0], sum]);
          }
          var colorList = Highcharts.getOptions().colors;
          var markerOpt = idx < colorList.length ? { enabled: false } : { enabled: true, radius: 5 };
          return {
            name: variantNames[type] || type,
            data: allMonths.map(function (m) {
              var found = cumul.find(function (row) {
                return row[0] === m;
              });
              return found ? (found[1] > 0 ? found[1] : 1) : null; //cant plot log(0)
            }),
            color: colorList[idx % colorList.length],
            lineWidth: 4,
            marker: markerOpt,
          };
        });
        Highcharts.chart(this, {
          chart: {
            type: 'line',
            zoomType: 'xy',
            alignTicks: false,
          },
          title: noText,
          credits: disabled,
          legend: {
            enabled: true,
            align: 'center',
            verticalAlign: 'bottom',
            layout: 'horizontal',
          },
          xAxis: {
            categories: allMonths,
            title: { text: trans.noarg('Year-Month') },
            labels: { rotation: -45 },
            gridLineWidth: 1,
          },
          yAxis: {
            title: { text: trans.noarg('Total Games') },
            min: 1,
            type: 'logarithmic',
          },
          series: series,
          plotOptions: {},
        });
      });
    });
  });
};
