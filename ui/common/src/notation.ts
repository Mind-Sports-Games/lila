import { NotationStyle } from 'stratutils';

interface ExtendedMoveInfo {
  san: string;
  uci: string;
  fen: string;
  prevFen: string;
}

interface ParsedMove {
  dest: string;
  orig: string;
}

type Board = { pieces: { [key: string]: string }; wMoved: boolean };

export function moveFromNotationStyle(notation: NotationStyle): (move: ExtendedMoveInfo, variant: Variant) => string {
  switch (notation) {
    case 'wxf':
      return xiangqiNotation;
    case 'usi':
      return shogiNotation;
    case 'san':
      return move => (move.san[0] === 'P' ? move.san.slice(1) : move.san);
    case 'uci':
      return (move, variant) => (variant.key === 'amazons' && move.uci[0] === 'P' ? move.uci.slice(1) : move.uci);
    case 'dpo':
      return destPosOthello;
    case 'dpg':
      return destPosGo;
    case 'man':
      return mancalaNotation;
    case 'bkg':
      return backgammonNotation;
  }
}

/*
 ** reads in a fen and outputs a map of board pieces - coordinates/keys are that of a shogi board [file+rank]
 */
export function readFen(fen: string, ranks: number, files: number) {
  const parts = fen.split(' '),
    board: Board = {
      pieces: {},
      wMoved: parts[1] === 'b',
    };

  parts[0]
    .split('[')[0]
    .split('/')
    .slice(0, ranks)
    .forEach((row, y) => {
      let x = files;
      let promoted = false;
      row.split('').forEach(v => {
        if (v == '~') return;
        const nb = parseInt(v, 10);
        if (nb) x -= nb;
        else if (v == '+') promoted = true;
        else {
          if (promoted) {
            board.pieces[`${x}${y + 1}`] = '+' + v;
          } else {
            board.pieces[`${x}${y + 1}`] = v;
          }
          x--;
          promoted = false;
        }
      });
    });

  return board;
}

function parseUciToUsi(uci: string, files: number, ranks: number): ParsedMove {
  //account for ranks going up to 10, files are just a letter
  const reg = uci.match(/[a-zA-Z][1-9@]0?/g) as string[];
  return {
    orig: parseUCISquareToUSI(reg[0], files, ranks)!,
    dest: parseUCISquareToUSI(reg[1], files, ranks)!,
  };
}

export function parseUCISquareToUSI(str: string, files: number, ranks: number): string | undefined {
  if (str.length > 3) return;
  const file = files - Math.abs(str.charCodeAt(0) - 'a'.charCodeAt(0));
  const rank = ranks + 1 - parseInt(str.slice(1));
  if (file < 1 || file > files || rank < 1 || rank > ranks) return;
  return file.toString() + rank.toString();
}

function shogiNotation(move: ExtendedMoveInfo, variant: Variant): string {
  const parsed = parseUciToUsi(move.uci, variant.boardSize.width, variant.boardSize.height),
    board = readFen(move.fen, variant.boardSize.height, variant.boardSize.width),
    prevBoard = readFen(move.prevFen, variant.boardSize.height, variant.boardSize.width),
    prevrole = prevBoard.pieces[parsed.orig],
    dest = parsed.dest,
    connector = isCapture(prevBoard, board) ? 'x' : isDrop(prevBoard, board) ? '*' : '-',
    role = board.pieces[dest],
    piece = role[0] === '+' ? role[0] + role[1].toUpperCase() : role[0].toUpperCase(),
    origin = !isDrop(prevBoard, board) && isMoveAmbiguous(board, parsed.dest, prevrole) ? parsed.orig : '', //ToDo ideally calculate this from SAN or in stratops as currently doesn't include illegal moves like piece being pinned or obstruction
    promotion = promotionSymbol(prevBoard, board, parsed);

  if (promotion == '+') return `${piece.slice(1)}${origin}${connector}${dest}${promotion}`;

  return `${piece}${origin}${connector}${dest}${promotion}`;
}

function isMoveAmbiguous(board: Board, dest: string, prevRole: string): boolean {
  const locations: string[] = previousLocationsOfPiece(prevRole, dest);
  const possibleRoles = locations.map(l => board.pieces[l]).filter(x => x != undefined);
  return possibleRoles.includes(prevRole);
}

