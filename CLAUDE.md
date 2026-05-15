# PlayStrategy Lila

Fork of [Lichess](https://lichess.org) (forked ~May 2021, significantly diverged). Multi-game 2-player strategy platform (Chess, Draughts, Go, Backgammon, and more). Production: https://playstrategy.org.

## Architecture

- **Backend**: Scala (Play framework)
- **Frontend**: TypeScript + SCSS
- **Database**: MongoDB via Reactivemongo (`$doc`, `$inc` syntax)

## Build & Run

- **Backend**: `./lila run` (sbt + Play)
- **Frontend**: `ui/.build/run` (custom tool — NOT webpack/vite)
- **Tests**: `sbt test`

## Key Directories

- `modules/` — Scala backend modules (one per domain)
- `app/views/` — Scala HTML views (plain `.scala` files, not Twirl templates)
- `ui/` — TypeScript + SCSS frontend; one module per feature/domain (e.g. `round`, `analyse`), each with its own `css/`; shared CSS in `ui/common/css/`
- `translation/source/` — i18n XML source files (Scala functions auto-generated from XML names)

## Sibling Repos (checked out at `../`)

These repos live alongside lila and should be read directly when tracing logic into them:

- **`../strategygames`** (Scala): backend game logic library — [Mind-Sports-Games/strategygames](https://github.com/Mind-Sports-Games/strategygames)
- **`../chessground`** (TypeScript): board rendering library — [Mind-Sports-Games/chessground](https://github.com/Mind-Sports-Games/chessground)
- **`../stratops`** (TypeScript): frontend game operations (analysis, puzzles, notation/replay — not core move logic) — [Mind-Sports-Games/stratops](https://github.com/Mind-Sports-Games/stratops)
- **`../lila-ws`**: WebSocket server, runs alongside lila in dev

## Notes

- Variant CSS classes: `variant-{variant.key}` on board/mini-game elements (e.g. `variant-atomic`)

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- ALWAYS read graphify-out/GRAPH_REPORT.md before reading any source files, running grep/glob searches, or answering codebase questions. The graph is your primary map of the codebase.
- IF graphify-out/wiki/index.md EXISTS, navigate it instead of reading raw files
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep — these traverse the graph's EXTRACTED + INFERRED edges instead of scanning files
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
