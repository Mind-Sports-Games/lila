/* Board libraries are loaded on demand, so a page that shows no mini board of a
 * given family never downloads that family's code. Memoised per family: initAll
 * runs again on every 'content-loaded' and on every snabbdom postpatch. */

type BoardCtor = (element: HTMLElement, config: any) => any;

// A rejection forgets itself: chunk names are content hashed, so a tab left open across
// a deploy must be able to fetch again rather than stay boardless for its lifetime.
const memo = (load: () => Promise<BoardCtor>): (() => Promise<BoardCtor>) => {
  let pending: Promise<BoardCtor> | undefined;
  return () =>
    (pending ??= load().catch(e => {
      pending = undefined;
      throw e;
    }));
};

export const loadChessground = memo(() => import('chessground').then(m => m.Chessground as unknown as BoardCtor));

export const loadDraughtsground = memo(() => import('draughtsground').then(m => m.default as unknown as BoardCtor));