function previousLocationsOfPiece(role: string, dest: string): string[] {
  // illegal positions will just return nothing from board.piece[l] therefore dont check
  // doesn't account for pins or obstruction
  // dest is file + rank ,each single digit 1-9.
  const sb: string[] = [];
  switch (role) {
    case 'N':
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) + 2).toString());
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) + 2).toString());
      break; // n-piece (p1)

    case 'n':
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) - 2).toString());
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) - 2).toString());
      break; // n-piece (p2)

    case 'S':
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) - 1).toString());
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) - 1).toString());
      break; // s-piece (p1)

    case 's':
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) - 1).toString());
      sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) - 1).toString());
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) - 1).toString());
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) + 1).toString());
      break; // s-piece (p2)

    case 'b':
    case 'B':
      [1, 2, 3, 4, 5, 6, 7, 8].forEach(i => {
        sb.push((parseInt(dest[0]) + i).toString() + (parseInt(dest[1]) + i).toString());
        sb.push((parseInt(dest[0]) + i).toString() + (parseInt(dest[1]) - i).toString());
        sb.push((parseInt(dest[0]) - i).toString() + (parseInt(dest[1]) + i).toString());
        sb.push((parseInt(dest[0]) - i).toString() + (parseInt(dest[1]) - i).toString());
      });
      break; // b-piece

    case 'r':
    case 'R':
      [1, 2, 3, 4, 5, 6, 7, 8].forEach(i => {
        sb.push((parseInt(dest[0]) + i).toString() + (parseInt(dest[1]) + 0).toString());
        sb.push((parseInt(dest[0]) - i).toString() + (parseInt(dest[1]) + 0).toString());
        sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) + i).toString());
        sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) - i).toString());
      });
      break; // r-piece

    case '+P':
    case '+L':
    case '+N':
    case '+S':
    case 'G':
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) + 0).toString());
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) + 0).toString());
      sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) - 1).toString());
      break; // g-piece (p1)

    case '+s':
    case '+n':
    case '+l':
    case '+p':
    case 'g':
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) - 1).toString());
      sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) - 1).toString());
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) - 1).toString());
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) + 0).toString());
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) + 0).toString());
      sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) + 1).toString());
      break; // g-piece (p2)

    case '+b':
    case '+B':
      [1, 2, 3, 4, 5, 6, 7, 8].forEach(i => {
        sb.push((parseInt(dest[0]) + i).toString() + (parseInt(dest[1]) + i).toString());
        sb.push((parseInt(dest[0]) + i).toString() + (parseInt(dest[1]) - i).toString());
        sb.push((parseInt(dest[0]) - i).toString() + (parseInt(dest[1]) + i).toString());
        sb.push((parseInt(dest[0]) - i).toString() + (parseInt(dest[1]) - i).toString());
      });
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) + 0).toString());
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) + 0).toString());
      sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) - 1).toString());
      break; // pb-piece

    case '+r':
    case '+R':
      [1, 2, 3, 4, 5, 6, 7, 8].forEach(i => {
        sb.push((parseInt(dest[0]) + i).toString() + (parseInt(dest[1]) + 0).toString());
        sb.push((parseInt(dest[0]) - i).toString() + (parseInt(dest[1]) + 0).toString());
        sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) + i).toString());
        sb.push((parseInt(dest[0]) + 0).toString() + (parseInt(dest[1]) - i).toString());
      });
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) + 1).toString());
      sb.push((parseInt(dest[0]) + 1).toString() + (parseInt(dest[1]) - 1).toString());
      sb.push((parseInt(dest[0]) - 1).toString() + (parseInt(dest[1]) - 1).toString());
      break; // pr-piece

    default:
    //nothing k-piece, p-piece, l-piece
  }
  return sb;
}

function isCapture(prevBoard: Board, board: Board): boolean {
  return Object.keys(prevBoard.pieces).length - Object.keys(board.pieces).length == 1;
}

function isDrop(prevBoard: Board, board: Board): boolean {
  return Object.keys(prevBoard.pieces).length - Object.keys(board.pieces).length == -1;
}

