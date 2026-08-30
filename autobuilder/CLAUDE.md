# CLAUDE.md — `autobuilder` (combat math + solver conventions)

Companion to `common-lib/CLAUDE.md` (the stat model) and root `AGENTS.md` §4 (the CP-SAT engine
architecture, scoring modes, the optimality certificate). This file is the actual **damage formula**
the max-damage mode optimizes and the combat mechanics behind it — read it before touching
`FindMaxDamageScoring.kt`, `SpellDamage.kt`, `WakfuBuildSolver.kt`'s objective, or the certificate.

## The damage formula (`common-lib/.../SpellDamage.expectedDamage`, mirrored by the CP-SAT objective)

```
mastery      = element's own mastery + MASTERY_ELEMENTARY (the "+all elements" aggregate)
             + (MASTERY_DISTANCE or MASTERY_MELEE, per the spell's range band)
             + MASTERY_BACK   (only if the hit is from behind)
             + MASTERY_BERSERK (only if the caster is at/below the berserk HP threshold)
critMastery  = max(MASTERY_CRITICAL, 0)          -- never lets a negative crit mastery help
critRate     = clamp(CRITICAL_HIT, 0..100, ≤ critCapPercent) / 100
diFactor     = 1 + max(DAMAGE_INFLICTED, -floor) / 100         -- own multiplicative term, not mastery
resistFactor = 1 - clamp(targetResistance, -100..+90) / 100    -- can be >1 (weakness) or <1
orientFactor = max(orientationMultiplierPercent, 0) / 100      -- face 100 / side 110 / back 125

nonCrit = baseDamage        × (1 + mastery / 100)               × diFactor × resistFactor × orientFactor
crit    = critBaseDamage    × (1 + (mastery + critMastery)/100) × diFactor × resistFactor × orientFactor
expected = (1 - critRate) × nonCrit + critRate × crit
```

Notes worth not re-deriving from scratch:
- **Only a hit's own element's mastery counts** (plus the "+all elements" aggregate) — a Fire spell
  never benefits from stacked Water mastery. This is *why* max-damage mode's objective picks "the best
  playable element" rather than summing every element (see `AGENTS.md` §4's boss-targeting note).
- **Rear mastery only applies to an actual back hit** — a side hit gets the 1.10 positional multiplier
  but grants no rear-mastery bonus (`Orientation.grantsRearMastery`, `DamageScenario.kt`). Berserk
  mastery gates on HP ≤ 50% (the in-game berserk threshold), not a build stat.
- **The crit base damage already includes the game's flat +25% crit bonus** when the encyclopedia
  exposes a separate crit value for the spell — applying `CRIT_MULTIPLIER` again on top would double-
  count it (a real bug this codebase fixed once; see the formula's own comment). Only synthesize the
  +25% (`base × CRIT_MULTIPLIER`) when no separate crit value exists.
- **Resistance is asymmetric and unbounded above 100%**: `[-100, +90]%` — a `-100`-resistance
  ("weakness") target takes up to double damage (`resistFactor` up to 2.0), matching the CP-SAT
  objective's own bounds exactly (rescoring and the solver must agree at the extremes, or the
  optimality certificate could be unsound — see `AGENTS.md` §4's soundness invariant).
- **`% Damage Inflicted` (`DAMAGE_INFLICTED`) is its own multiplicative factor**, not folded into
  mastery — a Major aptitude (+10/point) and (later) some sublimations grant it; see
  `common-lib/CLAUDE.md`'s Major section.
- **Single-target vs. area mastery is not modeled** — the game exposes no quantifiable source for it in
  this data version (a valueless action marker), and WakForge doesn't model it either; don't assume
  parity with a build tool that claims otherwise without checking its actual math.

## Scoring modes — which stats actually drive the search

- **Most-masteries** (default): AP/MP/range/crit are **hard constraints** at the exact requested value;
  the objective **maximizes** the requested masteries only. See `AGENTS.md` §4's "why the solver can
  leave slots empty" note before treating an empty mount/pet slot as a bug.
- **Precision**: every requested stat is a soft target the scorer tries to hit exactly (`FindClosest
  BuildFromInputScoring`) — no free-maximization term.
- **Max-damage**: the formula above, evaluated against a `DamageScenario` (element/orientation/berserk/
  target resistance — a fixed boss or a manual scenario). Unlike the other two modes it starts
  **constraint-free** by default (`setMode`'s own comment) — seeding an AP/MP/HP target here would only
  hold the solver back from the true highest-damage build.

## Spell rotation (`SpellRotation.kt`, `SpellRotationOptimizer.kt`)

Given a build's resolved stats, picks the AP-budget-optimal sequence of spell casts (max-damage mode
only) — a knapsack over the class's known spells' AP cost vs. expected damage from the formula above.
**WP cost is not folded into the AP budget** (a known, documented gap — `AGENTS.md` §4) — a rotation
that's technically WP-starved can still be suggested; don't "fix" this silently, it's tracked.

## Practical conventions

- **Native engine args required to run/test anything here**: `--enable-native-access=ALL-UNNAMED`,
  `--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED`, `--add-opens=java.base/jdk.internal.misc=
  ALL-UNNAMED` — already wired in `build.gradle.kts` for `run`/`test`, but a raw `java -jar` invocation
  outside Gradle needs them too, or OR-Tools' native load fails outright.
- **`WakfuBuildSolver.kt` is the one file to bump `CERTIFIER_VERSION` in** after any change to the
  optimality-certificate math (fast pass, exact pass, orchestrator, scaling) — see `AGENTS.md` §4's
  soundness invariant; a missed bump serves a stale, possibly-unsound cached bound.
- Item names in `--forced-items`/`--excluded-items` and all filtering match **French** names
  (`equipment.name.fr`) regardless of the CLI/GUI's display language.
