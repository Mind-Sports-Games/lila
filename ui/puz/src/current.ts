import { Chess, opposite, parseUci } from 'stratops';
import { parseFen } from 'stratops/fen';
import { Puzzle } from './interfaces';
import { getNow } from './util';

export default class CurrentPuzzle {
  line: Uci[];
  startAt: number;
  moveIndex = 0;
  pov: PlayerIndex;

  constructor(readonly index: number, readonly puzzle: Puzzle) {
    this.line = puzzle.line.split(' ');
    this.pov = opposite(parseFen('chess')(puzzle.fen).unwrap().turn);
    this.startAt = getNow();
  }

  position = (): Chess => {
    const pos = Chess.fromSetup(parseFen('chess')(this.puzzle.fen).unwrap()).unwrap();
    this.line.slice(0, this.moveIndex + 1).forEach(uci => pos.play(parseUci(uci)!));
    return pos;
  };

  expectedMove = () => this.line[this.moveIndex + 1];

  lastMove = () => this.line[this.moveIndex];

  isOver = () => this.moveIndex >= this.line.length - 1;
}
