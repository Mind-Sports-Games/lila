// board libraries load with the first board of their family, memoised per family

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
