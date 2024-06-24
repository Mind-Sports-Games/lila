import { Hooks } from 'snabbdom';
import { Puzzle } from './interfaces';
import { opposite } from 'stratops/build';
import { parseFen } from 'stratops/build/fen';

export function bind(eventName: string, f: (e: Event) => any, redraw?: () => void): Hooks {
  return onInsert(el =>
    el.addEventListener(eventName, e => {
      const res = f(e);
      if (redraw) redraw();
      return res;
    })
  );
}

export function onInsert<A extends HTMLElement>(f: (element: A) => void): Hooks {
  return {
    insert: vnode => f(vnode.elm as A),
  };
}

export const getNow = (): number => Math.round(performance.now());

export const uciToLastMove = (uci: string): [Key, Key] => [uci.substr(0, 2) as Key, uci.substr(2, 2) as Key];

export const puzzlePov = (puzzle: Puzzle) => opposite(parseFen('chess')(puzzle.fen).unwrap().turn);

export const loadSound = (file: string, volume?: number, delay?: number) => {
  setTimeout(() => playstrategy.sound.loadOggOrMp3(file, `${playstrategy.sound.baseUrl}/${file}`), delay || 1000);
  return () => playstrategy.sound.play(file, volume);
};

export const sound = {
  move: (take: boolean) => playstrategy.sound.play(take ? 'capture' : 'move'),
  good: loadSound('lisp/PuzzleStormGood', 0.9, 1000),
  wrong: loadSound('lisp/Error', 1, 1000),
  end: loadSound('lisp/PuzzleStormEnd', 1, 5000),
};