function promotionSymbol(prevBoard: Board, board: Board, parsed: ParsedMove): string {
  // '+' for promoted, '=' for chose not to promote, '' for normal move
  if (isDrop(prevBoard, board)) return '';

  const prevRole = prevBoard.pieces[parsed.orig];
  const currentRole = board.pieces[parsed.dest];

  if (!prevRole) return '';
  if (prevRole !== currentRole) return '+';
  if (prevRole.includes('+')) return '';
  if (
    currentRole.toLowerCase() !== 'g' &&
    currentRole.toLowerCase() !== 'k' &&
    ((board.wMoved && ['1', '2', '3'].includes(parsed.dest.slice(1))) ||
      (!board.wMoved && ['7', '8', '9'].includes(parsed.dest.slice(1))))
  ) {
    return '=';
  } else {
    return '';
  }
}

function xiangqiNotation(move: ExtendedMoveInfo, variant: Variant): string {
  const parsed = parseUciToUsi(move.uci, variant.boardSize.width, variant.boardSize.height),
    board = readFen(move.fen, variant.boardSize.height, variant.boardSize.width),
    role = board.pieces[parsed.dest],
    piece = xiangqiRoleToPiece(role),
    //converting to xiangqi from shogi board notation -> ranks: p2=1, p1=10 ; rows: left-right p1 pov, 9-1 for p1, 1-9 p2
    prevFile = board.wMoved ? parseInt(parsed.orig[0]) : variant.boardSize.width + 1 - parseInt(parsed.orig[0]),
    prevRank = parseInt(parsed.orig.slice(1)),
    newFile = board.wMoved ? parseInt(parsed.dest[0]) : variant.boardSize.width + 1 - parseInt(parsed.dest[0]),
    newRank = parseInt(parsed.dest.slice(1)),
    isdiagonalMove = newRank !== prevRank && prevFile !== newFile,
    direction =
      newRank === prevRank
        ? '='
        : (board.wMoved && newRank < prevRank) || (!board.wMoved && newRank > prevRank)
        ? '+'
        : '-',
    movement = direction == '=' || isdiagonalMove ? newFile : Math.abs(newRank - prevRank);

  //Ammend notation due to multiple pawns in row, case 1: pair sideways, case 2: 3 or more up and down and sideways
  if (role === 'p' || role == 'P') {
    const pawnRole = board.wMoved ? 'P' : 'p';
    const addMovedPiece = prevFile !== newFile;
    const pawnRanks = numFriendlyPawnsInColumn(
      parsed.orig[0],
      board,
      variant.boardSize.height,
      pawnRole,
      addMovedPiece,
      prevRank,
      newRank
    );

    if (pawnRanks.length == 2) {
      const pawnOp =
        (pawnRanks.indexOf(prevRank) == 0 && board.wMoved) || (pawnRanks.indexOf(prevRank) == 1 && !board.wMoved)
          ? '+'
          : '-';
      return `${piece}${pawnOp}${direction}${movement}`;
    } else if (pawnRanks.length > 2) {
      const pawnNum = board.wMoved ? pawnRanks.indexOf(prevRank) + 1 : pawnRanks.length - pawnRanks.indexOf(prevRank);
      return `${pawnNum}${prevFile}${direction}${movement}`;
    } else {
      return `${piece}${prevFile}${direction}${movement}`;
    }
  } else {
    return `${piece}${prevFile}${direction}${movement}`;
  }
}

function xiangqiRoleToPiece(role: string) {
  switch (role) {
    case 'n':
    case 'N':
      return 'H';
    case 'b':
    case 'B':
      return 'E';
    default:
      return role.toUpperCase();
  }
}

function numFriendlyPawnsInColumn(
  origFile: string,
  board: Board,
  numRanks: number,
  role: string,
  addMovedPiece: boolean,
  origPieceRank: number,
  newPieceRank: number
): number[] {
  const pawnRanks: number[] = [];
  const ranks = [...Array(numRanks + 1).keys()].slice(1);
  ranks.forEach(r => {
    if (addMovedPiece && r === origPieceRank) pawnRanks.push(origPieceRank); // add the moved piece in this position to avoid sorting
    const piece = board.pieces[origFile + r.toString()];
    if (piece === role) {
      if (!addMovedPiece && r === newPieceRank) {
        pawnRanks.push(origPieceRank); // add moved pawn in original position in order to acquire its index from prev position
      } else {
        pawnRanks.push(r);
      }
    }
  });
  return pawnRanks;
}

function destPosOthello(move: ExtendedMoveInfo, variant: Variant): string {
  if (!move.uci.includes('@')) return 'PASS';

  const reg = move.uci.match(/[a-zA-Z][1-9@]0?/g) as string[];
  const dest = reg[1];

  //convert into flipello notation - a1 is top left for first player (not bottom left)
  const newRank = variant.boardSize.height + 1 - parseInt(dest.slice(1));
  const destPos = dest[0] + newRank;

  return `${destPos}`;
}

