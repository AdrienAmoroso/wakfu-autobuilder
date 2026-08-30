# CLAUDE.md — `gui-compose` (Compose Desktop UI conventions)

Companion to root `AGENTS.md` §6 (screen/package map) and `common-lib/CLAUDE.md` (the game vocabulary
these screens render). This file is the practical, module-local how-to for adding/changing UI here.

## `BuildSearchModel` is split by feature — put new functions in the matching file

`state/BuildSearchModel.kt` used to be one 2,780-line file; it's now split across sibling files in the
same `me.chosante.ui.state` package (all extension functions on the class — `BuildSearchModel.kt`
itself keeps only the constructor/DI, the `ui` state holder, and genuinely shared nav/helpers):

| Screen / concern | File |
|---|---|
| `Screen.Builder` request + result, search, optimality proof | `BuildSearchModelSearch.kt` |
| `Screen.ManualBuild` | `BuildSearchModelManual.kt` |
| `Screen.Market` (incl. HDV capture control) | `BuildSearchModelMarket.kt` |
| `Screen.Kamas` | `BuildSearchModelKamas.kt` |
| `Screen.Library` / `Screen.Compare` | `BuildSearchModelLibrary.kt` |

When adding a function: put it in the file matching the screen it serves, as `fun BuildSearchModel.
yourFn(...)` (or `internal fun BuildSearchModel.yourFn(...)` if it's a helper only other split files
call, or `private fun BuildSearchModel.yourFn(...)` if only this one file calls it). Two mechanical
consequences of the split, easy to trip on:
- **Kotlin has no package-private visibility** — only `private` (class-body- or file-scoped) or
  `internal` (whole `gui-compose` module). A field/helper a split file needs must be at least
  `internal`; this is deliberate and documented inline on each promoted member, not an oversight.
- **A screen composable calling `model::yourFn` needs an explicit `import me.chosante.ui.state.
  yourFn`** — unlike a plain member function, an extension function isn't auto-visible across packages
  just because you hold an instance. Forgetting the import surfaces as "Unresolved reference" at
  compile time, not a runtime issue.
- A class member that's *also* an extension on some other type (e.g. `UiState.toTargetStats()`) can't
  keep a `BuildSearchModel` receiver too once moved out of the class body — Kotlin doesn't support two
  receivers. If its body never touches `ui`/other class state, it's a plain top-level extension on that
  other type instead (no promotion needed); if it does, that state becomes an explicit parameter.

## `UiState` — one flat `data class`, grouped by the same domains

Deliberately kept as a single `data class` (not split into nested per-screen state objects) — Compose
recomposes at the whole-`UiState` granularity regardless of how the Kotlin source is organized, so
splitting it would only add indirection for the same behavior. Its fields are grouped with `// --- X
---` banners matching the table above; add new fields to the matching group, not at the end of the file.

## i18n

`i18n/I18n.kt`'s hand-written `Tr` enum carries every string in EN + FR — **there is no generated i18n
code, no `.properties` files, no resource bundle.** `tr(Tr.X)` resolves through `LocalLang`. Adding UI
text = adding a `Tr` entry with both languages, not a resource-file edit.

## Design tokens & visual reference

- `theme/`: `WColor` (dark palette), `WTypography`, `WDimens` (radius/padding/gap) — prefer these over
  ad-hoc `Color(...)`/`.dp` literals; a past cleanup pass found several hardcoded 9/10/12dp radii that
  should have been `WDimens.radius`.
- `docs/design-reference/` (HTML/CSS/JSX mockups + screenshots) is the **visual source of truth** —
  check it before inventing new layout/spacing conventions.
- `RuneShape`/`socketLayout` (`paperdoll/PaperdollPanel.kt`) render the socket-colour + gold-wildcard
  UI shared between the auto-Builder's rune picker and the manual-build Enchantment tab — reuse them
  (`internal`, promoted specifically for this) rather than redrawing sockets elsewhere.

## OR-Tools warm-up — don't fight the gating

`BuildSearchModel`'s `init` pays OR-Tools' one-time native cold start off the UI thread, gated on the
window's first frame (`windowShown`) — starting it earlier stalls the whole UI thread on macOS's first-
launch dylib validation. If you're adding startup-time work, it must happen **after** `isReady` flips
(see `LoadingScreen`/`WarmupTiming.kt`), not before — the icon preloader was moved here once already
after it starved warm-up by competing for cores.

## Testing

- `WAKFU_COMPOSE_SCREENSHOT=/path ./gradlew :gui-compose:run` renders the app to a PNG and exits, with
  warm-up gating skipped. **Known unreliable in this dev environment**: the `Robot`-based capture grabs
  whatever's actually on screen at the window's coordinates, not an offscreen render — it has
  repeatedly captured an unrelated foreground window instead of the app. Treat a clean `BUILD
  SUCCESSFUL` (app launched and exited without a runtime exception) as the real signal; only trust the
  PNG's content if you actually open and look at it. Compile + `:gui-compose:test` remain the primary
  verification for any change here, not the screenshot.
- Screen/mode-specific screenshot env vars exist for smoke-testing a particular screen without manually
  navigating (`WAKFU_COMPOSE_SCREENSHOT_MARKET`, `_KAMAS`, `_FORCE_FIRST`, `_PRECISION`, `_VARY_
  PRIORITY`, …) — grep `BuildSearchModel.kt`'s screenshot-flag fields for the current set before adding
  a new one; they follow one shared naming/property pattern.
- `HistoryRepositoryTest > appDataDir resolves per OS()` fails on Windows — a known, pre-existing,
  unrelated gap (hardcoded POSIX path strings compared against Windows path formatting), not something
  a change here caused. `./gradlew test` (whole repo, unqualified) is fail-fast and this failure sits
  early in the task graph — use `--continue` or scope to `:gui-compose:test` to see past it.
