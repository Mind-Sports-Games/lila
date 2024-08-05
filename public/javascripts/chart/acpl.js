function toBlurArray(player) {
  return player.blurs && player.blurs.bits ? player.blurs.bits.split('') : [];
}
playstrategy.advantageChart = function (data, trans, el) {
  playstrategy.loadScriptCJS('javascripts/chart/common.js').then(function () {
    playstrategy.loadScriptCJS('javascripts/chart/division.js').then(function () {
      playstrategy.chartCommon('highchart').then(function () {
        playstrategy.advantageChart.update = function (d) {
          el.highcharts && el.highcharts.series[0].setData(makeSerieData(d));
        };

        var blurs = [toBlurArray(data.player), toBlurArray(data.opponent)];
        if (data.player.playerIndex === 'p1') blurs.reverse();

        var fillColor = Highcharts.theme.playstrategy.area.white;
        var negativeFillColor = Highcharts.theme.playstrategy.area.black;
        var oppositeColorVariants = ['flipello', 'flipello10', 'shogi', 'minishogi'];
        if (
          oppositeColorVariants.find(function (k) {
            return k == data.game.variant.key;
          })
        ) {
          fillColor = Highcharts.theme.playstrategy.area.black;
          negativeFillColor = Highcharts.theme.playstrategy.area.white;
        }

        var makeSerieData = function (d) {
          var twoPlysPerTurnVariants = ['amazons'];
          var colorForPly = function (ply) {
            if (
              twoPlysPerTurnVariants.find(function (k) {
                return k === data.game.variant.key;
              })
            ) {
              return (ply / 2) & 1;
            } else {
              return ply & 1;
            }
          };

          var partial = !d.analysis || d.analysis.partial;
          return d.treeParts.slice(1).map(function (node) {
            var color = colorForPly(node.ply),
              cp;

            var san = node.san;
            if (san === 'NOSAN') san = node.uci;

            if (node.eval && node.eval.mate) {
              cp = node.eval.mate > 0 ? Infinity : -Infinity;
            } else if (san.includes('#')) {
              cp = color === 1 ? Infinity : -Infinity;
              if (d.game.variant.key === 'antichess') cp = -cp;
            } else if (node.eval && typeof node.eval.cp !== 'undefined') {
              cp = node.eval.cp;
            } else
              return {
                y: null,
              };

            var turn = Math.floor((node.ply - 1) / 2) + 1;
            var dots = color === 1 ? '.' : '...';
            var point = {
              name: turn + dots + ' ' + san,
              y: 2 / (1 + Math.exp(-0.004 * cp)) - 1,
            };
            if (!partial && blurs[color].shift() === '1') {
              point.marker = {
                symbol: 'square',
                radius: 3,
                lineWidth: '1px',
                lineColor: '#d85000',
                fillColor: color ? '#fff' : '#333',
              };
              point.name += ' [blur]';
            }
            return point;
          });
        };

        var disabled = {
          enabled: false,
        };
        var noText = {
          text: null,
        };
        var serieData = makeSerieData(data);
        el.highcharts = Highcharts.chart(el, {
          credits: disabled,
          legend: disabled,
          series: [
            {
              name: trans('advantage'),
              data: serieData,
            },
          ],
          chart: {
            type: 'area',
            spacing: [3, 0, 3, 0],
            animation: false,
          },
          plotOptions: {
            series: {
              animation: false,
            },
            area: {
              fillColor: fillColor,
              negativeFillColor: negativeFillColor,
              threshold: 0,
              lineWidth: 1,
              color: '#d85000',
              allowPointSelect: true,
              cursor: 'pointer',
              states: {
                hover: {
                  lineWidth: 1,
                },
              },
              events: {
                click: function (event) {
                  if (event.point) {
                    event.point.select();
                    playstrategy.pubsub.emit('analysis.chart.click', event.point.x);
                  }
                },
              },
              marker: {
                radius: 1,
                states: {
                  hover: {
                    radius: 4,
                    lineColor: '#d85000',
                  },
                  select: {
                    radius: 4,
                    lineColor: '#d85000',
                  },
                },
              },
            },
          },
          tooltip: {
            pointFormatter: function (format) {
              format = format.replace('{series.name}', trans('advantage'));
              var eval = data.treeParts[this.x + 1].eval;
              if (!eval) return;
              else if (eval.mate) return format.replace('{point.y}', '#' + eval.mate);
              else if (typeof eval.cp !== 'undefined') {
                var e = Math.max(Math.min(Math.round(eval.cp / 10) / 10, 99), -99);
                if (e > 0) e = '+' + e;
                return format.replace('{point.y}', e);
              }
            },
          },
          title: noText,
          xAxis: {
            title: noText,
            labels: disabled,
            lineWidth: 0,
            tickWidth: 0,
            plotLines: playstrategy.divisionLines(data.game.division, trans),
          },
          yAxis: {
            title: noText,
            min: -1.1,
            max: 1.1,
            startOnTick: false,
            endOnTick: false,
            labels: disabled,
            lineWidth: 1,
            gridLineWidth: 0,
            plotLines: [
              {
                color: Highcharts.theme.playstrategy.text.weak,
                width: 1,
                value: 0,
              },
            ],
          },
        });
        playstrategy.pubsub.emit('analysis.change.trigger');
      });
    });
  });
};
