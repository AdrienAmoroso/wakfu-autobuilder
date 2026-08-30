# CLAUDE.md — `common-lib` (Wakfu game fundamentals)

`common-lib` is the pure domain model every other module depends on (see root `AGENTS.md` §3 for the
module map). This file is the game-knowledge companion to that: the Wakfu vocabulary and mechanics
this codebase encodes, grounded in the actual types below — not a general wiki dump. Read it once if
you're not already fluent in Wakfu; nothing elsewhere in the repo re-explains this.

## The 18 classes (`CharacterClass`, `Character.kt`)

One-line archetype per class — useful when a request/bug talks about "a Cra build" or "why does Sram
want back mastery": Feca (shields/defense support), Osamodas (summons), Enutrof (control + kamas/
prospection utility), Sram (stealth, backstab burst — wants `MASTERY_BACK`), Xelor (time control, AP/MP
removal — the one class with base **12 WP** instead of 6, see `Character.baseCharacteristicValues`),
Ecaflip (RNG-based burst), Eniripsa (healer — wants `MASTERY_HEALING`), Iop (melee brawler, AP-hungry),
Cra (ranged — wants `MASTERY_DISTANCE`), Sadida (dolls/poison, support-damage hybrid), Sacrieur (melee,
trades own HP for power — the canonical `MASTERY_BERSERK` class), Pandawa (melee, positioning/pushback),
Roublard (bombs/traps, ranged-leaning), Zobal (melee, mimics other classes' abilities), Ouginak (melee,
rage/bleed), Steamer (ranged, area control), Eliotrope (portals/positioning), Huppermage (must cycle
through all 4 elements for a bonus — the one class that genuinely wants `MASTERY_ELEMENTARY` broadly
rather than one element). Treat these as orientation, not a hard constraint the solver enforces — it
optimizes whatever stats the request actually asks for, regardless of class "theme".

## The stat model (`Characteristic`, `Equipment.kt`)

This is the taxonomy that keeps tripping up "total X" calculations (see the mastery-total bug fixed in
the manual-build sidebar) — get it right before writing any aggregate:

- **Elemental masteries** — `MASTERY_ELEMENTARY_{WATER,FIRE,EARTH,WIND}` (Eau/Feu/Terre/Air in-game),
  each scaling damage of that element, plus the aggregate `MASTERY_ELEMENTARY` (scales all four at
  once — most gear/runes/sublimations that grant "elemental mastery" grant this, not one element) and
  three "N random elements" variants (real in-game rolls the solver treats as their expected/aggregate
  form). **A hit always uses your single best element** — that's why Wakfu's own "total mastery" stat
  sums the specialized masteries below plus only the *highest* single elemental mastery, never a sum of
  all four — a real bug fixed once in the manual-build sidebar's total-mastery display.