function destPosGo(move: ExtendedMoveInfo): string {
  if (!move.uci.includes('@')) {
    if (move.uci == 'pass') return 'PASS';
    else if (move.uci == 'ss:') return 'O DS';
    else return `${move.uci.substring(3).split(',').length} DS`;
  }

  const reg = move.uci.match(/([sS]@|[a-zA-Z][1-9][0-9]?)/g) as string[];
  const dest = reg[1];

  return `${dest}`;
}

function mancalaNotation(move: ExtendedMoveInfo, variant: Variant): string {
  switch (variant.key) {
    case 'togyzkumalak':
      return togyzkumalakNotation(move, variant);
    default:
      return owareNotation(move, variant);
  }
}

function togyzkumalakNotation(move: ExtendedMoveInfo, variant: Variant): string {
  const reg = move.uci.match(/[a-z][1-2]/g) as string[];
  const orig = reg[0];
  const dest = reg[1];
  const origNumber = orig[1] === '1' ? orig.charCodeAt(0) - 96 : 97 - orig.charCodeAt(0) + variant.boardSize.width;
  const destNumber = dest[1] === '1' ? dest.charCodeAt(0) - 96 : 97 - dest.charCodeAt(0) + variant.boardSize.width;
  const gainedStones =
    orig[1] === '1'
      ? getMancalaScore(move.fen, 'p1') > getMancalaScore(move.prevFen, 'p1')
      : getMancalaScore(move.fen, 'p2') > getMancalaScore(move.prevFen, 'p2');
  const destEmpty = isDestEmptyInTogyFen(dest, destNumber, move.fen, variant.boardSize.width);
  const isCapture = gainedStones && orig[1] !== dest[1] && destEmpty;

  const score = orig[1] === '1' ? getMancalaScore(move.fen, 'p1') : getMancalaScore(move.fen, 'p2');
  const scoreText = isCapture ? `(${score})` : '';

  const createdTuzdik =
    orig[1] === '1'
      ? hasTuzdik(move.fen, 'p1') && !hasTuzdik(move.prevFen, 'p1')
      : hasTuzdik(move.fen, 'p2') && !hasTuzdik(move.prevFen, 'p2');

  return `${origNumber}${destNumber}${createdTuzdik ? 'X' : ''}${scoreText}`;
}

function hasTuzdik(fen: string, playerIndex: string): boolean {
  return ['T', 't'].some(t => fen.split(' ')[0].split('/')[playerIndex === 'p1' ? 0 : 1].includes(t));
}

function isDestEmptyInTogyFen(dest: string, destNumber: number, fen: string, width: number): boolean {
  const fenOpponentPart = fen.split(' ')[0].split('/')[dest[1] === '1' ? 1 : 0];
  const destIndex = dest[1] === '1' ? destNumber - 1 : width - destNumber;
  let currentIndex = 0;
  for (const f of fenOpponentPart.split(',')) {
    if (isNaN(+f)) {
      if (currentIndex >= destIndex) return false;
      currentIndex++;
    } else {
      for (let j = 0; j < Number(+f); j++) {
        if (currentIndex === destIndex) return true;
        currentIndex++;
      }
    }
  }
  return false;
}

function owareNotation(move: ExtendedMoveInfo, variant: Variant): string {
  const reg = move.uci.match(/[a-z][1-2]/g) as string[];
  const orig = reg[0];
  const origLetter =
    orig[1] === '1'
      ? orig[0].toUpperCase()
      : nextAsciiLetter(orig[0], (96 - orig.charCodeAt(0)) * 2 + variant.boardSize.width + 1);
  //captured number of stones
  const scoreDiff =
    getMancalaScore(move.fen, 'p1') +
    getMancalaScore(move.fen, 'p2') -
    getMancalaScore(move.prevFen, 'p1') -
    getMancalaScore(move.prevFen, 'p2');
  const scoreText = scoreDiff <= 0 ? '' : ` + ${scoreDiff}`;
  return `${origLetter}${scoreText}`;
}

function nextAsciiLetter(letter: string, n: number): string {
  return String.fromCharCode(letter.charCodeAt(0) + n);
}

