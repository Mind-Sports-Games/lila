import { Run } from './interfaces';
import { Config as CgConfig } from 'chessground/config';
import { uciToLastMove } from './util';
import { makeFen } from 'stratops/fen';
import { chessgroundDests } from 'stratops/compat';

export const makeCgOpts = (run: Run, canMove: boolean): CgConfig => {
  const cur = run.current;
  const pos = cur.position();
  return {
    fen: makeFen('chess')(pos.toSetup()),
    orientation: run.pov,
    myPlayerIndex: run.pov,
    turnPlayerIndex: pos.turn,
    movable: {
      playerIndex: run.pov,
      dests: canMove ? chessgroundDests('chess')(pos) : undefined,
    },
    check: !!pos.isCheck(),
    lastMove: uciToLastMove(cur.lastMove()),
  };
};
