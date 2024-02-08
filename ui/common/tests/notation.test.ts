import {
  parseUCISquareToUSI,
  readFen,
  moveFromNotationStyle,
  combinedNotationForBackgammonActions,
} from '../src/notation';

const shogiVariant = {
  key: 'shogi' as VariantKey,
  name: 'Shogi',
  short: 'Shogi',
  lib: 1,
  boardSize: { height: 9, width: 9 },
};

const xiangqiVariant = {
  key: 'xiangqi' as VariantKey,
  name: 'Xiangqi',
  short: 'Xiangqi',
  lib: 2,
  boardSize: { height: 10, width: 9 },
};

const owareVariant = {
  key: 'oware' as VariantKey,
  name: 'oware',
  short: 'oware',
  lib: 1,
  boardSize: { height: 2, width: 6 },
};

const backgammonVariant = {
  key: 'backgammon' as VariantKey,
  name: 'backgammon',
  short: 'backgammon',
  lib: 6,
  boardSize: { height: 2, width: 12 },
};

test('testing e4 maps to 56', () => {
  expect(parseUCISquareToUSI('e4', 9, 9)).toBe('56');
});

test('testing a6 maps to 94', () => {
  expect(parseUCISquareToUSI('a6', 9, 9)).toBe('94');
});

test('testing i6 maps to 14', () => {
  expect(parseUCISquareToUSI('i6', 9, 9)).toBe('14');
});

// Shogi

test('reading shogi fen and getting pieces', () => {
  const fen = 'lnsgkgsnl/2+P6/2+P2pbpp/9/1r7/pp3P3/PP1P2PPP/7R1/LNSGKGSNL[PPPPb] b - - 4 12';
  const board = readFen(fen, 9, 9);
  expect(board.pieces['91']).toBe('l');
});

test('reading shogi fen and getting pieces different drop notation', () => {
  const fen = 'lnsgkgsnl/2+P6/2+P2pbpp/9/1r7/pp3P3/PP1P2PPP/7R1/LNSGKGSNL/PPPb b - - 4 12';
  const board = readFen(fen, 9, 9);
  expect(board.pieces['91']).toBe('l');
});

