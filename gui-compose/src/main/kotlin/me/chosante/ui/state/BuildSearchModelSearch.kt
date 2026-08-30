package me.chosante.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.chosante.autobuilder.domain.BuildCombination
import me.chosante.autobuilder.domain.DamageScenario
import me.chosante.autobuilder.domain.PassiveCatalog
import me.chosante.autobuilder.domain.ScenarioDamage
import me.chosante.autobuilder.domain.SpellElement
import me.chosante.autobuilder.domain.SpellRotation
import me.chosante.autobuilder.domain.SpellRotationOptimizer
import me.chosante.autobuilder.domain.TargetStat
import me.chosante.autobuilder.domain.TargetStats
import me.chosante.autobuilder.domain.against
import me.chosante.autobuilder.domain.againstAllElements
import me.chosante.autobuilder.genetic.SolverResult
import me.chosante.autobuilder.genetic.wakfu.MaxDamageSearch
import me.chosante.autobuilder.genetic.wakfu.ScoreComputationMode
import me.chosante.autobuilder.genetic.wakfu.WakfuBestBuildFinderAlgorithm
import me.chosante.autobuilder.genetic.wakfu.WakfuBestBuildParams
import me.chosante.autobuilder.genetic.wakfu.computeCharacteristicsValues
import me.chosante.autobuilder.genetic.wakfu.isMaximizableMastery
import me.chosante.common.Character
import me.chosante.common.Characteristic
import me.chosante.common.Equipment
import me.chosante.common.ItemType
import me.chosante.common.Monster
import me.chosante.common.Rarity
import me.chosante.common.history.HistoryEntry
import me.chosante.ui.history.historyJson
import me.chosante.ui.history.suggestedBuildName
import me.chosante.ui.history.toHistoryEntry
import me.chosante.ui.i18n.Tr
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// [BuildSearchModel]'s auto-Builder request+result functions (search mode/targets/constraints,
// running the solver, the optimality-proof flow, and the Builder-screen Zenith export) -- split out
// of BuildSearchModel.kt purely to keep that file from growing without bound as more screens were
// added; behavior is unchanged, this is the same class's body in a different file.
fun BuildSearchModel.setMode(mode: ScoreComputationMode) {
    val normalizedTargets =
        when (mode) {
            ScoreComputationMode.FIND_BUILD_WITH_MOST_MASTERIES_FROM_INPUT ->
                ui.targets.map { target ->
                    if (target.characteristic.isMaximizableMastery()) {
                        target.copy(value = "1")
                    } else {
                        target
                    }
                }
            // Max-damage maximizes the rotation's real damage directly, so the seeded AP/MP/range/HP/crit
            // rows would only act as hard power-6 constraints that can exclude higher-damage builds (e.g.
            // pinning AP=11 stops the solver finding the best AP breakpoint). Start CONSTRAINT-FREE; the user
            // can still add an explicit target row (an AP floor, a min HP…) if they want a more playable build.
            ScoreComputationMode.FIND_BUILD_WITH_MAX_DAMAGE -> emptyList()
            else -> ui.targets
        }
    // Switching mode invalidates any completed result: a build/match/rotation found under the old mode
    // would be reinterpreted under the new mode's display rules. Clear it so the UI returns to Idle.
    ui =
        ui.copy(
            mode = mode,
            targets = normalizedTargets,
            phase = Phase.Idle,
            progress = 0,
            match = java.math.BigDecimal.ZERO,
            optimal = false,
            build = null,
            achieved = emptyMap(),
            spellRotation = null,
            scenarioDamages = emptyList()
        )
}

fun BuildSearchModel.setScenario(scenario: DamageScenario) {
    // Turning the survivability floor on (via the toggle or the Tank preset) without a value would be a
    // silent no-op — default the floor from the level so it actually nudges the build (and the min-EHP
    // field shows a tunable number rather than 0).
    val withFloor =
        if (scenario.survivabilityFloor && scenario.minEffectiveHp <= 0) {
            scenario.copy(minEffectiveHp = DamageScenario.defaultMinEffectiveHp(ui.level))
        } else {
            scenario
        }
    ui = ui.copy(scenario = withFloor)
}

/**
 * Target [monster] in max-damage mode: switch to max-damage (the boss fills the per-element
 * resistances the objective optimizes over) and close the picker — mirroring the CLI's `--boss`,
 * which also forces max-damage. Clears any stale result computed under the previous scenario.
 */
fun BuildSearchModel.pickBoss(monster: Monster) {
    ui =
        ui.copy(
            selectedBoss = monster,
            mode = ScoreComputationMode.FIND_BUILD_WITH_MAX_DAMAGE,
            // Same as setMode: max-damage is constraint-free by default (seeded AP/MP/HP targets would only
            // hold the solver back from the highest-damage build vs this boss).
            targets = emptyList(),
            modal = null,
            phase = Phase.Idle,
            progress = 0,
            match = java.math.BigDecimal.ZERO,
            optimal = false,
            build = null,
            achieved = emptyMap(),
            spellRotation = null,
            scenarioDamages = emptyList()
        )
}

