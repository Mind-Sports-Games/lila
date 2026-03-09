# PlayStrategy Lila

Fork of Lichess. Multi-game strategy platform. Scala (Play framework) + TypeScript + SCSS.

## Build

- **Frontend**: `ui/.build/run` (custom tool — NOT webpack/vite)
- **Backend**: `sbt` (Play framework), **DB**: MongoDB via Reactivemongo (`$doc`, `$inc` syntax)

## Key Directories

- `modules/` — Scala backend modules (one per domain)
- `app/views/` — Twirl templates
- `ui/` — TypeScript + SCSS frontend (CSS under `ui/common/css/`)
- `translation/source/` — i18n XML source files (Scala functions auto-generated from XML names)

## Notes

- **strategygames** (Scala): backend game logic library (see playstrategy/strategygames repo)
- **chessground** (TypeScript): board rendering library (local fork, see playstrategy/chessground repo)
- **stratops** (TypeScript): frontend game operations library — used in analysis, puzzles, and round notation/replay display, but not for core game/move logic
- Variant CSS classes: `variant-{variant.key}` on board/mini-game elements (e.g. `variant-atomic`)
