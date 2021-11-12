import { NotationStyle } from 'chess';
import * as cg from 'chessground/types';

interface ExtendedMoveInfo{
    san: string
    uci: string;
    fen: string;
}

interface ParsedMove{
  role: cg.Role;
  dest: string;
  orig: string;
  capture: boolean;
  drop: boolean;
  promotion: string;
}

export function moveFromNotationStyle(notation: NotationStyle): (move: ExtendedMoveInfo) => string {
    switch (notation) {
        case 'xs2': return xiangqiNotation;
        case 'usi': return shogiNotation;
        case 'san': return move => move.san[0] === 'P' ? move.san.slice(1) : move.san;
        case 'uci': return move => move.uci;
    }
}

export function parseUci (uci: string): ParsedMove {
    return {
        role: 'p-piece', //lishogiCharToRole(san[0])!,
        orig: parseUCISquareToUSI(uci.slice(0, 2))!,
        dest: parseUCISquareToUSI(uci.slice(2, 4))!,
        capture: false, //san.includes('x'),
        drop: false, //san.includes('*'),
        promotion: '', //san.includes('=') ? '=' : san.includes('+') ? '+' : '',
    };
}

export function parseUCISquareToUSI(str: string): string | undefined {
    if (str.length !== 2) return;
    const file = 9 - Math.abs(str.charCodeAt(0) - 'a'.charCodeAt(0));
    const rank = 10 - parseInt(str[1]);
    if (file < 1 || file > 9 || rank < 1 || rank > 9) return;
    return file.toString() + rank.toString();
  }

function shogiNotation(move: ExtendedMoveInfo): string {
    const parsed = parseUci(move.uci),
     dest = parsed.dest,
     connector = "-", //move (-), capture(x), drop (*)
     piece = 'P',
     origin = '';

    return `${piece}${origin}${connector}${dest}${parsed.promotion}-u-${move.uci}`;
}

function xiangqiNotation(move: ExtendedMoveInfo): string {
    return move.uci
}