/** Drop the boss target; the next search falls back to the manual damage [UiState.scenario]. */
fun BuildSearchModel.clearBoss() {
    ui = ui.copy(selectedBoss = null, bossElement = null)
}

/** Force the damage element vs the boss, or null to let the objective auto-pick the best one. */
fun BuildSearchModel.setBossElement(element: SpellElement?) {
    ui = ui.copy(bossElement = element)
}

/** Dungeon HP multiplier (integer) for the turns-to-kill estimate; display only, never the build. */
fun BuildSearchModel.setBossDifficulty(value: String) {
    ui = ui.copy(bossDifficulty = value.onlyDigits().take(3))
}

fun BuildSearchModel.updateTargetValue(
    id: String,
    value: String,
) {
    ui = ui.copy(targets = ui.targets.map { if (it.id == id) it.copy(value = value.onlyDigits()) else it })
}

/** Sets a constraint's priority (#123), clamped to 1..5 — the segmented bar. See [TargetRow.weight]. */
fun BuildSearchModel.updateTargetWeight(
    id: String,
    weight: Int,
) {
    ui = ui.copy(targets = ui.targets.map { if (it.id == id) it.copy(weight = weight.coerceIn(1, 5)) else it })
}

fun BuildSearchModel.removeTarget(id: String) {
    ui = ui.copy(targets = ui.targets.filterNot { it.id == id })
}

fun BuildSearchModel.addTarget(characteristic: Characteristic) {
    if (ui.targets.any { it.characteristic == characteristic }) {
        return
    }
    statDefFor(characteristic)?.let { def ->
        ui =
            ui.copy(
                targets = ui.targets + def.toRow(if (characteristic.isMaximizableMastery()) "1" else "0")
            )
    }
}

fun BuildSearchModel.toggleMaximizedMastery(characteristic: Characteristic) {
    if (!characteristic.isMaximizableMastery()) {
        return
    }
    val alreadySelected = ui.targets.any { it.characteristic == characteristic }
    if (alreadySelected) {
        ui = ui.copy(targets = ui.targets.filterNot { it.characteristic == characteristic })
        return
    }
    val row = statDefFor(characteristic)?.toRow("1") ?: return
    // "All elements" and the specific elements are mutually exclusive: they express two distinct
    // intents (a build balanced over the four vs. one focused on those elements), and combining
    // them is what made the engine optimise the wrong thing. Selecting one clears the other;
    // non-elemental masteries (distance/crit/…) are never cleared.
    val conflicting =
        when (characteristic) {
            Characteristic.MASTERY_ELEMENTARY -> ELEMENTAL_MASTERY_ELEMENTS
            in ELEMENTAL_MASTERY_ELEMENTS -> setOf(Characteristic.MASTERY_ELEMENTARY)
            else -> emptySet()
        }
    ui = ui.copy(targets = ui.targets.filterNot { it.characteristic in conflicting } + row)
}

/** Screenshot-only: seed excluded rarities from WAKFU_COMPOSE_SCREENSHOT_EXCLUDE_RARITIES (comma list). */
internal fun BuildSearchModel.screenshotExcludedRarities(): Set<Rarity>? {
    val raw =
        System.getProperty("wakfu.compose.screenshot.excludeRarities")
            ?: System.getenv("WAKFU_COMPOSE_SCREENSHOT_EXCLUDE_RARITIES") ?: return null
    return raw
        .split(",")
        .mapNotNull { token -> runCatching { Rarity.valueOf(token.trim().uppercase()) }.getOrNull() }
        .toSet()
        .ifEmpty { null }
}

/**
 * Toggle whether [rarity] is allowed in the search (#124). Excluding the last still-allowed rarity
 * is refused — an all-excluded set would leave the solver no items at all.
 */
fun BuildSearchModel.toggleRarity(rarity: Rarity) {
    val excluded = ui.excludedRarities
    val next =
        if (rarity in excluded) {
            ui.copy(excludedRarities = excluded - rarity)
        } else if (excluded.size < Rarity.entries.size - 1) {
            ui.copy(excludedRarities = excluded + rarity)
        } else {
            return
        }
    ui = next
    reconcileForcedItemsForCurrentRequest()
}

internal fun BuildSearchModel.reconcileForcedItemsForCurrentRequest() {
    val snapshot = ui
    if (snapshot.forcedItems.isEmpty()) return
    scope.launch(Dispatchers.Default) {
        val byFrenchName = WakfuBestBuildFinderAlgorithm.equipments.groupBy { it.name.fr }
        val kept =
            snapshot.forcedItems.filter { chip ->
                val matches = byFrenchName[chip.matchName].orEmpty()
                matches.isEmpty() || matches.any { it.isEquippableIn(snapshot) }
            }
        val removed = snapshot.forcedItems.size - kept.size
        if (removed <= 0) return@launch
        withContext(mainDispatcher) {
            if (ui.level == snapshot.level &&
                ui.minLevel == snapshot.minLevel &&
                ui.maxRarity == snapshot.maxRarity &&
                ui.excludedRarities == snapshot.excludedRarities &&
                ui.forcedItems == snapshot.forcedItems
            ) {
                ui =
                    ui.copy(
                        forcedItems = kept,
                        toast = Tr.TOAST_FORCED_ITEMS_REMOVED.value(ui.lang).format(removed)
                    )
            }
        }
    }
}

