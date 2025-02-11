playstrategy.load.then(() => {
  $('#memory-app').each(function (this: HTMLElement) {
    //const $memoryApp = $(this);
    const $memoryGrid = $('.memory-grid');
    const $memoryButtons = $('.memory-buttons');
    const $playAgain = $memoryButtons.find('.start');
    const $memoryCards = $memoryGrid.find('.memory-card');

    // Game Logic
    let tries = 0;
    let firstCard, secondCard;
    let lockBoard = false;
    let hasFlippedCard = false;
    let pairsFound = 0;

    function flipCard(card: HTMLElement) {
      if (lockBoard) return;
      if (card.classList.contains('flip')) return;
      if (card === firstCard) return;

      $(card).addClass('flip');

      if (!hasFlippedCard) {
        hasFlippedCard = true;
        firstCard = card;
        return;
      }

      secondCard = card;
      tries += 1;
      $('#moves').text(`Moves: ${tries}`);
      checkForMatch();
    }

    const checkForMatch = () => {
      const isMatch = firstCard.dataset.framework === secondCard.dataset.framework;
      isMatch ? disableCards() : unflipCards();
    };

    const disableCards = () => {
      pairsFound += 1;
      if (pairsFound === 8) {
        $('#moves').text(`You found all the pairs in ${tries} moves!`);
      }

      resetBoard();
    };

    const unflipCards = () => {
      lockBoard = true;

      setTimeout(() => {
        firstCard.classList.remove('flip');
        secondCard.classList.remove('flip');

        resetBoard();
      }, 1500);
    };

    const resetBoard = () => {
      [hasFlippedCard, lockBoard] = [false, false];
      [firstCard, secondCard] = [null, null];
    };

    //shuffle cards
    (function shuffle() {
      $memoryCards.each(function (this: HTMLElement) {
        const randomPos = Math.floor(Math.random() * 16);
        $(this).css('order', randomPos);
      });
    })();

    const icons = [
      '⁄',
      "'",
      '(',
      '',
      '',
      '@',
      '>',
      '_',
      '',
      '',
      '',
      '',
      '',
      '',
      '',
      '',
      '',
      '',
      '',
      '‹',
      'K',
      '',
      '',
      '',
      '',
      '',
      '',
      '',
      '',
      '€',
      '',
      '',
      '',
      '›',
      '',
      '',
      '',
      '',
      '',
      '',
      '',
      '\ue927',
    ];
    const shuffledIcons = icons.sort(() => Math.random() - 0.5);

    //setup cards
    $memoryCards.each(function (this: HTMLElement) {
      $(this).on('click', () => flipCard(this));
      $(this).find('.front-face').attr('data-icon', shuffledIcons[this.dataset.framework]);
    });

    $playAgain.on('click', () => {
      window.location.reload();
    });
  });
});
