declare module 'ab' {
  import { MoveMetadata } from 'draughtsground/types';
  function init(round: unknown): void;
  function move(round: unknown, meta: MoveMetadata): void;
}
