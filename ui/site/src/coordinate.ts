// @ts-nocheck
import * as xhr from 'common/xhr';
import sparkline from '@fnando/sparkline';
import throttle from 'common/throttle';

playstrategy.load.then(() => {
  $('#trainer').each(function (this: HTMLElement) {
    const $trainer = $(this);
    const $board = $('.coord-trainer__board .cg-wrap');
    let ground;
    const $side = $('.coord-trainer__side');
    const $right = $('.coord-trainer__table');
    const $bar = $trainer.find('.progress_bar');
    const $coords = [$('#next_coord0'), $('#next_coord1')];
    const $start = $right.find('.start');
    const $explanation = $right.find('.explanation');
    const $score = $('.coord-trainer__score');
    const $timer = $('.coord-trainer__timer');
    const scoreUrl = $trainer.data('score-url');
    const duration = 30 * 1000;
    const tickDelay = 50;
    let playerIndexPref = $trainer.data('playerindex-pref');
    let playerIndex;
    let startAt, score;
    let wrongTimeout;

    const showPlayerIndex = function () {
      playerIndex = playerIndexPref == 'random' ? ['p1', 'p2'][Math.round(Math.random())] : playerIndexPref;
      if (!ground)
        ground = window.Chessground($board[0], {
          coordinates: false,
          drawable: { enabled: false },
          movable: {
            free: false,
            playerIndex: null,
          },
          orientation: playerIndex,
          myPlayerIndex: playerIndex,
          addPieceZIndex: $('#main-wrap').hasClass('is3d'),
        });
      else if (playerIndex !== ground.state.orientation) ground.toggleOrientation();
      $trainer.removeClass('p1 p2').addClass(playerIndex);
    };
    showPlayerIndex();

    $trainer.find('form.playerIndex').each(function (this: HTMLFormElement) {
      const form = this,
        $form = $(this);
      $form.find('input').on('change', function () {
        const selected = $form.find('input:checked').val() as string;
        const pi = {
          1: 'p1',
          2: 'random',
          3: 'p2',
        }[selected];
        if (pi !== playerIndexPref) xhr.formToXhr(form);
        playerIndexPref = pi;
        showPlayerIndex();
        return false;
      });
    });

    const setZen = throttle(1000, zen =>
      xhr.text('/pref/zen', {
        method: 'post',
        body: xhr.form({ zen: zen ? 1 : 0 }),
      }),
    );

    playstrategy.pubsub.on('zen', () => {
      const zen = !$('body').hasClass('zen');
      $('body').toggleClass('zen', zen);
      window.dispatchEvent(new Event('resize'));
      setZen(zen);
      requestAnimationFrame(showCharts);
    });

    window.Mousetrap.bind('z', () => playstrategy.pubsub.emit('zen'));

    $('#zentog').on('click', () => playstrategy.pubsub.emit('zen'));

    function showCharts() {
      $side.find('.user_chart').each(function (this: HTMLElement) {
        const $svg = $('<svg class="sparkline" height="80px" stroke-width="3">')
          .attr('width', $(this).width() + 'px')
          .prependTo($(this).empty());
        sparkline($svg[0] as unknown as SVGSVGElement, $(this).data('points'), {
          interactive: true,
          /* onmousemove(event, datapoint) { */
          /*   var svg = findClosest(event.target, "svg"); */
          /*   var tooltip = svg.nextElementSibling; */
          /*   var date = new Date(datapoint.date).toUTCString().replace(/^.*?, (.*?) \d{2}:\d{2}:\d{2}.*?$/, "$1"); */

          /*   tooltip.hidden = false; */
          /*   tooltip.textContent = `${date}: $${datapoint.value.toFixed(2)} USD`; */
          /*   tooltip.style.top = `${event.offsetY}px`; */
          /*   tooltip.style.left = `${event.offsetX + 20}px`; */
          /* }, */

          /* onmouseout() { */
          /*   var svg = findClosest(event.target, "svg"); */
          /*   var tooltip = svg.nextElementSibling; */

          /*   tooltip.hidden = true; */
          /* } */
          /* }; */
        });
      });
    }
    requestAnimationFrame(showCharts);

    const centerRight = function () {
      $right.css('top', 256 - $right.height() / 2 + 'px');
    };
    centerRight();

    const clearCoords = function () {
      $.each($coords, function (_, e) {
        e.text('');
      });
    };

    const newCoord = function (prevCoord) {
      // disallow the previous coordinate's row or file from being selected
      let files = 'abcdefgh';
      const fileIndex = files.indexOf(prevCoord[0]);
      files = files.slice(0, fileIndex) + files.slice(fileIndex + 1, 8);

      let rows = '12345678';
      const rowIndex = rows.indexOf(prevCoord[1]);
      rows = rows.slice(0, rowIndex) + rows.slice(rowIndex + 1, 8);

      return (
        files[Math.round(Math.random() * (files.length - 1))] + rows[Math.round(Math.random() * (rows.length - 1))]
      );
    };

    const advanceCoords = function () {
      $('#next_coord0').removeClass('nope');
      const lastElement = $coords.shift()!;
      $.each($coords, function (i, e) {
        e.attr('id', 'next_coord' + i);
      });
      lastElement.attr('id', 'next_coord' + $coords.length);
      lastElement.text(newCoord($coords[$coords.length - 1].text()));
      $coords.push(lastElement);
    };

    const stop = function () {
      clearCoords();
      $trainer.removeClass('play');
      centerRight();
      $trainer.removeClass('wrong');
      ground.set({
        events: {
          select: false,
        },
      });
      if (scoreUrl)
        xhr
          .text(scoreUrl, {
            method: 'post',
            body: xhr.form({ playerIndex, score }),
          })
          .then(charts => {
            $side.find('.scores').html(charts);
            showCharts();
          });
    };

    const tick = function () {
      const spent = Math.min(duration, new Date().getTime() - startAt);
      const left = ((duration - spent) / 1000).toFixed(1);
      if (+left < 10) {
        $timer.addClass('hurry');
      }

      $timer.text(left);
      $bar.css('width', (100 * spent) / duration + '%');
      if (spent < duration) setTimeout(tick, tickDelay);
      else stop();
    };

    $start.on('click', () => {
      $explanation.remove();
      $trainer.addClass('play').removeClass('init');
      $timer.removeClass('hurry');
      showPlayerIndex();
      clearCoords();
      centerRight();
      score = 0;
      $score.text(score);
      $bar.css('width', 0);
      setTimeout(function () {
        startAt = new Date();
        ground.set({
          events: {
            select(key) {
              const hit = key == $coords[0].text();
              if (hit) {
                score++;
                $score.text(score);
                advanceCoords();
              } else {
                clearTimeout(wrongTimeout);
                $trainer.addClass('wrong');

                wrongTimeout = setTimeout(function () {
                  $trainer.removeClass('wrong');
                }, 500);
              }
            },
          },
        });
        $coords[0].text(newCoord('a1'));
        let i;
        for (i = 1; i < $coords.length; i++) $coords[i].text(newCoord($coords[i - 1].text()));
        tick();
      }, 1000);
    });
  });
});