- **Specialized ("secondary") masteries** — `MASTERY_{DISTANCE,MELEE,CRITICAL,BACK,BERSERK,HEALING}`:
  conditional multipliers that stack *additively* with your elemental mastery, not separate damage
  sources. Distance = attacks at range ≥2, Melee = range 1, Critical = bonus on a crit, Back = attacking
  from behind the target, Berserk = while below the caster's berserk HP threshold, Healing = only on
  heal spells. `SECONDARY_MASTERY_CHARACTERISTICS` (`Sublimation.kt`) is the canonical set of six — reuse
  it rather than re-listing them; a solver-era comment nearby explains why an *incomplete* set (the
  codebase's own past bug: summing only melee+distance) silently mis-evaluates sublimation conditions.
- **Resistances** mirror this: `RESISTANCE_ELEMENTARY_{WATER,FIRE,EARTH,WIND}` (+ the aggregate and
  random-element variants) are flat elemental damage reduction; `RESISTANCE_CRITICAL`/`RESISTANCE_BACK`
  reduce the crit/back damage bonus specifically.
- **`CONTROL` is never a mastery** — a distinct melee-control stat (push/lock strength), base value 1
  for every class. Never fold it into a masteries total.
- **AP / MP / WP** (`ACTION_POINT`/`MOVEMENT_POINT`/`WAKFU_POINT`, + their `MAX_*` variants): the
  per-turn action/movement budget and the resource "Wakfu"-tier spells consume. Base values (`Character.
  baseCharacteristicValues`): AP=6, MP=3, WP=6 (12 for Xelor), HP=`50 + level*10`, crit=3, control=1.
- **Combat/utility stats**: `RANGE` (spell reach), `HP`, `CRITICAL_HIT` (crit rate %), `DODGE`/`LOCK`
  (escaping vs. holding a melee lock), `INITIATIVE` (turn order), `WISDOM` (WP regen/turn), `WILLPOWER`,
  `BLOCK_PERCENTAGE` (chance to block a crit), `GIVEN_ARMOR_PERCENTAGE`/`RECEIVED_ARMOR_PERCENTAGE`
  (shield mechanics), `DAMAGE_INFLICTED` (a flat **% damage** multiplier — its own multiplicative term
  in the damage formula, distinct from mastery; see the Major aptitude below), `PROSPECTION` (loot-
  rarity/drop-rate — the stat that matters for `market-server`'s monster-farming ROI, not combat).
- **Harvest-profession quantity bonuses**: `{HERBALIST,LUMBERJACK,TRAPPER,MINER,FARMER,FISHERMAN}_
  HARVEST_QUANTITY_PERCENTAGE` — the 6 Wakfu gathering professions; relevant to `market-server`'s harvest
  profitability scanner, not the equipment solver.

## Skill points (`skills/`) — what each branch actually governs

5 branches, each an `Assignable<T>` of `SkillCharacteristic`s (`CharacterSkills`); points available
scale with level, `Major` unlocks new slots at 25/75/125/175. Grounded directly in each branch's file,
not general game lore:
- **Intelligence** — `%HP`, `RESISTANCE_ELEMENTARY`, Shield, `%Heal Received`, `%HP as Armor`: the
  survivability branch.
- **Strength** — `MASTERY_ELEMENTARY`, `MASTERY_DISTANCE`, `MASTERY_MELEE`, `HP`: the raw-offense branch.
- **Agility** — `LOCK`, `DODGE` (+ a paired Dodge/Lock point), `INITIATIVE`, `WILLPOWER`: the
  mobility/control branch.
- **Luck** — `%CRITICAL_HIT`, `%BLOCK`, `MASTERY_CRITICAL`, `MASTERY_BACK`, `MASTERY_BERSERK`,
  `MASTERY_HEALING`, `RESISTANCE_BACK`, `RESISTANCE_CRITICAL`: crit + positional/healing offense.
- **Major** (`Major.kt`) — the 7 expensive, 1-point-each, unlock-gated bonuses: `ACTION_POINT` (+1 AP),
  Movement Point **+** `MASTERY_ELEMENTARY` (paired), Range **+** `MASTERY_ELEMENTARY` (paired),
  `WAKFU_POINT` (+2), Control **+** `MASTERY_ELEMENTARY` (paired), `%DAMAGE_INFLICTED` (+10pts),
  `RESISTANCE_ELEMENTARY` (+50%). The paired ones (`PairedCharacteristic`) are why "1 Major point"
  sometimes shows two stat increases at once in the UI — that's correct, not a display bug.

## Equipment (`Equipment.kt`)

- **Rarity** (`Rarity`, ordered `COMMON < UNCOMMON < RARE < MYTHIC < LEGENDARY < RELIC < SOUVENIR <
  EPIC`): a build may carry **at most 1 EPIC and 1 RELIC**. `RARITY_ID_TO_RARITY` is the CDN numeric-id
  mapping every extractor must share (don't hand-roll a second copy — see its own doc comment for why).
- **Slots** (`ItemType`, 14 total, each with Ankama's numeric id used by rune-doubling/CDN lookups):
  amulet, ring (×2, independently selectable — not a doubled slot), boots, helmet, cape, belt,
  chestplate, shoulder pads, emblem, pet, mount, and weapons (1H / 2H / off-hand, mutually exclusive per
  `BuildCombination.isValid()`).
- **Sockets / runes ("châsses"/"éclats")** — `maxShardSlots` (0–4) per item; `RuneType` models 15 runes
  (one per supported stat), each socket-**colour**-gated (`RuneColor`: red/green/blue, matching Ankama's
  `shardsParameters.color`; a **gold/wildcard** socket — this codebase's manual-build "gold toggle" —
  matches any colour) and **doubled** when placed on one of the rune's favoured slots
  (`doubleBonusPosition`). Values follow the **best-achievable (max enchantment level for the carrier's
  item level, doubled where applicable)** model — see the type's own doc comment and
  `docs/ENCHANTMENTS_PLAN.md` for the full reasoning and WakForge-sourced value tables.
- **Sublimations** (`Sublimation.kt`) — a further per-item bonus slot (normal 3-colour-socketed items,
  or a dedicated slot for EPIC/RELIC carriers), decoded from the game's own condition-script format into
  4 kinds (`SublimationKind`): `FLAT` (always on), `STATIC_CONDITIONAL` (a build-static condition, e.g.
  "AP ≤ N" — solver-modelable), `CONVERSION` (moves % of one stat into another), `COMBAT_CONDITIONAL`
  (depends on in-combat events — **not** solver-modelable, forced-input only). See root `AGENTS.md` §5
  for the extraction pipeline and the solver-choosable-vs-forced-only split.

## Recipes (`Recipe.kt`) & professions

`Recipe` = one crafting-profession output (`jobId`/`jobName`, e.g. Baker, Handyman, ...) plus its
ingredient list (`RecipeIngredient`, itemId + quantity) — the crafting side of Wakfu's economy, distinct
from the 6 *harvest* professions above. Feeds `market-server`'s craft-cost/profitability features (its
own `CLAUDE.md` covers the economy vocabulary — ROI, expected value, HDV — built on top of this).