private fun Equipment.isEquippableIn(state: UiState): Boolean {
    val levelOk = itemType == ItemType.PETS || itemType == ItemType.MOUNTS || level in state.minLevel..state.level
    val rarityOk = rarity <= state.maxRarity && rarity !in state.excludedRarities
    return levelOk && rarityOk
}

fun BuildSearchModel.setDuration(duration: String) {
    ui = ui.copy(duration = duration.onlyDigits().take(3))
}

fun BuildSearchModel.setStopAtMatch(stopAtMatch: Boolean) {
    ui = ui.copy(stopAtMatch = stopAtMatch)
}

fun BuildSearchModel.removeForcedItem(item: ItemChip) {
    ui = ui.copy(forcedItems = ui.forcedItems - item)
}

fun BuildSearchModel.removeExcludedItem(item: ItemChip) {
    ui = ui.copy(excludedItems = ui.excludedItems - item)
}

// Backs [BuildSearchModel.runeOptions] below. Stateless/pure (just a sorted view of the embedded rune
// data), so a module-level lazy is equivalent to the per-instance `by lazy` this used to be (there is
// only ever one live BuildSearchModel instance in the app) -- and a delegated extension property can't
// hold a `by lazy` of its own (no per-receiver storage), so the property just reads through to this.
private val ALL_RUNE_OPTIONS: List<me.chosante.common.RuneType> by lazy {
    WakfuBestBuildFinderAlgorithm.runes.sortedBy { it.name.fr.lowercase() }
}

/** The modeled runes ([me.chosante.common.RuneType]) the user can pin onto an item, sorted for the picker. */
val BuildSearchModel.runeOptions: List<me.chosante.common.RuneType>
    get() = ALL_RUNE_OPTIONS

fun BuildSearchModel.setUseSublimations(enabled: Boolean) {
    ui = ui.copy(useSublimations = enabled)
}

fun BuildSearchModel.setMaxSublimationTier(tier: Int?) {
    ui = ui.copy(maxSublimationTier = tier)
}

fun BuildSearchModel.addForcedSublimation(name: String) {
    if (name.isNotBlank() && name !in ui.forcedSublimations) ui = ui.copy(forcedSublimations = ui.forcedSublimations + name)
}

fun BuildSearchModel.removeForcedSublimation(name: String) {
    ui = ui.copy(forcedSublimations = ui.forcedSublimations - name)
}

fun BuildSearchModel.addExcludedSublimation(name: String) {
    if (name.isNotBlank() && name !in ui.excludedSublimations) ui = ui.copy(excludedSublimations = ui.excludedSublimations + name)
}

fun BuildSearchModel.removeExcludedSublimation(name: String) {
    ui = ui.copy(excludedSublimations = ui.excludedSublimations - name)
}

/**
 * Apply the sublimation chosen from the [Modal.SublimationPicker] modal — forced by default, EXCLUDED
 * when the picker was opened in exclude mode — then close it. The engine matches sublimations by their
 * **French** name, so that's the key we store (regardless of UI lang).
 */
fun BuildSearchModel.pickSublimation(sub: me.chosante.common.Sublimation) {
    val exclude = (ui.modal as? Modal.SublimationPicker)?.exclude == true
    if (exclude) addExcludedSublimation(sub.name.fr) else addForcedSublimation(sub.name.fr)
}

fun BuildSearchModel.removeForcedPassive(name: String) {
    ui = ui.copy(forcedPassives = ui.forcedPassives - name)
}

/**
 * Add the passive chosen from the [Modal.PassivePicker] to the loadout (matched by **French** name by
 * the engine), capped to the level's passive slots, then close the modal. A duplicate or over-cap pick
 * is ignored.
 */
fun BuildSearchModel.pickPassive(passive: me.chosante.common.Passive) {
    // forcedPassives stores the canonical FRENCH name (the engine matches passives by French).
    val name = passive.name?.fr ?: return
    val slots =
        me.chosante.autobuilder.domain.PassiveCatalog
            .slotsForLevel(ui.level)
    if (name !in ui.forcedPassives && ui.forcedPassives.size < slots) {
        ui = ui.copy(forcedPassives = ui.forcedPassives + name)
    }
}

/** Open the per-item rune picker for [equipment] (only meaningful when the item has sockets). */
fun BuildSearchModel.openItemRunePicker(equipment: me.chosante.common.Equipment) {
    openModal(Modal.ItemRunePicker(equipment.name.fr))
}

/** Rune ids currently pinned onto the item with this French name. */
fun BuildSearchModel.pinnedRunes(itemName: String): List<Int> = ui.forcedRunesByItem[itemName].orEmpty()

