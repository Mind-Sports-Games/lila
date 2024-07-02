// these used to be enums in index.d.ts, had to instantiate them because enum values
// cannot be imported by isolated modules

export const enum Coords {
  Hidden = 0,
  Inside = 1,
  Outside = 2,
}

export const enum AutoQueen {
  Never = 1,
  OnPremove = 2,
  Always = 3,
}

export const enum ShowClockTenths {
  Never = 0,
  Below10Secs = 1,
  Always = 2,
}

export const enum ShowResizeHandle {
  Never = 0,
  OnlyAtStart = 1,
  Always = 2,
}

export const enum MoveEvent {
  Click = 0,
  Drag = 1,
  ClickOrDrag = 2,
}

export const enum Replay {
  Never = 0,
  OnlySlowGames = 1,
  Always = 2,
}
