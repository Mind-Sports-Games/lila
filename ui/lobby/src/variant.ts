type variantConfirmsType = {
  chess960: string;
  kingOfTheHill: string;
  threeCheck: string;
  fiveCheck: string;
  antichess: string;
  atomic: string;
  horde: string;
  racingKings: string;
  crazyhouse: string;
  noCastling: string;
  monster: string;
  linesOfAction: string;
  scrambledEggs: string;
  frisian: string;
  frysk: string;
  antidraughts: string;
  breakthrough: string;
  shogi: string;
  minishogi: string;
  xiangqi: string;
  minixiangqi: string;
  flipello: string;
  flipello10: string;
  amazons: string;
  breakthroughtroyka: string;
  minibreakthroughtroyka: string;
  oware: string;
  togyzkumalak: string;
  bestemshe: string;
  go9x9: string;
  go13x13: string;
  go19x19: string;
  backgammon: string;
  hyper: string;
  nackgammon: string;
  abalone: string;
};

const variantConfirms: variantConfirmsType = {
  chess960: "This is a Chess960 game!\n\nThe starting position of the pieces on the players' home ranks is randomized.",
  kingOfTheHill: 'This is a King of the Hill game!\n\nThe game can be won by bringing the king to the center.',
  threeCheck: 'This is a Three-check game!\n\nThe game can be won by checking the opponent three times.',
  fiveCheck: 'This is a Five-check game!\n\nThe game can be won by checking the opponent five times.',
  antichess:
    'This is an Antichess game!\n\nIf you can take a piece, you must. The game can be won by losing all your pieces, or by being stalemated.',
  atomic:
    "This is an Atomic chess game!\n\nCapturing a piece causes an explosion, taking out your piece and surrounding non-pawns. Win by mating or exploding your opponent's king.",
  horde: 'This is a Horde chess game!\nBlack must take all White pawns to win. White must checkmate the Black king.',
  racingKings:
    'This is a Racing Kings game!\n\nPlayers must race their kings to the eighth rank. Checks are not allowed.',
  crazyhouse:
    'This is a Crazyhouse game!\n\nEvery time a piece is captured, the capturing player gets a piece of the same type and of their color in their pocket.',
  noCastling: 'This is a No Castling game!\n\nThe game is played the same as standard chess but you cannot castle.',
  monster:
    'This is a Monster chess game!\nWhite gets two moves every turn to compensate for their starting disadvantage.',
  linesOfAction:
    "This is a Lines Of Action game! The aim of the game is to connect all of one's pieces, with movement variable on the number of pieces in a line.",
  scrambledEggs:
    "This is a Scrambled Eggs game! The aim of the game is to connect all of one's pieces, with movement variable on the number of pieces in a line.",
  frisian: 'This is a Frisian draughts game!\n\nPieces can also capture horizontally and vertically.',
  frysk: 'This is a Frysk! game!\n\nFrisian draughts starting with 5 pieces each.',
  antidraughts:
    'This is an Antidraughts game!\n\nThe game can be won by losing all your pieces, or running out of moves.',
  breakthrough: 'This is a BRKTHRU game!\n\nThe first player who makes a king wins.',
  shogi: 'This is a Shogi game!\n\nThe aim of the game is to checkmate the opponent.',
  minishogi: 'This is a Mini Shogi game!\n\nThe aim of the game is to checkmate the opponent.',
  xiangqi: 'This is a Xiangqi game!\n\nThe aim of the game is to checkmate the opponent.',
  minixiangqi: 'This is a Mini Xiangqi game!\n\nThe aim of the game is to checkmate the opponent.',
  flipello:
    'This is a Othello game!\n\nPlayers take it in turns to place counters, flipping all opposition counters between their placed counter and another counter on the edge. The winner is the player who has the most counters face up at the end.',
  flipello10:
    'This is a Grand Othello game!\n\nPlayers take it in turns to place counters, flipping all opposition counters between their placed counter and another counter on the edge. The winner is the player who has the most counters face up at the end.',
  amazons: 'This is an Amazons game!\n\nImmobilize all the enemy pieces to win.',
  breakthroughtroyka:
    'This is a Breakthrough game!\n\nThe first player who reaches their last rank (opponent home row) wins.',
  minibreakthroughtroyka:
    'This is a Mini Breakthrough game!\n\nThe first player who reaches their last rank (opponent home row) wins.',
  oware: 'This is an Oware game!\n\nThe aim of the game is to capture the most stones.',
  togyzkumalak: 'This is a Togyzqumalaq game!\n\nThe aim of the game is to capture the most stones.',
  bestemshe: 'This is a Bestemshe game!\n\nThe aim of the game is to capture the most stones.',
  go9x9: 'This is a 9 by 9 Go game!\n\n The aim of the game is to surround the largest area(s) with your stones',
  go13x13: 'This is a 13 by 13 Go game!\n\n The aim of the game is to surround the largest area(s) with your stones',
  go19x19: 'This is a 19 by 19 Go game!\n\n The aim of the game is to surround the largest area(s) with your stones',
  backgammon:
    'This is a Backgammon game!\n\nThe aim of the game is to get all your pieces into your home, and then take them all off the board first.',
  hyper:
    'This is a Hyper Backgammon game!\n\nThe aim of the game is to get all your pieces into your home, and then take them all off the board first.',
  nackgammon:
    'This is a Nackgammon game!\n\nThe aim of the game is to get all your pieces into your home, and then take them all off the board first.',
  abalone:
    'This is a Abalone game!\n\nThe aim of the game is to be the first to push six of your opponents marbles off the board.',
};

function storageKey(key: string) {
  return 'lobby.variant.' + key;
}

export default function (variant: string) {
  return Object.keys(variantConfirms).every(function (key: keyof variantConfirmsType) {
    const v: string = variantConfirms[key];
    if (variant === key && !playstrategy.storage.get(storageKey(key))) {
      const c = confirm(v);
      if (c) playstrategy.storage.set(storageKey(key), '1');
      return c;
    } else return true;
  });
}