/** Replace the runes pinned onto [itemName] (an empty list clears the entry), then close the picker. */
fun BuildSearchModel.setForcedRunesForItem(
    itemName: String,
    runeIds: List<Int>,
) {
    val updated =
        if (runeIds.isEmpty()) {
            ui.forcedRunesByItem - itemName
        } else {
            ui.forcedRunesByItem + (itemName to runeIds)
        }
    ui = ui.copy(forcedRunesByItem = updated, modal = null)
}

/** Copy the runes currently displayed on [equipment] into the per-item forced-runes request. */
fun BuildSearchModel.lockCurrentRunes(equipment: me.chosante.common.Equipment) {
    val runeIds =
        ui.build
            ?.runes
            ?.get(equipment)
            .orEmpty()
            .map { it.id }
    if (runeIds.isEmpty()) return
    ui =
        ui.copy(
            forcedRunesByItem = ui.forcedRunesByItem + (equipment.name.fr to runeIds),
            toast = Tr.TOAST_RUNES_LOCKED.value(ui.lang)
        )
}

/**
 * Force this exact item into the next searched build. Driven by the center paperdoll's per-slot
 * action; mirrors [pickItem]'s dedup but is independent of the picker modal and, like the modal,
 * does **not** re-run the search.
 */
fun BuildSearchModel.forceItem(equipment: me.chosante.common.Equipment) {
    pinForced(equipment.toChip())
}

/** Exclude this exact item from the next search. Paperdoll counterpart to [forceItem]. */
fun BuildSearchModel.excludeItem(equipment: me.chosante.common.Equipment) {
    pinExcluded(equipment.toChip())
}

/**
 * Pin [chip] as required. Forcing and excluding are contradictory constraints, so this also drops
 * the item from the excluded list ([pinExcluded] is the mirror): the same item can never sit in
 * both lists — otherwise the engine's exclude filter wins and silently ignores the force, leaving
 * the item invisible yet still listed as forced. Re-pinning an already-forced item is a no-op.
 */
internal fun BuildSearchModel.pinForced(chip: ItemChip) {
    ui =
        ui.copy(
            forcedItems = if (ui.forcedItems.any { it.matchName == chip.matchName }) ui.forcedItems else ui.forcedItems + chip,
            excludedItems = ui.excludedItems.filterNot { it.matchName == chip.matchName }
        )
}

/** Pin [chip] as excluded, dropping it from the forced list. Mirror of [pinForced]. */
internal fun BuildSearchModel.pinExcluded(chip: ItemChip) {
    ui =
        ui.copy(
            excludedItems = if (ui.excludedItems.any { it.matchName == chip.matchName }) ui.excludedItems else ui.excludedItems + chip,
            forcedItems = ui.forcedItems.filterNot { it.matchName == chip.matchName }
        )
}

