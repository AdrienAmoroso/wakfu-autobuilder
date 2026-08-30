# CLAUDE.md

This file gives Claude Code its project context. The full agent guide lives in `AGENTS.md` —
it is imported below so both files stay in sync.

@AGENTS.md

## Module-scoped context — read these when you're actually working in that module

These load automatically the moment you Read/Edit a file under that directory (not at session start),
so you don't need to open them proactively — but if you're new to Wakfu or this module's own
conventions, reading the relevant one first will save you from re-deriving it mid-task:

- **`common-lib/CLAUDE.md`** — Wakfu game fundamentals (classes, the stat/mastery taxonomy, rarity,
  equipment slots, runes/sockets, sublimations, skill branches, recipes) grounded in the domain model
  every module shares. Start here if you don't already know Wakfu well.
- **`autobuilder/CLAUDE.md`** — the actual damage formula the max-damage solver optimizes, scoring-mode
  mechanics, spell-rotation conventions.
- **`gui-compose/CLAUDE.md`** — where to put a new `BuildSearchModel` function (it's split by screen
  across sibling files now, not one file), i18n/design-token conventions, screenshot-test caveats.
- **`market-server/CLAUDE.md`** — Wakfu economy vocabulary (HDV, kamas, crafting vs. harvest
  professions), the craft-cost/ROI/expected-value formulas, the external capture pipeline.

## Claude Code working notes (project-specific)

- **Engine.** The solver is the Google OR-Tools CP-SAT solver
  (`autobuilder/.../WakfuBuildSolver.kt`, deterministic & optimal). It streams its result as a
  `Flow<SolverResult<BuildCombination>>` (`SolverResult` was formerly `GeneticAlgorithmResult`; the
  enclosing package is still named `genetic`). The original genetic-algorithm engine has been
  **removed** — OR-Tools is the only solver (no `WakfuSolver` toggle). See `AGENTS.md` §4 and
  `autobuilder/CLAUDE.md` for the damage formula it optimizes.
- **OR-Tools is native.** It loads a native library at runtime, so running/testing the engine needs
  extra JVM args (`--enable-native-access=ALL-UNNAMED`, `--add-opens …`) — already wired in the
  `autobuilder` and `gui-compose` build scripts. The first search pays a one-time cold start; the
  GUI hides it behind a loading screen (`gui-compose/.../WarmupTiming.kt`, `BuildSearchModel`).
- **Build is heavy.** A cold `./gradlew build` resolves Compose Desktop + the native OR-Tools
  library. Prefer module-scoped tasks (`:autobuilder:test`, `:gui-compose:run`) while iterating.
- **GUI is Compose Desktop** (`gui-compose` module) — built programmatically in Kotlin, no FXML.
  `state/BuildSearchModel.kt` is split by screen across sibling `BuildSearchModelSearch/Manual/Market/
  Kamas/Library.kt` files (extension functions, same package) — see `gui-compose/CLAUDE.md` before
  adding a new function so it lands in the right file with the right visibility.
- **Run `./gradlew ktlintFormat`** before finishing a change; CI style is strict.
- **Git.** Don't commit/push unless explicitly asked; this repo's default branch is `main`. When you
  ARE asked to commit/push: never add a `Co-Authored-By: Claude` trailer to the commit message in this
  repo (overrides the harness's usual default) — if a given flow can't produce a commit without one,
  stop and leave the commit/push to the user instead of working around it.
