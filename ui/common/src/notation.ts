import { NotationStyle, getScore } from 'stratutils';
import { Xiangqi } from 'stratops/variants/xiangqi/Xiangqi';
import { MiniXiangqi } from 'stratops/variants/xiangqi/MiniXiangqi';
import { Shogi } from 'stratops/variants/shogi/Shogi';
import { MiniShogi } from 'stratops/variants/shogi/MiniShogi';
import { Othello } from 'stratops/variants/othello/Othello';
import { GrandOthello } from 'stratops/variants/othello/GrandOthello';
import { GameFamily as GoFamily } from 'stratops/variants/go/GameFamily';
import type { ExtendedMoveInfo, ParsedMove } from 'stratops/variants/interfaces';

type Board = { pieces: { [key: string]: string }; wMoved: boolean };

// @TODO: The content of this file will progressively migrate to stratops (or stratutils)

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
      return othelloNotation;
    case 'dpg':
      return goNotation;
    case 'man':
      return mancalaNotation;
    case 'bkg':
      return backgammonNotation;
    case 'abl':
      return abaloneNotation;
  }
}

function shogiNotation(move: ExtendedMoveInfo, variant: Variant): string {
  if (variant.key === 'minishogi') {
    return MiniShogi.uci2Notation(move);
  }

  return Shogi.uci2Notation(move);
}

function xiangqiNotation(move: ExtendedMoveInfo, variant: Variant): string {
  if (variant.key === 'minixiangqi') {
    return MiniXiangqi.uci2Notation(move);
  }

  return Xiangqi.uci2Notation(move);
}

function othelloNotation(move: ExtendedMoveInfo, variant: Variant): string {
  if (variant.key === 'flipello10') {
    return GrandOthello.uci2Notation(move);
  }

  return Othello.uci2Notation(move);
}

function goNotation(move: ExtendedMoveInfo, _variant: Variant): string {
  return GoFamily.uci2Notation(move);
}

