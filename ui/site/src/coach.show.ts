import rate from 'common/rate';

// this file does not seem to be loaded from anywhere
playstrategy.load.then(() => {
  $('.coach-review-form .toggle').on('click', function (this: HTMLElement) {
    $(this).remove();
    $('.coach-review-form form').show();
    $('select.rate').each(function (this: HTMLSelectElement) {
      rate(this);
    });
  });
});
