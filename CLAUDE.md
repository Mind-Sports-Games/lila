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
- **`../docs`**: the documentation repo — decision history and domain language for all of the above; see [Docs Repo](#docs-repo-docs)

## Docs Repo (`../docs`)

Prose that outlives a pull request lives in `../docs`, not in this repo. Lila's documentation is
under `../docs/lila/`. **This is where the history of why the code looks the way it does is kept** —
consult it before proposing a change that reverses an earlier decision, and before answering "why is
it done this way".

- `../docs/README.md` — the single source of truth for layout and naming across every repo's docs.
  Read it first and follow it; never invent or assume a convention.
- `../docs/lila/README.md` — entry point: what is documented for lila and where to start.
- `../docs/lila/context.md` — the **glossary**: the project's own words, alphabetical, tight
  definitions only. Bound by it — use these terms in code, names, commit messages and PR text rather
  than coining a synonym for something that already has a name here.
- `../docs/lila/adr/` — **architecture decision records**, one file per decision, named for the date
  it was taken and the Linear ticket it came from (`<date>-<ticket-slug>.md`, e.g. the slug
  `pla-148-xyz` from the ticket's git branch name). `../docs/README.md` gives the exact format and
  wins over that sketch — read it there rather than copying the example. Scan the filenames when
  starting work; read in full any ADR that touches what you are about to change.
- `../docs/lila/<topic>.md` — reference and explanation for a subject that needs more than an ADR.

### Working with it

- **Reading**: at the start of any non-trivial change, read `../docs/README.md`, then
  `../docs/lila/context.md`, then the ADR filenames. If `../docs/lila/` does not exist yet, say so once
  and carry on — the work is not blocked by it.
- **Writing**: a resolved term goes inline into `context.md` the moment it resolves (edit an existing
  entry in place, never append a second one for the same word). A decision becomes an ADR only if it
  passes all three gates at once — hard to reverse, surprising without context, and a real trade-off
  with a live alternative that was rejected for a reason. Most decisions fail a gate and belong
  nowhere but the diff; never manufacture an ADR.
- **Separate repo, separate review**: `../docs` has its own git history. Its changes never go on a
  lila branch and are never committed alongside code. Leave them uncommitted on whatever branch it is
  already on unless asked otherwise, and report `git status` for the two repos separately.
- Skills that already follow this: `grill-with-docs`, `plan-for-ticket`, `implement-plan-from-ticket`.

## Notes

- Variant CSS classes: `variant-{variant.key}` on board/mini-game elements (e.g. `variant-atomic`)

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:

- ALWAYS read graphify-out/GRAPH_REPORT.md before reading any source files, running grep/glob searches, or answering codebase questions. The graph is your primary map of the codebase.
- IF graphify-out/wiki/index.md EXISTS, navigate it instead of reading raw files
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep — these traverse the graph's EXTRACTED + INFERRED edges instead of scanning files
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
