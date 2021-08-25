type Board = { pieces: { [key: number]: string }; turn: boolean };
export type SanToUci = { [key: string]: Uci };

function decomposeUci(uci: string): Array<Uci> {
  return [uci.slice(0, 2), uci.slice(2, 4)];
}

function readFen(fen: string) {
  const fenParts = fen.split(':');
  const board: Board = {
    pieces: {},
    turn: fenParts[0] === 'W',
  };

  for (let i = 0; i < fenParts.length; i++) {
    const clr = fenParts[i].slice(0, 1);
    if ((clr === 'W' || clr === 'B') && fenParts[i].length > 1) {
      const fenPieces = fenParts[i].slice(1).split(',');
      for (let k = 0; k < fenPieces.length; k++) {
        let fieldNumber = fenPieces[k].slice(1);
        const role = fenPieces[k].slice(0, 1);
        if (fieldNumber.length !== 0 && role.length !== 0) {
          if (fieldNumber.length == 1) fieldNumber = '0' + fieldNumber;
          const fieldNumberNumber = parseInt(fieldNumber);
          board.pieces[fieldNumberNumber] = role;
        }
      }
    }
  }

  return board;
}

function shorten(uci: string) {
  return uci && uci.startsWith('0') ? uci.slice(1) : uci;
}

function sanOf(_board: Board, uci: string, capture: boolean) {
  const move = decomposeUci(uci);
  if (capture) return shorten(move[0]) + 'x' + shorten(move[1]);
  else return shorten(move[0]) + '-' + shorten(move[1]);
}

export default function sanWriter(fen: string, ucis: string[], captLen?: number): SanToUci {
  const board = readFen(fen);
  const capture = !!captLen && captLen > 0;
  const sans: SanToUci = {};
  ucis.forEach(function (uci) {
    const san = sanOf(board, uci, capture);
    sans[san] = uci;
  });
  return sans;
}