export function getMancalaScore(fen: string, playerIndex: string): number {
  return +fen.split(' ')[playerIndex === 'p1' ? 1 : 2];
}

function backgammonNotation(move: ExtendedMoveInfo, variant: Variant): string {
  //TODO support all uci from actions
  console.log('notation move.uci', move.uci);
  if (move.uci === 'roll') return '';
  if (move.uci.includes('|')) return `${move.uci.replace('|', '')}: ?`;

  const reg = move.uci.match(/[a-lsA-LS][1-2@]/g) as string[];
  const orig = reg[0];
  const dest = reg[1];
  const isDrop = reg[0].includes('@');
  const movePlayer = move.prevFen.split(' ')[1] === 'w' ? 'p1' : 'p2';
  const moveOpponent = move.prevFen.split(' ')[1] === 'w' ? 'p2' : 'p1';
  //TODO get this from the fen when it exists and fix test
  const diceRoll = '43';

  //captures
  const capturedPiecesBefore = numberofCapturedPiecesOfPlayer(moveOpponent, move.prevFen);
  const capturedPiecesAfter = numberofCapturedPiecesOfPlayer(moveOpponent, move.fen);
  const isCapture = capturedPiecesBefore !== capturedPiecesAfter;
  const isCaptureNotation = isCapture ? '*' : '';

  //board pos
  const origFile = 1 + Math.abs(orig.charCodeAt(0) - 'a'.charCodeAt(0));
  const origRank = parseInt(orig.slice(1), 10);
  const destFile = 1 + Math.abs(dest.charCodeAt(0) - 'a'.charCodeAt(0));
  const destRank = parseInt(dest.slice(1), 10);

  const origBoardPosNumber = isDrop
    ? 'bar'
    : movePlayer === 'p1'
    ? origRank === 1
      ? variant.boardSize.width + 1 - origFile
      : variant.boardSize.width + origFile
    : origRank === 1
    ? variant.boardSize.width + origFile
    : variant.boardSize.width + 1 - origFile;
  const destBoardPosNumber =
    movePlayer === 'p1'
      ? destRank === 1
        ? variant.boardSize.width + 1 - destFile
        : variant.boardSize.width + destFile
      : destRank === 1
      ? variant.boardSize.width + destFile
      : variant.boardSize.width + 1 - destFile;

  // examples:
  // 43: 8/4 8/5
  // 55: 16/21(3) bar/5
  // 21: 8/7* 13/11
  return `${diceRoll}: ${origBoardPosNumber}/${destBoardPosNumber}${isCaptureNotation}`;
}

function numberofCapturedPiecesOfPlayer(player: 'p1' | 'p2', fen: string): number {
  const pieceString = player === 'p1' ? 'S' : 's';

  if (fen.indexOf('[') !== -1 && fen.indexOf(']') !== -1) {
    const start = fen.indexOf('[', 0);
    const end = fen.indexOf(']', start);
    const pocket = fen.substring(start + 1, end);
    if (pocket === '') return 0;
    for (const p of pocket.split(',')) {
      const count = p.slice(0, -1);
      const letter = p.substring(p.length - 1);
      if (letter === pieceString) {
        return +count;
      }
    }

    return 0;
  } else return 0;
}

export function combinedNotationForBackgammonActions(actionNotations: string[]): string {
  const actions: string[] = [];
  const captures: boolean[] = [];
  const occurances: number[] = [];
  for (const notation of actionNotations) {
    const movePart = notation.split(' ')[1].replace('*', '');
    const isCapture = notation.split(' ')[1].includes('*');
    if (actions.includes(movePart)) {
      const duplicateIndex = actions.indexOf(movePart);
      occurances[duplicateIndex] += 1;
      captures[duplicateIndex] = captures[duplicateIndex] || isCapture;
    } else {
      actions.push(movePart);
      occurances.push(1);
      if (isCapture) {
        captures.push(true);
      } else {
        captures.push(false);
      }
    }
  }

  const dice = actionNotations[0].split(' ')[0];
  let output = dice;

  actions.forEach((action, index) => {
    const occurancesString = occurances[index] > 1 ? `(${occurances[index]})` : '';
    const captureString = captures[index] ? '*' : '';
    const part = ` ${action}${occurancesString}${captureString}`;
    output += part;
  });

  //examples (also see tests):
  // ["43: 8/4", "43: 8/4"] -> "43: 8/4(2)"
  return output;
}
