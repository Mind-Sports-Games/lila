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
          xAxis: [
            {
              categories: allMonths,
              title: { text: trans.noarg('Date'), y: 10 },
              labels: {
                useHTML: true,
                rotation: -30,
                formatter: function () {
                  // this.value is 'yyyy-mm'
                  var parts = this.value.split('-');
                  var monthNum = parseInt(parts[1], 10);
                  var months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
                  return months[monthNum - 1] || '';
                },
              },
              gridLineWidth: 1,
            },
            {
              categories: allMonths,
              linkedTo: 0,
              opposite: false,
              tickPositions: (function () {
                // Find the middle index for each year
                var yearToIndexes = {};
                allMonths.forEach(function (ym, i) {
                  var year = ym.split('-')[0];
                  if (!yearToIndexes[year]) yearToIndexes[year] = [];
                  yearToIndexes[year].push(i);
                });
                return Object.values(yearToIndexes).map(function (arr) {
                  return arr[Math.floor(arr.length / 2)];
                });
              })(),
              labels: {
                y: -10,
                style: { color: '#888', fontSize: 'smaller', fontWeight: 'bold' },
                formatter: function () {
                  var year = this.value.split('-')[0];
                  // Only show the year label at the tick positions
                  return year;
                },
              },
              tickLength: 0,
              lineWidth: 0,
              minorGridLineWidth: 0,
              gridLineWidth: 0,
              minorTickLength: 0,
              title: null,
            },
          ],
          yAxis: {
            title: { text: trans.noarg('Total Games') },
            min: 1,
            //type: 'logarithmic',
          },
          series: series,
          plotOptions: {},
        });
      });
    });
  });
};
