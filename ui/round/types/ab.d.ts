declare module 'ab' {
  import { MoveMetadata } from 'chessground/build/types';
  function init(round: unknown): void;
  function move(round: unknown, meta: MoveMetadata): void;
}
