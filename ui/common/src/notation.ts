import { NotationStyle } from 'chess';

interface ExtendedMoveInfo{
    san: String
    uci: String;
    fen: String;
}

export function moveFromNotationStyle(notation: NotationStyle): (move: ExtendedMoveInfo) => String {
    switch (notation) {
        case 'xs2': return xiangqiNotation;
        case 'usi': return shogiNotation;
        case 'san': return move => move.san[0] === 'P' ? move.san.slice(1) : move.san;
        case 'uci': return move => move.uci;
    }
}


function shogiNotation(move: ExtendedMoveInfo): String {
    return move.uci
}

function xiangqiNotation(move: ExtendedMoveInfo): String {
    return move.uci
}