test('moveFromNotationStyle shogi pawn move', () => {
  const move = {
    san: '',
    uci: 'e3e4',
    fen: 'lnsgkgsnl/1r5b1/ppppppppp/9/9/4P4/PPPP1PPPP/1B5R1/LNSGKGSNL[] b - - 1 1',
    prevFen: 'lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL[] w - - 0 1',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('P-56');
});

test('moveFromNotationStyle shogi Lance move', () => {
  const move = {
    san: '',
    uci: 'a1a2',
    fen: 'lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/LB5R1/1NSGKGSNL[] b - - 1 1',
    prevFen: 'lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL[] b - - 0 1',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('L-98');
});

test('moveFromNotationStyle shogi Knight move', () => {
  const move = {
    san: '',
    uci: 'h9g7',
    fen: 'lnsgkgs1l/1r5b1/ppppppnpp/6p2/9/2P6/PP1PPPPPP/LB5R1/1NSGKGSNL[] w - - 4 3',
    prevFen: 'lnsgkgsnl/1r5b1/pppppp1pp/6p2/9/2P6/PP1PPPPPP/LB5R1/1NSGKGSNL[] w - - 0 3',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('N-33');
});

test('moveFromNotationStyle shogi Rook move', () => {
  const move = {
    san: '',
    uci: 'h2e2',
    fen: 'lnsgkgs1l/1r5b1/ppppppnpp/6p2/9/2P6/PP1PPPPPP/LB2R4/1NSGKGSNL[] b - - 5 3',
    prevFen: 'lnsgkgs1l/1r5b1/ppppppnpp/6p2/9/2P6/PP1PPPPPP/LB5R1/1NSGKGSNL[] b - - 5 2',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('R-58');
});

test('moveFromNotationStyle shogi Silver move', () => {
  const move = {
    san: '',
    uci: 'g9f8',
    fen: 'lnsgkg2l/1r3s1b1/ppppppnpp/6p2/9/2P6/PP1PPPPPP/LB2R4/1NSGKGSNL[] w - - 6 4',
    prevFen: 'lnsgkgs1l/1r5b1/ppppppnpp/6p2/9/2P6/PP1PPPPPP/LB2R4/1NSGKGSNL[] w - - 6 4',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('S-42');
});

test('moveFromNotationStyle shogi Gold move', () => {
  const move = {
    san: '',
    uci: 'f1f2',
    fen: 'lnsgkg2l/1r3s1b1/ppppppnpp/6p2/9/2P6/PP1PPPPPP/LB2RG3/1NSGK1SNL[] b - - 7 4',
    prevFen: 'lnsgkg2l/1r3s1b1/ppppppnpp/6p2/9/2P6/PP1PPPPPP/LB2R4/1NSGKGSNL[] b - - 7 3',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('G-48');
});

test('moveFromNotationStyle shogi King move', () => {
  const move = {
    san: '',
    uci: 'e9d8',
    fen: 'lnsg1g2l/1r1k1s1b1/ppppppnpp/6p2/9/2P6/PP1PPPPPP/LB2RG3/1NSGK1SNL[] w - - 8 5',
    prevFen: 'lnsgkg2l/1r3s1b1/ppppppnpp/6p2/9/2P6/PP1PPPPPP/LB2RG3/1NSGK1SNL[] w - - 8 5',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('K-62');
});

test('moveFromNotationStyle shogi Bishop move', () => {
  const move = {
    san: '',
    uci: 'h8g9',
    fen: 'lnsg1gb1l/1r1k1s3/ppppppBpp/6p2/9/2P6/PP1PPPPPP/L3RG3/1NSGK1SNL[N] w - - 1 6',
    prevFen: 'lnsg1g2l/1r1k1s1b1/ppppppBpp/6p2/9/2P6/PP1PPPPPP/L3RG3/1NSGK1SNL[N] w - - 1 6',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('B-31');
});

test('moveFromNotationStyle shogi Dragon move', () => {
  const move = {
    san: '',
    uci: 'h8e5',
    fen: 'lnsg1gb1l/1r1k1s3/p1pppp1pp/1p4p2/4+B4/2P6/PP1PPPPPP/L3RG3/1NSGK1SNL[N] b - - 4 7',
    prevFen: 'lnsg1gb1l/1r1k1s1+B1/p1pppp1pp/1p4p2/9/2P6/PP1PPPPPP/L3RG3/1NSGK1SNL[N] b - - 4 6',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('+B-55');
});

//shogi, requiring the previous fen for captures, drops and promotion

test('moveFromNotationStyle shogi Pawn capture', () => {
  const move = {
    san: '',
    uci: 'e6e5',
    fen: 'lnsgkgsnl/1r5b1/pppp1pppp/9/4p4/9/PPPP1PPPP/1B5R1/LNSGKGSNL[p] w - - 0 3',
    prevFen: 'lnsgkgsnl/1r5b1/pppp1pppp/4p4/4P4/9/PPPP1PPPP/1B5R1/LNSGKGSNL[] b - - 3 2',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('Px55');
});

test('moveFromNotationStyle shogi Pawn drop', () => {
  const move = {
    san: '',
    uci: 'P@e6',
    fen: 'lnsgkgsnl/1r5b1/pppp1pppp/4p4/9/4R4/PPPP1PPPP/1B7/LNSGKGSNL[P] w - - 1 5',
    prevFen: 'lnsgkgsnl/1r5b1/pppp1pppp/9/9/4R4/PPPP1PPPP/1B7/LNSGKGSNL[Pp] b - - 0 4',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('P*54');
});

test('moveFromNotationStyle shogi Rook promotion choice yes', () => {
  const move = {
    san: '',
    uci: 'e6e7',
    fen: 'lnsg1gsnl/1r3k1b1/pppp+Rpppp/9/9/9/PPPP1PPPP/1B7/LNSGKGSNL[PP] b - - 2 6',
    prevFen: 'lnsg1gsnl/1r3k1b1/pppp1pppp/4R4/9/9/PPPP1PPPP/1B7/LNSGKGSNL[PP] w - - 1 6',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('R-53+');
});

test('moveFromNotationStyle shogi Rook promotion choice no', () => {
  const move = {
    san: '',
    uci: 'e6e7',
    fen: 'lnsg1gsnl/1r3k1b1/ppppRpppp/9/9/9/PPPP1PPPP/1B7/LNSGKGSNL[PP] b - - 2 6',
    prevFen: 'lnsg1gsnl/1r3k1b1/pppp1pppp/4R4/9/9/PPPP1PPPP/1B7/LNSGKGSNL[PP] w - - 1 6',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('R-53=');
});

test('moveFromNotationStyle shogi Dragon moving no promotion', () => {
  const move = {
    san: '',
    uci: 'e7d7',
    fen: 'lnsg1gsnl/1r4kb1/ppp+R1pppp/9/9/9/PPPP1PPPP/1B7/LNSGKGSNL[PPP] b - - 0 7',
    prevFen: 'lnsg1gsnl/1r4kb1/pppp+Rpppp/9/9/9/PPPP1PPPP/1B7/LNSGKGSNL[PP] w - - 3 7',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('+Rx63');
});

test('moveFromNotationStyle shogi Knight drop no promotion', () => {
  const move = {
    san: '',
    uci: 'N@b7',
    fen: 'lnsg2s1l/1r2g1kb1/1Np+R1p1pp/1p7/p2N5/2P6/PP1P1PNPP/9/L1SGKGS1L[PPPppb] b - - 2 14',
    prevFen: 'lnsg2s1l/1r2g1kb1/2p+R1p1pp/1p7/p2N5/2P6/PP1P1PNPP/9/L1SGKGS1L[NPPPppb] w - - 1 14',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('N*83');
});

test('moveFromNotationStyle shogi Lance capture and promotion', () => {
  const move = {
    san: '',
    uci: 'i9i1',
    fen: 'lnsg2sk1/4g2b1/1rp4p1/1p3+R3/p2N5/2P6/PP1P1PNP1/9/L1SGKGS1+l[PPPPPnlpppb] w - - 0 22',
    prevFen: 'lnsg2skl/4g2b1/1rp4p1/1p3+R3/p2N5/2P6/PP1P1PNP1/9/L1SGKGS1L[PPPPPnpppb] b - - 2 21',
  };

  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('Lx19+');
});

//shogi, ambiguous moves - required orig

test('moveFromNotationStyle shogi gold ambiguous', () => {
  const move = {
    san: '',
    uci: 'f1e2',
    fen: 'lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B2G2R1/LNSGK1SNL w - 2',
    prevFen: 'lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1',
  };
  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('G49-58');
});

test('moveFromNotationStyle shogi Knight ambiguous', () => {
  const move = {
    san: '',
    uci: 'f5e7',
    fen: 'lnsgkgsnl/1r5b1/ppp1+Nppp1/3p4p/3N5/2P3P2/PP1P+pP1PP/1B5R1/L1SGKGS1L w p 14',
    prevFen: 'lnsgkgsnl/1r5b1/ppp2ppp1/3p4p/3N1N3/2P3P2/PP1P+pP1PP/1B5R1/L1SGKGS1L b p 13',
  };
  const notation = moveFromNotationStyle('usi')(move, shogiVariant);
  expect(notation).toBe('N45-53+');
});

// xiangqi

test('moveFromNotationStyle xiangqi Pawn move', () => {
  const move = {
    san: '',
    uci: 'e4e5',
    fen: 'rnbakabnr/9/1c5c1/p1p1p1p1p/9/4P4/P1P3P1P/1C5C1/9/RNBAKABNR b - - 1 1',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('P5+1');
});

test('moveFromNotationStyle xiangqi knight move', () => {
  const move = {
    san: '',
    uci: 'b10c8',
    fen: 'r1bakabnr/9/1cn4c1/p1p1p1p1p/9/9/P1P1P1P1P/1C4NC1/9/RNBAKAB1R w - - 2 2',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('H2+3');
});

test('moveFromNotationStyle xiangqi Elephant move', () => {
  const move = {
    san: '',
    uci: 'g1e3',
    fen: 'r1bakabnr/9/1cn4c1/p1p1p1p1p/9/9/P1P1P1P1P/1C2B1NC1/9/RNBAKA2R b - - 3 2',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('E3+5');
});

test('moveFromNotationStyle xiangqi Advisor move', () => {
  const move = {
    san: '',
    uci: 'f10e9',
    fen: 'rnbak1bnr/4a4/1c5c1/p1p1p1p1p/9/4P4/P1P3P1P/1C5C1/9/RNBAKABNR w - - 2 2',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('A6+5');
});

test('moveFromNotationStyle xiangqi King move', () => {
  const move = {
    san: '',
    uci: 'e9f9',
    fen: 'rnba1abnr/5k3/1c5c1/p1p1p1p1p/9/4P4/P1P3P1P/1C5C1/4K4/RNBA1ABNR w - - 4 3',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('K5=6');
});

test('moveFromNotationStyle xiangqi Rook move', () => {
  const move = {
    san: '',
    uci: 'i1i3',
    fen: 'rnba1abnr/5k3/1c5c1/p1p1p1p1p/9/4P4/P1P3P1P/1C5CR/4K4/RNBA1ABN1 b - - 5 3',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('R1+2');
});

test('moveFromNotationStyle xiangqi Cannon move', () => {
  const move = {
    san: '',
    uci: 'b8b1',
    fen: 'rnba1abnr/5k3/7c1/p1p1p1p1p/9/4P4/P1P3P1P/1C5CR/4K4/RcBA1ABN1 w - - 0 4',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('C2+7');
});

test('moveFromNotationStyle xiangqi Promoted pawn move', () => {
  const move = {
    san: '',
    uci: 'e7d7',
    fen: 'rnba1abnr/5k3/7c1/3P2p1p/p1p6/9/P1P3P1P/1C5CR/4K4/RcBA1ABN1 b - - 2 6',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('P5=6');
});

test('moveFromNotationStyle xiangqi 2 pawns in column move', () => {
  const move = {
    san: '',
    uci: 'c7d7',
    fen: 'rnb2abnr/4ak3/7c1/3P2p1p/9/p1P6/P5P1P/1C5CR/4K4/RcBA1ABN1 b - - 4 9',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('P+=6');
});

test('moveFromNotationStyle xiangqi 3 pawns in column move', () => {
  const move = {
    san: '',
    uci: 'c7c8',
    fen: 'rnb2abnr/4ak3/2P4c1/6p1p/2P6/2P6/6P1P/1C5CR/4K4/RcBA1ABN1 b - - 10 15',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('17+1');
});

test('moveFromNotationStyle xiangqi 3 pawns in column sideways move', () => {
  const move = {
    san: '',
    uci: 'c6d6',
    fen: '1nb2abnr/4ak3/r1P4c1/6p1p/3P5/2P6/6P1P/1C5CR/4K4/RcBA1ABN1 b - - 12 16',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('27=6');
});

test('moveFromNotationStyle xiangqi 3 pawns in column p2 move', () => {
  const move = {
    san: '',
    uci: 'g3g2',
    fen: 'rnbakabnr/9/1c5c1/p1P6/6p2/P5p2/8P/1C5C1/4A1p2/RNB1KABNR w - - 11 13',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('17+1');
});

test('moveFromNotationStyle xiangqi 3 pawns in column p2 sideways move', () => {
  const move = {
    san: '',
    uci: 'g5f5',
    fen: 'rnbakabnr/9/1c5c1/p1P6/6p2/P4p3/8P/1CN4C1/4A1p2/R1B1KABNR w - - 13 14',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('27=6');
});

test('moveFromNotationStyle xiangqi 4 pawns in column p2 move', () => {
  const move = {
    san: '',
    uci: 'g6g5',
    fen: 'rnbakabnr/9/1c5c1/1CP6/9/6p2/R5p1P/2N3pC1/4A4/2B1KApNR w - - 6 26',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('47+1');
});

test('moveFromNotationStyle xiangqi 4 pawns in column p2 sideways move', () => {
  const move = {
    san: '',
    uci: 'g4f4',
    fen: 'rnbakabnr/9/1c5c1/1CP6/9/1N4p2/R4p2P/6pC1/4A4/2B1KApNR w - - 8 27',
    prevFen: '',
  };

  const notation = moveFromNotationStyle('wxf')(move, xiangqiVariant);
  expect(notation).toBe('37=6');
});

//Oware
test('moveFromNotationStyle oware c1 pos stone from starting', () => {
  const move = { san: '', uci: 'c1f2', fen: 'DDDDDD/DDDDDD 0 0 S', prevFen: 'DDDDDD/DDDDDD 0 0 S' };

  const notation = moveFromNotationStyle('man')(move, owareVariant);
  expect(notation).toBe('C');
});

test('moveFromNotationStyle oware b2 pos stone', () => {
  const move = { san: '', uci: 'b2c1', fen: 'DDDCDD/DDEDDD 0 0 N', prevFen: 'DDDDDD/DDDDDD 0 0 S' };

  const notation = moveFromNotationStyle('man')(move, owareVariant);
  expect(notation).toBe('e');
});

//Backgammon
test('moveFromNotationStyle backgammon a2c1 from starting', () => {
  const move = {
    san: '',
    uci: 'a2c1',
    fen: '4S,3,3s,1,5s,4,2S/5s,1,S,1,3S,1,5S,4,2s[] 5 3 w 0 0 1',
    prevFen: '5S,3,3s,1,5s,4,2S/5s,3,3S,1,5S,4,2s[] 3/5 - w 0 0 1',
  };

  const notation = moveFromNotationStyle('bkg')(move, backgammonVariant);
  expect(notation).toBe('35: 13/10');
});

test('moveFromNotationStyle backgammon a1f2 p2 from starting', () => {
  const move = {
    san: '',
    uci: 'a1f2',
    fen: '5S,3,3s,s,5s,4,2S/4s,3,3S,1,5S,4,2s[] 6/6/6 6 b 0 0 1',
    prevFen: '5S,3,3s,1,5s,4,2S/5s,3,3S,1,5S,4,2s[] 6/6/6/6 - b 0 0 1',
  };

  const notation = moveFromNotationStyle('bkg')(move, backgammonVariant);
  expect(notation).toBe('66: 13/7');
});

test('moveFromNotationStyle backgammon testing drop S@i2', () => {
  const move = {
    san: '',
    uci: 'S@i2',
    fen: '4S,3,3s,1,5s,1,S,2,2S/5s,3,3S,1,5S,4,2s[] 3 4 w 0 0 1',
    prevFen: '4S,3,3s,1,5s,4,2S/5s,3,3S,1,5S,4,2s[1S] 4/3 - w 0 0 1',
  };

  const notation = moveFromNotationStyle('bkg')(move, backgammonVariant);
  expect(notation).toBe('43: bar/21');
});

test('moveFromNotationStyle backgammon testing captures', () => {
  const move = {
    san: '',
    uci: 'a2c1',
    fen: '4S,3,3s,1,5s,4,2S/5s,1,S,1,3S,1,5S,4,2s[1s] 6 3 w 0 0 1',
    prevFen: '5S,3,3s,1,5s,4,2S/4s,1,s,1,3S,1,5S,4,2s[] 6/3 - w 0 0 1',
  };

  const notation = moveFromNotationStyle('bkg')(move, backgammonVariant);
  expect(notation).toBe('63: 13/10*');
});

test('moveFromNotationStyle backgammon testing captures 2', () => {
  const move = {
    san: '',
    uci: 'a2c1',
    fen: '4S,3,3s,1,5s,4,2S/5s,1,S,1,3S,1,5S,4,1s[2s] - 3/6 w 0 0 1',
    prevFen: '5S,3,3s,1,5s,4,2S/4s,1,s,1,3S,1,5S,4,1s[1s] 3 6 w 0 0 1',
  };

  const notation = moveFromNotationStyle('bkg')(move, backgammonVariant);
  expect(notation).toBe('36: 13/10*');
});

test('moveFromNotationStyle backgammon testing lift', () => {
  const move = {
    san: '',
    uci: '^g1',
    fen: '6,15s,5/6,S,2S,2S,3S,3S,3S[] 3 6 w 1 0 10',
    prevFen: '6,15s,5/6,2S,2S,2S,3S,3S,3S[] 3/6 - w 0 0 10',
  };

  const notation = moveFromNotationStyle('bkg')(move, backgammonVariant);
  expect(notation).toBe('36: 6/off');
});

test('moveFromNotationStyle backgammon testing capture', () => {
  const move = {
    san: '',
    uci: 'g2h2',
    fen: '3S,3,3s,1S,3s,1s,2,2s,1S/3s,1S,2,3S,1S,4S,1s,3,2s[1S] 1 1/1/1 b 0 0 1',
    prevFen: '3S,3,3s,1S,4s,3,2s,1S/3s,1S,2,3S,1S,4S,1s,3,2s[1S] 1/1 1/1 b 0 0 1',
  };

  const notation = moveFromNotationStyle('bkg')(move, backgammonVariant);
  expect(notation).toBe('11: 6/5');
});

test('combinedNotationForBackgammonActions with 2 same actions', () => {
  const actions = ['44:', '44: 8/4', '44: 8/4'];

  const notation = combinedNotationForBackgammonActions(actions);
  expect(notation).toBe('44: 8/4(2)');
});

test('combinedNotationForBackgammonActions with 4 actions and captures', () => {
  const actions = ['33:', '33: 8/4*', '33: bar/20*', '33: 8/4', '33: 8/7'];

  const notation = combinedNotationForBackgammonActions(actions);
  expect(notation).toBe('33: 8/4(2)* bar/20* 8/7');
});

//Cant actually do these 4 moves in a games but still okay for testing notation
test('combinedNotationForBackgammonActions with 4 actions and captures', () => {
  const actions = ['33:', '33: 8/4*', '33: bar/20*', '33: 8/4', '33: 3/off'];

  const notation = combinedNotationForBackgammonActions(actions);
  expect(notation).toBe('33: 8/4(2)* bar/20* 3/off');
});

test('combinedNotationForBackgammonActions with 1 capture and 1 non capture', () => {
  const actions = ['34:', '34: 8/4*', '34: 10/7'];

  const notation = combinedNotationForBackgammonActions(actions);
  expect(notation).toBe('34: 8/4* 10/7');
});

//Hit and move (https://backgammon-hub.com/how-to-read-and-use-backgammon-notation/)
//We dont follow backgmmon hub but instead split out moves as easier to manage
test('combinedNotationForBackgammonActions with 4 actions and captures', () => {
  const actions = ['53:', '53: 13/8*', '53: 8/5'];

  const notation = combinedNotationForBackgammonActions(actions);
  expect(notation).toBe('53: 13/8* 8/5');
  //expect(notation).toBe('53: 13/8*/5'); // notation on bg-hub
});
