# CLAUDE.md — `market-server` (Wakfu economy fundamentals + module conventions)

Companion to `common-lib/CLAUDE.md` (the item/stat model) and root `AGENTS.md` §5's "Market / economy
data" subsection (the module map + pipeline overview). This file is what you need to reason correctly
about kamas/economy features specifically — read it before touching craft-cost, harvest, monster-drop,
or capture code.

## Wakfu economy vocabulary

- **HDV** ("Hôtel de Vente") — Wakfu's server-scoped auction house. Every price this module serves is a
  **captured observation** of a real HDV listing, not a live API call (see "How prices get here" below)
  — `market-server` has zero HTTP-client dependency, structurally incapable of calling Ankama itself.
- **Kamas** — the in-game currency. Every profitability number in this module (`craftCost`, `roi`,
  expected drop value, …) is denominated in kamas.
- **Two distinct professions systems**, both feeding this module from different `common-lib` types:
  - **Crafting professions** (`Recipe.jobId`/`jobName`, e.g. Baker, Handyman, Armorer…): turn N
    ingredients into 1+ output items. `recipes.json` (`recipes-extractor`) is the source.
  - **Harvest professions** (`Characteristic.*_HARVEST_QUANTITY_PERCENTAGE` in `common-lib`, 6 of them:
    Herbalist/Lumberjack/Trapper/Miner/Farmer/Fisherman): gather raw resources from map nodes, no
    ingredients consumed. `harvest-nodes.json` (`harvest-extractor`) is the source.
  - Monster kills are a third, un-professioned source of tradeable drops (`monster-drops.json`,
    `items-extractor`'s bestiary crawl).
- **Prospection** (`Characteristic.PROSPECTION`, `common-lib`) is the character stat that raises drop
  rates/rarity — real in-game lever a farming build would stack, but this module doesn't model it
  (its scanners rank *recipes/nodes/monsters*, not personal-prospection-adjusted yields).

## The three profitability formulas (all in `service/`, all pure/testable, none touches the CP-SAT solver)

- **Craft cost / ROI** (`CraftCostService.scoreRecipe`): `craftCost` = Σ ingredient
  `unitPrice × quantity` (each ingredient priced from **its own** latest HDV observation — deliberately
  **non-recursive**, matching the legacy `WakfuMarket.App` behavior: an ingredient that's itself
  craftable is still bought-priced, never recipe-resolved). `grossMargin = marketPrice - craftCost`;
  `netMargin = grossMargin × (1 - taxRate)` (`taxRate` defaults to **2%**, the HDV sale tax); `roi =
  netMargin / craftCost`. Decision: `"insufficient_data"` if any ingredient or the output itself has no
  captured price, else `"craft"` when `roi > 5%` (`ROI_CRAFT_THRESHOLD`), else `"buy"`. `confidence =
  avgConfidence(priced ingredients) × completenessFactor(priced ÷ total ingredients)` — a recipe with
  half its ingredients priced never looks as trustworthy as one fully priced, even if the average of
  what IS priced looks great.
- **Harvest / monster-drop expected value** (`ExpectedValue.kt`, shared by both scanners): `Σ dropRate
  × quantity × minPrice` over every priced drop; `missingDropCount` surfaces drops that had to be
  excluded (never silently treated as free/zero) — same "surface the gap, don't hide it" honesty as
  `craftCost`'s `missingPriceCount`.
- **All three are informational only** — none of this ever feeds the equipment solver's objective
  (`autobuilder`); it's a separate "what's worth doing right now" ranking, not a build constraint.

## How prices get here (the capture pipeline)

`market-server` and an **external, untouched** Python/PowerShell pipeline
(`C:\Users\adrie\Claude\Projects\AgentWakfu\scraper\`, outside this repo) share one SQLite file
(`%LOCALAPPDATA%\WakfuMarket\wakfu.db`). The pipeline sniffs the real game client's own HDV network
traffic (tshark) while you browse the auction house in-game — this module's `/api/capture/*` routes
just start/stop/poll that external process as a subprocess (`capture/CaptureService.kt`); "stop" means
killing the tracked tshark child, not a clean signal, because the wrapped script's own JSON-writing
logic runs unconditionally either way (verified against its source). **Never point this module at a
live Ankama endpoint** — that's a deliberate, structural constraint (no HTTP client dependency at all),
reacting to a real anti-pattern in the old .NET tool it replaces (an unofficial endpoint call using a
sniffed auth token, disabled by default there).

## Module-local conventions worth not re-discovering

- **Every service function takes `db: Database` explicitly** and calls `transaction(db) { ... }` —
  never Exposed's implicit no-arg `transaction { }`. Skipping this caused a real, reproducible flaky
  test (cross-test data leaking via Exposed's JVM-global "current default database") the first time
  this module was built. If a future module adopts Exposed, this is the first thing to suspect if
  isolated-looking tests start leaking data.
- **Equipment/recipe/harvest catalogs are read straight off `autobuilder`'s committed JSON resources**
  (`equipment/{EquipmentCatalog,RecipeCatalog,HarvestCatalog,MonsterDropCatalog}.kt`, wired via an extra
  Gradle `resources { srcDir(...) }`, not a project dependency) — deliberately, to avoid pulling
  OR-Tools' ~100 native dylibs + Clikt into a REST server just to read a few JSON files.
- **`market-client` holds its own DTOs**, deliberately not shared with this module's — small surface,
  avoids coupling the GUI's wire format to this module's internal response shapes.
- **`PATCH .../prices` appends** `"[corrected_manually]"` to `Comment`; **`PATCH .../flag` overwrites**
  `Comment` entirely with `"[$motif]"` — both copied exactly from the legacy `edit_prices.py` script's
  real behavior, not independently designed. Don't "fix" the flag endpoint's destructive overwrite
  without checking with the user first.
- `./gradlew test` (unqualified, whole repo) is fail-fast and `gui-compose`'s known pre-existing
  Windows-only `HistoryRepositoryTest` failure sits earlier in the task graph — use `--continue`, or
  scope to `:market-server:test`, to actually see this module's test results.