fun BuildSearchModel.search() {
    job?.cancel()
    proofCancelled.set(true) // B8: stop the certifier DP inside the job, not just the coroutine
    proofJob?.cancel()
    val snapshot = ui
    val character = Character(snapshot.clazz, snapshot.level, snapshot.minLevel)
    val targetStats = snapshot.toTargetStats()
    // A targeted boss overlays its per-element resistances onto the manual scenario (mirrors the CLI):
    // a forced element pins that one element, else all four are filled so the objective auto-picks.
    val damageScenario =
        when {
            snapshot.selectedBoss != null && snapshot.bossElement != null ->
                snapshot.scenario.against(snapshot.selectedBoss, snapshot.bossElement)
            snapshot.selectedBoss != null -> snapshot.scenario.againstAllElements(snapshot.selectedBoss)
            else -> snapshot.scenario
        }
    val params =
        WakfuBestBuildParams(
            character = character,
            targetStats = targetStats,
            // Blank duration = the longest sensible run (10 min). Kept finite on purpose: an unbounded
            // budget would make the time-driven progress bar meaningless and risk a search that never
            // returns on a hard input. (QOL-2)
            searchDuration = (snapshot.duration.toIntOrNull() ?: 600).coerceAtLeast(1).seconds,
            // "Stop at 100% match" only applies to precision mode (the only mode with an exact target);
            // ignore a stale toggle when searching in most-masteries / max-damage.
            stopWhenBuildMatch = snapshot.stopAtMatch && snapshot.mode == ScoreComputationMode.FIND_CLOSEST_BUILD_FROM_INPUT,
            maxRarity = snapshot.maxRarity,
            excludedRarities = snapshot.excludedRarities,
            forcedItems = snapshot.forcedItems.map { it.matchName },
            excludedItems = snapshot.excludedItems.map { it.matchName },
            scoreComputationMode = snapshot.mode,
            useSublimations = snapshot.useSublimations,
            maxSublimationTier = snapshot.maxSublimationTier,
            forcedSublimations = snapshot.forcedSublimations,
            excludedSublimations = snapshot.excludedSublimations,
            forcedPassives = snapshot.forcedPassives,
            forcedRunesByItem = snapshot.forcedRunesByItem,
            damageScenario = damageScenario
        )

    // Validate the whole request up front and surface ALL problems together in a pop-up
    // (UiState.requestErrors) instead of throwing on the first and burying it in the results-panel banner.
    val requestProblems = WakfuBestBuildFinderAlgorithm.validateRequest(params)
    if (requestProblems.isNotEmpty()) {
        ui = snapshot.copy(requestErrors = requestProblems)
        return
    }

    ui =
        snapshot.copy(
            phase = Phase.Searching,
            progress = 0,
            match = java.math.BigDecimal.ZERO,
            optimal = false,
            proofState = ProofState.Idle,
            build = null,
            achieved = emptyMap(),
            spellRotation = null,
            scenarioDamages = emptyList(),
            lastLandedEquipmentId = null,
            zenith = ZenithState.Idle,
            zenithUrl = null,
            toast = null,
            error = null,
            requestErrors = emptyList()
        )
    job =
        scope.launch(Dispatchers.Default) {
            // The CP-SAT solver only reports progress when it finds a *better* solution, which can
            // be many seconds apart — or stop entirely once the first good build is found — so the
            // bar would sit frozen and the app looks dead mid-search. The budget is wall-clock, so
            // we drive the bar smoothly from elapsed time here; the solver callbacks below keep
            // refreshing the actual build/mastery. Child of `job`, so it dies with the search.
            val searchStartMs = clock()
            val searchDurationMs = params.searchDuration.inWholeMilliseconds.coerceAtLeast(1)
            val progressTicker =
                launch(mainDispatcher) {
                    while (isActive) {
                        if (ui.phase == Phase.Searching) {
                            val pct = ((clock() - searchStartMs).toDouble() / searchDurationMs * 100).toInt().coerceIn(0, 99)
                            if (pct > ui.progress) ui = ui.copy(progress = pct)
                        }
                        delay(120)
                    }
                }
            try {
                var hasResult = false
                // The per-position damage breakdown is a result-level detail that runs 3-4 extra rotations,
                // so it's computed ONCE for the final build after the stream settles (below) — not on every
                // streamed improvement. These capture the last build/achieved/headline-rotation for that.
                var finalBuild: BuildCombination? = null
                var finalAchieved: Map<Characteristic, Int> = emptyMap()
                var finalRotation: SpellRotation? = null
                // The final SolverResult (build + isOptimal + CP-SAT objective) drives the post-search
                // optimality certificate below.
                var finalResult: SolverResult<BuildCombination>? = null
                buildFinder(params)
                    .conflate()
                    .collect { result ->
                        hasResult = true
                        // Resolve the achieved per-stat grid with the SAME random-element assignment the scorer
                        // used, so the displayed values match the score: most-masteries → exact max-min,
                        // precision → exact max-capped, max-damage → greedy. Mirrors FindMostMasteriesFromInputScoring;
                        // omitting the mode would fall to the greedy `else` branch and diverge from the score.
                        val masteryElementsToMinimize =
                            if (params.scoreComputationMode == ScoreComputationMode.FIND_BUILD_WITH_MOST_MASTERIES_FROM_INPUT) {
                                targetStats.masteryElementsToMinimize
                            } else {
                                null
                            }
                        val resistanceElementsToMinimize =
                            if (params.scoreComputationMode == ScoreComputationMode.FIND_BUILD_WITH_MOST_MASTERIES_FROM_INPUT &&
                                targetStats.any { it.characteristic == Characteristic.RESISTANCE_ELEMENTARY }
                            ) {
                                targetStats.resistanceElementsWanted.keys.toList()
                            } else {
                                null
                            }
                        val achieved =
                            computeCharacteristicsValues(
                                buildCombination = result.individual,
                                characterBaseCharacteristics = character.baseCharacteristicValues,
                                masteryElementsWanted = targetStats.masteryElementsWanted,
                                resistanceElementsWanted = targetStats.resistanceElementsWanted,
                                scoreComputationMode = params.scoreComputationMode,
                                masteryElementsToMinimize = masteryElementsToMinimize,
                                resistanceElementsToMinimize = resistanceElementsToMinimize
                            )
                        // Best spells to cast for this build's AP — only in max-damage mode, computed
                        // here off the UI thread (like `achieved`) so the panel just reads it. Uses the
                        // boss-overlaid `damageScenario` (not the raw `snapshot.scenario`) and picks the
                        // build's best playable element, so the shown rotation is exactly the turn that was scored.
                        val spellRotation =
                            if (snapshot.mode == ScoreComputationMode.FIND_BUILD_WITH_MAX_DAMAGE) {
                                SpellRotationOptimizer.bestSequencedRotation(
                                    result.individual,
                                    character,
                                    character.clazz,
                                    damageScenario
                                )
                            } else {
                                null
                            }
                        finalBuild = result.individual
                        finalAchieved = achieved
                        finalRotation = spellRotation
                        finalResult = result
                        withContext(mainDispatcher) {
                            val landedEquipmentId = newlyLandedEquipmentId(ui.build, result.individual)
                            ui =
                                ui.copy(
                                    // `progress` is driven smoothly by progressTicker (time-based);
                                    // the solver only emits on improvements, so don't set it here.
                                    match = result.matchPercentage,
                                    optimal = result.isOptimal,
                                    maxDamageStructural = result.maxDamageHeuristicPhases,
                                    build = result.individual,
                                    achieved = achieved,
                                    spellRotation = spellRotation,
                                    lastLandedEquipmentId = landedEquipmentId ?: ui.lastLandedEquipmentId
                                )
                            if (landedEquipmentId != null) {
                                clearLandedMarkerLater(landedEquipmentId)
                            }
                            if (screenshotForceFirst && ui.forcedItems.isEmpty() && ui.excludedItems.isEmpty()) {
                                result.individual.equipments
                                    .getOrNull(0)
                                    ?.let { forceItem(it) }
                                result.individual.equipments
                                    .getOrNull(1)
                                    ?.let { excludeItem(it) }
                            }
                        }
                    }
                // Compute the per-position breakdown ONCE for the final build, still off the UI thread
                // (we're on Dispatchers.Default here), reusing the final headline rotation for the
                // configured combo so only the OTHER positions pay a rotation.
                val finalBuildSnapshot = finalBuild
                val scenarioDamages =
                    if (snapshot.mode == ScoreComputationMode.FIND_BUILD_WITH_MAX_DAMAGE && finalBuildSnapshot != null) {
                        SpellRotationOptimizer.scenarioBreakdown(
                            finalBuildSnapshot,
                            character,
                            character.clazz,
                            damageScenario,
                            includeBerserk = (finalAchieved[Characteristic.MASTERY_BERSERK] ?: 0) > 0,
                            configuredRotationTotal = finalRotation?.totalExpectedDamage
                        )
                    } else {
                        emptyList()
                    }
                val completedResult = finalResult
                withContext(mainDispatcher) {
                    if (ui.phase == Phase.Searching && hasResult) {
                        // Snap the time-based bar to a clean 100% on completion (the solver may
                        // have proven the optimum well before the wall-clock budget ran out).
                        ui = ui.copy(phase = Phase.Done, progress = 100, scenarioDamages = scenarioDamages, lastLandedEquipmentId = null)
                        // Certificate optimality proof (P4.4): only for max-damage, and off the search's
                        // critical path — a full exact solve can take minutes, so it runs in its own job and
                        // streams its verdict into [UiState.proofState] when ready. It can prove an optimum
                        // CP-SAT left un-closed (badge flips to proven even when `optimal` was false).
                        if (params.scoreComputationMode == ScoreComputationMode.FIND_BUILD_WITH_MAX_DAMAGE && completedResult != null) {
                            launchOptimalityProof(params, completedResult, character, damageScenario)
                        }
                    } else if (ui.phase == Phase.Searching) {
                        ui =
                            ui.copy(
                                phase = Phase.Idle,
                                progress = 0,
                                error = Tr.SEARCH_NO_RESULT.value(ui.lang),
                                lastLandedEquipmentId = null
                            )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                // Catch Throwable (not just Exception): the solver can raise fatal Errors
                // (e.g. native-access / linkage issues) that would otherwise crash the event
                // thread with a masked coroutines error instead of surfacing here. Request-validation
                // problems are caught BEFORE the search starts (see validateRequest above), so they
                // don't reach here.
                throwable.printStackTrace()
                val message = throwable.message ?: throwable::class.qualifiedName ?: "Search failed"
                withContext(mainDispatcher) {
                    ui = ui.copy(phase = Phase.Idle, error = message)
                }
            } finally {
                progressTicker.cancel()
            }
        }
}

/**
 * Computes the AP-cell certificate optimality proof (P4.4) off the UI thread and streams the verdict into
 * [UiState.proofState]. The certificate solve is a blocking call that can take minutes, so it runs in its
 * own [proofJob]; the result is only applied while the shown build is still the one it was proving (a new
 * search / build swap invalidates it). Failures degrade to [ProofState.Unavailable] — never a wrong badge.
 */
internal fun BuildSearchModel.launchOptimalityProof(
    params: WakfuBestBuildParams,
    result: SolverResult<BuildCombination>,
    character: Character,
    damageScenario: DamageScenario,
) {
    val provenBuild = result.individual
    proofCancelled.set(false)
    val proofStartMs = clock()

    // The proof-progress callback (v1): phase transitions observed HERE feed it; a later per-cell hook
    // from the certifier DP can call the same function with cellsDone/cellsTotal filled in.
    suspend fun reportProofProgress(progress: ProofProgress) {
        withContext(mainDispatcher) {
            // Only while the shown build is still the one being proven, and never resurrect a badge a
            // cancel/load already reset (proofState must still be Proving — or Idle for the first report).
            if (ui.phase == Phase.Done &&
                ui.build == provenBuild &&
                (ui.proofState is ProofState.Proving || ui.proofState == ProofState.Idle)
            ) {
                ui = ui.copy(proofState = ProofState.Proving(progress))
            }
        }
    }
    proofJob =
        scope.launch(Dispatchers.Default) {
            reportProofProgress(ProofProgress(phase = ProofPhase.CERTIFYING, startedAtMs = proofStartMs))
            val proof =
                try {
                    optimalityProver(params, result) { proofCancelled.get() }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    throwable.printStackTrace()
                    MaxDamageSearch.MaxDamageProof.Unavailable
                }
            // E8 fast-path: a ProvenWithin verdict means the certificate has proven a strictly better build
            // EXISTS than the search reached. Try to CONSTRUCT that proven optimum from the same certificate DP
            // (off the UI thread, here). On success we swap the shown build to it and flip the badge to
            // ProvenOptimal — recomputing its stats / rotation / scenario breakdown EXACTLY as the search did
            // (same character + boss-overlaid scenario), so the whole sheet stays consistent with the paperdoll.
            val upgrade =
                if (proof is MaxDamageSearch.MaxDamageProof.ProvenWithin) {
                    reportProofProgress(ProofProgress(phase = ProofPhase.CONSTRUCTING, startedAtMs = proofStartMs))
                    try {
                        WakfuBestBuildFinderAlgorithm.constructMaxDamageProvenOptimum(params, result)?.let { up ->
                            val upBuild = up.individual
                            val upAchieved =
                                computeCharacteristicsValues(
                                    buildCombination = upBuild,
                                    characterBaseCharacteristics = character.baseCharacteristicValues,
                                    masteryElementsWanted = params.targetStats.masteryElementsWanted,
                                    resistanceElementsWanted = params.targetStats.resistanceElementsWanted,
                                    scoreComputationMode = params.scoreComputationMode,
                                    masteryElementsToMinimize = null,
                                    resistanceElementsToMinimize = null
                                )
                            val upRotation = SpellRotationOptimizer.bestSequencedRotation(upBuild, character, character.clazz, damageScenario)
                            val upScenario =
                                SpellRotationOptimizer.scenarioBreakdown(
                                    upBuild,
                                    character,
                                    character.clazz,
                                    damageScenario,
                                    includeBerserk = (upAchieved[Characteristic.MASTERY_BERSERK] ?: 0) > 0,
                                    configuredRotationTotal = upRotation?.totalExpectedDamage
                                )
                            UpgradedBuild(upBuild, upAchieved, upRotation, upScenario, up.matchPercentage)
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        throwable.printStackTrace()
                        null
                    }
                } else {
                    null
                }
            val state =
                when (proof) {
                    MaxDamageSearch.MaxDamageProof.ProvenOptimal -> ProofState.ProvenOptimal
                    is MaxDamageSearch.MaxDamageProof.ProvenWithin -> if (upgrade != null) ProofState.ProvenOptimal else ProofState.ProvenWithin(proof.fraction)
                    MaxDamageSearch.MaxDamageProof.Unavailable -> ProofState.Unavailable
                }
            withContext(mainDispatcher) {
                if (ui.phase == Phase.Done && ui.build == provenBuild) {
                    ui =
                        if (upgrade != null) {
                            ui.copy(
                                build = upgrade.build,
                                achieved = upgrade.achieved,
                                spellRotation = upgrade.rotation,
                                scenarioDamages = upgrade.scenario,
                                match = upgrade.match,
                                optimal = true,
                                proofState = state
                            )
                        } else {
                            ui.copy(proofState = state)
                        }
                }
            }
        }
}

// The E8-constructed proven optimum + its derived sheet fields, computed off the UI thread in
// [launchOptimalityProof] and applied together so the swapped build's stats stay consistent.
private data class UpgradedBuild(
    val build: BuildCombination,
    val achieved: Map<Characteristic, Int>,
    val rotation: SpellRotation?,
    val scenario: List<ScenarioDamage>,
    val match: java.math.BigDecimal,
)

fun BuildSearchModel.cancel() {
    job?.cancel()
    job = null
    proofCancelled.set(true) // B8: stop the certifier DP inside the job, not just the coroutine
    proofJob?.cancel()
    proofJob = null
    ui = ui.copy(phase = Phase.Idle, progress = 0, proofState = ProofState.Idle)
}

/** View the currently displayed build through max-damage damage/rotation cards without re-running the solver. */
fun BuildSearchModel.viewCurrentBuildAsMaxDamage() {
    val snapshot = ui
    val build = snapshot.build ?: return
    job?.cancel()
    proofCancelled.set(true)
    proofJob?.cancel()
    val character = Character(snapshot.clazz, snapshot.level, snapshot.minLevel).copy(characterSkills = build.characterSkills)
    val damageScenario = snapshot.currentDamageScenario()
    ui =
        snapshot.copy(
            mode = ScoreComputationMode.FIND_BUILD_WITH_MAX_DAMAGE,
            phase = Phase.Done,
            progress = 100,
            optimal = false,
            proofState = ProofState.Idle,
            spellRotation = null,
            scenarioDamages = emptyList(),
            error = null,
            toast = null
        )
    scope.launch(Dispatchers.Default) {
        val rotation = SpellRotationOptimizer.bestSequencedRotation(build, character, character.clazz, damageScenario)
        val breakdown =
            SpellRotationOptimizer.scenarioBreakdown(
                build,
                character,
                character.clazz,
                damageScenario,
                includeBerserk = (snapshot.achieved[Characteristic.MASTERY_BERSERK] ?: 0) > 0,
                configuredRotationTotal = rotation.totalExpectedDamage
            )
        withContext(mainDispatcher) {
            if (ui.build == build && ui.mode == ScoreComputationMode.FIND_BUILD_WITH_MAX_DAMAGE) {
                ui = ui.copy(spellRotation = rotation, scenarioDamages = breakdown)
            }
        }
    }
}

private fun UiState.currentDamageScenario(): DamageScenario =
    when {
        selectedBoss != null && bossElement != null -> scenario.against(selectedBoss, bossElement)
        selectedBoss != null -> scenario.againstAllElements(selectedBoss)
        else -> scenario
    }

/** Dismisses the pre-search request-errors pop-up ([UiState.requestErrors]). */
fun BuildSearchModel.dismissRequestErrors() {
    ui = ui.copy(requestErrors = emptyList())
}

fun BuildSearchModel.openZenithBuild() {
    createZenithLink { link ->
        runCatching {
            openBrowser(link)
        }.onFailure { exception ->
            ui = ui.copy(zenith = ZenithState.Error, error = exception.message ?: "Unable to open Zenith")
        }
    }
}

fun BuildSearchModel.copyZenithLink() {
    createZenithLink { link ->
        copyToClipboard(link)
        ui =
            ui.copy(
                toast =
                    Tr.TOAST_ZENITH_COPIED.value(ui.lang)
            )
    }
}

/**
 * Copies the current build — its full request (input) *and* discovered result (output) — to the
 * clipboard as a [HistoryEntry] JSON, so a tester can hand you a build without a screenshot. The
 * same payload re-imports losslessly via [importBuild]. Reflects the live workspace; when a saved
 * build is loaded its metadata (note/tags/folder/created date) is preserved.
 */
fun BuildSearchModel.exportBuild() {
    val source = saveSource()
    if (source.build == null) return
    val active = source.activeBuildId?.let { id -> ui.savedBuilds.firstOrNull { it.id == id } }
    val isManual = ui.screen == Screen.ManualBuild
    val entry =
        source.toHistoryEntry(
            id = active?.id ?: idGenerator(),
            name = source.activeBuildName ?: source.suggestedBuildName(),
            note = active?.note ?: (if (isManual) ui.manualNote.ifBlank { null } else null),
            createdAt = active?.createdAt ?: clock(),
            dataVersion = dataVersion,
            tags = active?.tags ?: emptyList(),
            folder = active?.folder
        ) ?: return
    copyToClipboard(historyJson.encodeToString(HistoryEntry.serializer(), entry))
    ui = ui.copy(toast = Tr.TOAST_BUILD_EXPORTED.value(ui.lang))
}

internal fun BuildSearchModel.createZenithLink(onReady: (String) -> Unit) {
    createZenithLink(
        build = ui.build,
        setZenithState = { state -> ui = ui.copy(zenith = state) },
        setZenithUrl = { url -> ui = ui.copy(zenithUrl = url) },
        onReady = onReady
    )
}

private fun UiState.toTargetStats(): TargetStats {
    val raw = targets.map { TargetStat(it.characteristic, it.value.toIntOrNull() ?: 0, it.weight) }
    // Most-masteries only: split a single "all resistances" target into the four per-element ones
    // so the solver gets four graceful constraints instead of one brittle min-over-four. The UI
    // keeps a single editable row; the split happens here, on the way to the engine.
    val forEngine =
        if (mode == ScoreComputationMode.FIND_BUILD_WITH_MOST_MASTERIES_FROM_INPUT) {
            expandGlobalResistance(raw)
        } else {
            raw
        }
    return TargetStats(forEngine)
}

internal fun BuildSearchModel.newlyLandedEquipmentId(
    previous: BuildCombination?,
    next: BuildCombination,
): Int? {
    val previousIds = previous?.equipments?.map { it.equipmentId }?.toSet() ?: emptySet()
    return next.equipments.firstOrNull { it.equipmentId !in previousIds }?.equipmentId
}

internal fun BuildSearchModel.clearLandedMarkerLater(equipmentId: Int) {
    scope.launch {
        delay(560.milliseconds)
        withContext(mainDispatcher) {
            if (ui.lastLandedEquipmentId == equipmentId) {
                ui = ui.copy(lastLandedEquipmentId = null)
            }
        }
    }
}

/**
 * The search button's click handler. When a saved build is loaded the button is *locked*: the
 * first click asks for confirmation (so re-optimizing a saved build is a deliberate act) rather
 * than silently recomputing it. Otherwise it runs the search straight away.
 */
fun BuildSearchModel.onSearchPressed() {
    if (ui.searchLocked) {
        ui = ui.copy(modal = Modal.ConfirmReSearch)
    } else {
        search()
    }
}

/** Confirms the guarded re-search: unlock and run. The active build identity is kept so the user
 * can save the recomputed result back over the same entry. */
fun BuildSearchModel.confirmReSearch() {
    ui = ui.copy(modal = null, searchLocked = false)
    search()
}