function mancalaNotation(move: ExtendedMoveInfo, variant: Variant): string {
  switch (variant.key) {
    case 'togyzkumalak':
    case 'bestemshe':
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
  return getScore('oware', fen, playerIndex) ?? 0;
}

function backgammonNotation(move: ExtendedMoveInfo, variant: Variant): string {
  let isLift = false; //using this instead of changing the regex
  if (move.uci === 'roll') return '';
  if (move.uci === 'cubeo') return 'Double';
  if (move.uci === 'cubey') return 'Take';
  if (move.uci === 'cuben') return 'Drop';
  if (move.uci === 'undo') return 'undo';
  if (move.uci === 'endturn') return '(no-play)';
  if (move.uci.includes('/')) return `${move.uci.replace('/', '')}:`;
  if (move.uci.includes('^')) {
    isLift = true;
  }

  const reg = isLift
    ? (move.uci.replace('^', 'a1').match(/[a-lsA-LS][1-2@]/g) as string[])
    : (move.uci.replace('x', '').match(/[a-lsA-LS][1-2@]/g) as string[]);
  const orig = reg[0];
  const dest = reg[1];
  const isDrop = reg[0].includes('@');
  const movePlayer = move.prevFen.split(' ')[3] === 'w' ? 'p1' : 'p2';
  const moveOpponent = move.prevFen.split(' ')[3] === 'w' ? 'p2' : 'p1';

  const diceRoll = getDice(move.prevFen); //this is not really used but for completeness

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
  // 55: 21/16(3) bar/5
  // 21: 8/7* 13/11
  if (isLift) return `${diceRoll}: ${destBoardPosNumber}/off`;
  return `${diceRoll}: ${origBoardPosNumber}/${destBoardPosNumber}${isCaptureNotation}`;
}

export function getDice(fen: string): string {
  if (fen.split(' ').length < 2) return '';
  const unusedDice = fen.split(' ')[1].replace('-', '').split('/');
  const usedDice = fen.split(' ')[2].replace('-', '').split('/');
  const dice = unusedDice.concat(usedDice).join('');
  if (dice.length === 4) return dice.slice(2); // handles doubles
  return dice;
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
    if (notation.split(' ').length === 2) {
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
    } else if (notation === '(no-play)' && actionNotations.length === 2) {
      return actionNotations[0].split(' ')[0] + ' ' + notation;
    } else if (notation === '(no-play)' && actionNotations.length === 1) {
      return '...';
    } else if (['Double', 'Take', 'Drop'].includes(notation) && actionNotations.length === 1) {
      return notation;
    }
  }

  //dice roll is always first action - check this assumption in multipoint games
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

function abaloneNotation(move: ExtendedMoveInfo, variant: Variant): string {
  const reg = move.uci.match(/[a-i][1-9]/g) as string[],
    parsed = parseUciToAbl(move.uci),
    orig = reg[0],
    dest = reg[1],
    board = readAbaloneFen(move.fen, variant.boardSize.height, variant.boardSize.width),
    prevBoard = readAbaloneFen(move.prevFen, variant.boardSize.height, variant.boardSize.width),
    keyDiffs = diffAbaloneBoard(board, prevBoard),
    isPush = new Set(keyDiffs[0].concat(keyDiffs[1])).size != keyDiffs[0].concat(keyDiffs[1]).length,
    oppPushedTo = keyDiffs[1].filter(k => k !== dest),
    isCapture =
      move.fen.split(' ')[1] + move.fen.split(' ')[2] != move.prevFen.split(' ')[1] + move.prevFen.split(' ')[2],
    is3v2Capture = isCapture && !isOnEdgeAbaloneBoard(dest);

  const destNotation = isPush
    ? isCapture
      ? is3v2Capture
        ? parseUCISquareToAbl(findEdgeFromAbaloneMove(orig, dest))
        : parsed.dest
      : parseUCISquareToAbl(oppPushedTo[0])
    : parsed.dest;

  return `${parsed.orig}${isCapture ? 'x' : ''}${destNotation}`;
}

function isOnEdgeAbaloneBoard(dest: string): boolean {
  return (
    dest[0] == 'a' ||
    dest[0] == 'i' ||
    dest[1] == '1' ||
    dest[1] == '9' ||
    ['b6', 'c7', 'd8', 'f2', 'g3', 'h4'].includes(dest)
  );
}

function findEdgeFromAbaloneMove(orig: string, dest: string): string {
  //same letter (\)
  if (orig[0] == dest[0]) {
    if (parseInt(orig[1]) > parseInt(dest[1])) return orig[0] + Math.max(orig[0].charCodeAt(0) - 96 - 4, 1).toString();
    else return orig[0] + Math.min(orig[0].charCodeAt(0) - 96 + 4, 9).toString();
  }
  //same number (-)
  if (orig[1] == dest[1]) {
    if (orig[0].charCodeAt(0) > dest[0].charCodeAt(0))
      return String.fromCharCode(Math.max(parseInt(orig[1]) + 96 - 4, 97)) + orig[1];
    else return String.fromCharCode(Math.min(parseInt(orig[1]) + 96 + 4, 105)) + orig[1];
  }
  // other direction (/)
  if (Math.abs(parseInt(dest[1]) + dest[0].charCodeAt(0) - (parseInt(orig[1]) + orig[0].charCodeAt(0))) % 2 == 0) {
    if (parseInt(orig[1]) + orig[0].charCodeAt(0) > parseInt(dest[1]) + dest[0].charCodeAt(0)) {
      return String.fromCharCode(dest[0].charCodeAt(0) - 1) + (parseInt(dest[1]) - 1).toString();
    } else return String.fromCharCode(dest[0].charCodeAt(0) + 1) + (parseInt(dest[1]) + 1).toString();
  }
  return 'a1';
}

function diffAbaloneBoard(board: Board, prevBoard: Board): [string[], string[]] {
  const turnPlayerChanges = Object.keys(prevBoard.pieces).filter(k => prevBoard.pieces[k] !== board.pieces[k]);
  const oppChanges = Object.keys(board.pieces).filter(k => prevBoard.pieces[k] !== board.pieces[k]);
  return [turnPlayerChanges, oppChanges];
}

function parseUciToAbl(uci: string): ParsedMove {
  const reg = uci.match(/[a-i][1-9]/g) as string[];
  return {
    orig: parseUCISquareToAbl(reg[0])!,
    dest: parseUCISquareToAbl(reg[1])!,
  };
}

export function parseUCISquareToAbl(str: string): string | undefined {
  if (str.length > 2) return;
  const numberPart = 1 + Math.abs(str.charCodeAt(0) - 'a'.charCodeAt(0));
  const letterPart = String.fromCharCode(parseInt(str.slice(1)) + 96);
  return letterPart.toString() + numberPart.toString();
}

export function readAbaloneFen(fen: string, ranks: number, files: number) {
  const parts = fen.split(' '),
    board: Board = {
      pieces: {},
      wMoved: parts[3] === 'b',
    };

  parts[0]
    .split('[')[0]
    .split('/')
    .slice(0, ranks)
    .forEach((row, y) => {
      let x = Math.max(files - y - 4, 1);
      row.split('').forEach(v => {
        if (v == '~') return;
        const nb = parseInt(v, 10);
        if (nb) x += nb;
        else {
          board.pieces[`${String.fromCharCode(x + 96)}${files - y}`] = v;
          x++;
        }
      });
    });

  return board;
}
