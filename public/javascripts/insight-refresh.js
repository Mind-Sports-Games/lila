playstrategy.refreshInsightForm = () => {
  $('form.insight-refresh:not(.armed)')
    .addClass('armed')
    .on('submit', function () {
      fetch(this.action, {
        method: 'post',
        credentials: 'same-origin',
      }).then(playstrategy.reload);
      $(this).replaceWith($(this).find('.crunching').show());
      return false;
    });
};
playstrategy.load.then(playstrategy.refreshInsightForm);
