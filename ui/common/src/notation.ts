import { NotationStyle } from 'chess';

interface ExtendedMoveInfo{
    san: string
    uci: string;
    fen: string;
}

export function moveFromNotationStyle(notation: NotationStyle): (move: ExtendedMoveInfo) => string {
    switch (notation) {
        case 'xs2': return xiangqiNotation;
        case 'usi': return shogiNotation;
        case 'san': return move => move.san[0] === 'P' ? move.san.slice(1) : move.san;
        case 'uci': return move => move.uci;
    }
}


function shogiNotation(move: ExtendedMoveInfo): string {
    return move.uci
}

function xiangqiNotation(move: ExtendedMoveInfo): string {
    return move.uci
}