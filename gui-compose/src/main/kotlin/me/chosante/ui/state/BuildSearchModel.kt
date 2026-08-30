package me.chosante.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import me.chosante.ZenithInputParameters
import me.chosante.autobuilder.domain.BuildCombination
import me.chosante.autobuilder.domain.PassiveCatalog
import me.chosante.autobuilder.domain.TargetStat
import me.chosante.autobuilder.genetic.SolverResult
import me.chosante.autobuilder.genetic.wakfu.MaxDamageSearch
import me.chosante.autobuilder.genetic.wakfu.ScoreComputationMode
import me.chosante.autobuilder.genetic.wakfu.WakfuBestBuildFinderAlgorithm
import me.chosante.autobuilder.genetic.wakfu.WakfuBestBuildParams
import me.chosante.autobuilder.genetic.wakfu.WakfuBuildSolver
import me.chosante.common.Character
import me.chosante.common.Characteristic
import me.chosante.common.Equipment
import me.chosante.common.history.HistoryEntry
import me.chosante.createZenithBuild
import me.chosante.marketclient.ItemSearchResult
import me.chosante.marketclient.MarketRepository
import me.chosante.ui.components.BreedAssets
import me.chosante.ui.components.IconPreloader
import me.chosante.ui.components.pageCount
import me.chosante.ui.components.toCsv
import me.chosante.ui.components.warmUpPaths
import me.chosante.ui.history.HistoryRepository
import me.chosante.ui.i18n.Tr
import java.awt.Desktop
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.util.concurrent.CancellationException
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private typealias BuildFinder = (WakfuBestBuildParams) -> Flow<SolverResult<BuildCombination>>
private typealias ZenithBuilder = suspend (ZenithInputParameters) -> String
private typealias OptimalityProver = (WakfuBestBuildParams, SolverResult<BuildCombination>, () -> Boolean) -> MaxDamageSearch.MaxDamageProof

/** The four specific elemental masteries, mutually exclusive with the aggregate "all elements".
 * internal: only [BuildSearchModelSearch.kt] uses this, but a top-level `private` declaration is
 * file-scoped in Kotlin, not package-scoped -- left here rather than moved, to avoid disturbing the
 * imports above it. */
internal val ELEMENTAL_MASTERY_ELEMENTS =
    setOf(
        Characteristic.MASTERY_ELEMENTARY_WATER,
        Characteristic.MASTERY_ELEMENTARY_FIRE,
        Characteristic.MASTERY_ELEMENTARY_EARTH,
        Characteristic.MASTERY_ELEMENTARY_WIND
    )

private val ELEMENTAL_RESISTANCES =
    listOf(
        Characteristic.RESISTANCE_ELEMENTARY_WATER,
        Characteristic.RESISTANCE_ELEMENTARY_FIRE,
        Characteristic.RESISTANCE_ELEMENTARY_EARTH,
        Characteristic.RESISTANCE_ELEMENTARY_WIND
    )

/**
 * Splits a single "all resistances" target ([Characteristic.RESISTANCE_ELEMENTARY]) into the four
 * per-element resistance targets, keeping any element the user already set explicitly. The solver
 * models the aggregate as one min-over-four constraint, which the score's power-6 penalty makes
 * brittle — one short element craters the whole build; four independent per-element constraints
 * degrade gracefully (the "400 in each" form that works). Applied only when handing off to the
 * engine, so the UI keeps a single editable row.
 */
internal fun expandGlobalResistance(targets: List<TargetStat>): List<TargetStat> {
    val global = targets.firstOrNull { it.characteristic == Characteristic.RESISTANCE_ELEMENTARY } ?: return targets
    // A meaningful (non-zero) per-element resistance keeps its own value; a zero one is an inert
    // placeholder (e.g. the default wind=0) the global must override, so all four really get the value.
    val explicit = targets.filter { it.characteristic in ELEMENTAL_RESISTANCES && it.target != 0 }.map { it.characteristic }.toSet()
    val perElement =
        ELEMENTAL_RESISTANCES
            .filter { it !in explicit }
            .map { TargetStat(it, global.target, global.userDefinedWeight) }
    return targets.filterNot {
        it.characteristic == Characteristic.RESISTANCE_ELEMENTARY ||
            (it.characteristic in ELEMENTAL_RESISTANCES && it.target == 0)
    } + perElement
}

// Comfortably covers the largest catalog this app queries in one call -- the ~10,400-item Market
// catalog (equipment + resources/consumables/cosmetics/misc + sublimations, matches market-server's
// own MAX_SEARCH_LIMIT) and, reused for the Kamas screen's three scans, the 2846-monster catalog
// (server-clamped to its own smaller MAX_SCAN_LIMIT regardless, so requesting this much is always
// safe). Used by the CSV export and every *filtered* Prices-tab search, and by every Kamas scan: all
// of these screens are meant to browse the whole game's data, so a filter must return every match,
// not a slice cut off long before most of the catalog is reached.
internal const val FULL_CATALOG_LIMIT = 20_000

/** internal: only [BuildSearchModelMarket.kt]'s `exportItemsToCsv` uses this (same file-scoping reason
 * as [ELEMENTAL_MASTERY_ELEMENTS] above). */
internal fun itemsToCsv(results: List<ItemSearchResult>): String {
    val headers = listOf("item_id", "name_fr", "name_en", "level", "rarity", "category", "min_price", "avg_price", "server", "observed_at")
    val rows =
        results.map { result ->
            listOf(
                result.item.itemId.toString(),
                result.item.name.fr,
                result.item.name.en,
                result.item.level.toString(),
                result.item.rarity.name,
                result.item.category,
                result.latestMinPrice?.toString() ?: "",
                result.latestAvgPrice?.toString() ?: "",
                result.latestServer ?: "",
                result.latestObservedAt ?: ""
            )
        }
    return toCsv(headers, rows)
}

/**
 * All constructor collaborators below are `internal`, not `private`: [BuildSearchModel]'s body is
 * split by feature across sibling `BuildSearchModelSearch.kt`/`Manual.kt`/`Market.kt`/`Kamas.kt`/
 * `Library.kt` files (extension functions on this class, `package me.chosante.ui.state` — see this
 * file's own functions for what's left after the split), and Kotlin has no "file-private-but-
 * package-visible" level between `private` and `internal` — so anything those files need to read
 * must be at least `internal` (module-wide, but still invisible outside `gui-compose`).
 */
class BuildSearchModel(
    internal val scope: CoroutineScope,
    internal val buildFinder: BuildFinder = { WakfuBestBuildFinderAlgorithm.run(it) },
    // Post-search certificate optimality proof (P4.4). Injectable so tests drive proofState deterministically
    // without a real (minutes-long) exact solve.
    internal val optimalityProver: OptimalityProver = { params, result, isCancelled -> WakfuBestBuildFinderAlgorithm.proveMaxDamageOptimality(params, result, isCancelled) },
    internal val zenithBuilder: ZenithBuilder = { it.createZenithBuild() },
    internal val openBrowser: (String) -> Unit = { link -> Desktop.getDesktop().browse(URI(link)) },
    internal val copyToClipboard: (String) -> Unit = { link -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null) },
    internal val readClipboard: () -> String = {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull().orEmpty()
    },
    internal val mainDispatcher: CoroutineDispatcher = Dispatchers.Swing,
    internal val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal val historyRepository: HistoryRepository = HistoryRepository(),
    internal val marketRepository: MarketRepository = MarketRepository(),
    /** Persisted library view options (sort + group-by-class). Injectable for tests. */
    internal val libraryPreferences: LibraryPreferences = LibraryPreferences(),
    /** Wakfu game-data version stamped onto saved builds (injectable for tests). */
    internal val dataVersion: String = WakfuBestBuildFinderAlgorithm.dataVersion,
    internal val idGenerator: () -> String = {
        java.util.UUID
            .randomUUID()
            .toString()
    },
    internal val clock: () -> Long = { System.currentTimeMillis() },
) {
    var ui by androidx.compose.runtime.mutableStateOf(UiState())
        internal set

    /**
     * `true` once the app is ready to show its main UI: OR-Tools' one-time cold start has been paid
     * (or we're in screenshot mode). Until then a [me.chosante.ui.shell.LoadingScreen] is shown
     * instead of the heavy main UI — mounting that UI *during* native-library loading is what made
     * the window appear to hang.
     */
    var isReady by androidx.compose.runtime.mutableStateOf(false)
        private set

    /**
     * Completed by the UI once the window is actually on screen. Gates the native warm-up: on its
     * very first launch macOS spends seconds validating the freshly extracted OR-Tools dylibs and
     * stalls the UI thread for the whole load (see `OrToolsNativeLoader`), so the load must never
     * start before the loading screen has had its first frame. Awaited with a timeout so headless
     * usage (tests) can never hang on it.
     */
    val windowShown: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred()

    // The real app window, handed over once it exists (unlike windowShown above, a CSV export's
    // Save dialog only needs the reference itself, not a "has it appeared yet" signal -- it's only
    // ever used from a button click, long after the window is showing). internal: read by the Market
    // domain's CSV export (BuildSearchModelMarket.kt).
    internal var ownerWindow: Frame? = null

    fun attachWindow(window: Frame) {
        ownerWindow = window
    }

    /** Estimated warm-up progress (0..1) for the loading screen. See [WarmupTiming]. */
    var warmupProgress by androidx.compose.runtime.mutableStateOf(0f)
        private set

    /** Estimated seconds left on the warm-up, or `null` once the estimate is exhausted/done. */
    var warmupEtaSeconds by androidx.compose.runtime.mutableStateOf<Int?>(null)
        private set

    // internal: also cancelled from the Library domain's loadBuild/newBuild (BuildSearchModelLibrary.kt).
    internal var job: Job? = null

    // The post-search optimality proof runs independently of [job] (it can take minutes after the search
    // already finished), so it has its own handle — cancelled when a new search starts. internal: also
    // cancelled from the Library domain (BuildSearchModelLibrary.kt).
    internal var proofJob: Job? = null

    // Polls market-server's capture status while a capture is running (started by startCapture()/
    // stopCapture(), restarted by refreshCaptureStatus() when the Market screen is (re)opened).
    // internal: owned by the Market domain (BuildSearchModelMarket.kt).
    internal var captureJob: Job? = null

    // Debounces the HDV-style item search (name/level/rarity edits) so typing doesn't fire one
    // request per keystroke -- cancel-and-replace, same shape as captureJob. internal: owned by the
    // Market domain (BuildSearchModelMarket.kt).
    internal var marketSearchJob: Job? = null

    // B8: cancelling [proofJob] only stops the coroutine, not the blocking certifier DP running inside it (which
    // can hold a core for minutes). This flag — set at every proof-cancel site, polled once per certifier DP
    // stage — makes the DP bail promptly. AtomicBoolean because the parallel exact tier polls it off pool threads.
    // internal: also read from the Library domain's loadBuild/newBuild (BuildSearchModelLibrary.kt).
    internal val proofCancelled =
        java.util.concurrent.atomic
            .AtomicBoolean(false)

    /** Persisted tag registry (display casing). Tags here survive having no build, until deleted.
     * internal: read/written by the Library domain's tag functions (BuildSearchModelLibrary.kt). */
    internal var tagRegistry: List<String> = emptyList()

    /** Union of the registry and tags currently on [builds], de-duped case-insensitively, A–Z.
     * internal: called from the Library domain (BuildSearchModelLibrary.kt) after every save/load/tag edit. */
    internal fun computeKnownTags(builds: List<me.chosante.common.history.HistoryEntry>): List<String> =
        (tagRegistry + builds.flatMap { it.tags })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    /** Mirrors [me.chosante.ui.SCREENSHOT_PATH_PROPERTY] / `WAKFU_COMPOSE_SCREENSHOT` (see Main.kt). */
    private val isScreenshotMode =
        System.getProperty("wakfu.compose.screenshot") != null ||
            System.getenv("WAKFU_COMPOSE_SCREENSHOT") != null

    /** Screenshot-only: pin the 1st build item as required and exclude the 2nd, to capture the #125 badges.
     * internal: also read from the Search domain's search() (BuildSearchModelSearch.kt). */
    internal val screenshotForceFirst =
        System.getProperty("wakfu.compose.screenshot.forceFirst") != null ||
            System.getenv("WAKFU_COMPOSE_SCREENSHOT_FORCE_FIRST") != null

    /** Screenshot-only: start in precision mode so a capture can show that screen's target editor (#123). */
    private val screenshotPrecisionMode =
        System.getProperty("wakfu.compose.screenshot.precision") != null ||
            System.getenv("WAKFU_COMPOSE_SCREENSHOT_PRECISION") != null

    /** Screenshot-only: spread ascending 1..5 priorities across targets so a capture shows the priority bars at different levels (#123). */
    private val screenshotVaryPriority =
        System.getProperty("wakfu.compose.screenshot.varyPriority") != null ||
            System.getenv("WAKFU_COMPOSE_SCREENSHOT_VARY_PRIORITY") != null

    /** Screenshot-only: land on the Market screen (Prices tab, loaded) instead of running a search. */
    private val screenshotMarketScreen =
        System.getProperty("wakfu.compose.screenshot.market") != null ||
            System.getenv("WAKFU_COMPOSE_SCREENSHOT_MARKET") != null

    /** Screenshot-only: land on the Kamas screen instead of running a search. */
    private val screenshotKamasScreen =
        System.getProperty("wakfu.compose.screenshot.kamas") != null ||
            System.getenv("WAKFU_COMPOSE_SCREENSHOT_KAMAS") != null

    /** Screenshot-only: which Kamas tab to land on ("harvesting"/"monster_farming"; default crafting). */
    private val screenshotKamasTab =
        (System.getProperty("wakfu.compose.screenshot.kamas.tab") ?: System.getenv("WAKFU_COMPOSE_SCREENSHOT_KAMAS_TAB"))
            ?.lowercase()

    init {
        // Seed the persisted UI options (language + library view) + tag registry before any UI reads them.
        tagRegistry = libraryPreferences.loadTags()
        ui =
            ui.copy(
                lang = libraryPreferences.loadLang(),
                librarySort = libraryPreferences.loadSort(),
                libraryGroupByClass = libraryPreferences.loadGroupByClass()
            )

        // Load the saved-build library off the UI thread. A read failure must never block startup —
        // it just yields an empty library that fills in as the user saves builds.
        scope.launch(ioDispatcher) {
            val all = runCatching { historyRepository.loadAll() }.getOrDefault(emptyList())
            withContext(mainDispatcher) { ui = ui.copy(savedBuilds = all, knownTags = computeKnownTags(all)) }
        }

        if (isScreenshotMode) {
            // Screenshots want the real UI immediately, with no warm-up gating. Kick off a search
            // with the default request so the captured frame shows a populated build (paperdoll,
            // stats, skill tree) instead of an empty shell. The first solve pays OR-Tools' cold
            // start inline; ScreenshotCapture waits for the build before grabbing pixels.
            startIconPreload()
            isReady = true
            if (screenshotPrecisionMode) setMode(ScoreComputationMode.FIND_CLOSEST_BUILD_FROM_INPUT)
            if (screenshotVaryPriority) {
                // Vary the constraint priority bars across levels so a capture shows the gradient.
                ui = ui.copy(targets = ui.targets.mapIndexed { index, target -> target.copy(weight = (index % 5) + 1) })
            }
            screenshotExcludedRarities()?.let { ui = ui.copy(excludedRarities = it) }
            if (screenshotMarketScreen) {
                ui = ui.copy(screen = Screen.Market)
                searchMarketItems()
            } else if (screenshotKamasScreen) {
                val tab =
                    when (screenshotKamasTab) {
                        "harvesting" -> KamasTab.HARVESTING
                        "monster_farming" -> KamasTab.MONSTER_FARMING
                        else -> KamasTab.CRAFTING
                    }
                ui = ui.copy(screen = Screen.Kamas, kamasTab = tab)
                ensureKamasTabLoaded(tab)
            } else {
                search()
            }
        } else {
            // Pay OR-Tools' one-time cold start behind the loading screen, so the first real search
            // starts warm and the heavy main UI only mounts once the native library is loaded (no
            // CPU/IO contention with Compose's first render). The short delay lets the loader paint.
            scope.launch(Dispatchers.Default) {
                val estimateMs = WarmupTiming.estimatedDurationMs()
                val start = System.currentTimeMillis()
                // The native load reports no real progress, so animate an estimated %/ETA from the
                // elapsed time vs. the last measured duration. The bar caps below 100% until warm-up
                // actually finishes, then snaps full — never claims "done" early.
                val ticker =
                    launch {
                        while (isActive) {
                            val elapsed = System.currentTimeMillis() - start
                            val remainingMs = estimateMs - elapsed
                            withContext(mainDispatcher) {
                                warmupProgress = (elapsed.toFloat() / estimateMs).coerceIn(0f, 0.92f)
                                warmupEtaSeconds = if (remainingMs > 0) ceil(remainingMs / 1000.0).toInt() else null
                            }
                            delay(80.milliseconds)
                        }
                    }
                // Wait for the loading screen's first frame before touching the native engine: the
                // load can stall the UI thread (macOS first-launch code-sign validation), and a
                // stall behind a painted window is invisible while one before it looks like the app
                // failed to start. Generous bound: on a cold first packaged launch the window itself
                // can take seconds to appear (Skiko's freshly extracted dylib pays the same macOS
                // validation), and guessing low here would start the native load before the first
                // frame — the exact failure this gate prevents. Nothing user-visible ever waits on
                // the timeout (tests cancel their scope; the GUI completes the gate within ~200ms),
                // it only exists so a headless run can never hang.
                kotlinx.coroutines.withTimeoutOrNull(15.seconds) { windowShown.await() }
                delay(100.milliseconds)
                try {
                    WakfuBuildSolver.warmUp()
                    WarmupTiming.record(System.currentTimeMillis() - start)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    // Swallow (Throwable: native loading raises Errors, not Exceptions): a warm-up
                    // failure should degrade to a cold first search, never crash the app.
                    throwable.printStackTrace()
                } finally {
                    // Always reveal the UI: a warm-up failure must never leave the app stuck on the
                    // loading screen.
                    ticker.cancel()
                    withContext(mainDispatcher) {
                        warmupProgress = 1f
                        warmupEtaSeconds = null
                        isReady = true
                    }
                }
                // Only start decoding item icons once the engine is warm. During warm-up every core
                // counts: running the preloader (thousands of PNG decodes + the equipments JSON parse
                // it triggers) concurrently with the native cold start starved the AWT event thread,
                // which on macOS froze any window operation until warm-up finished. Icons are not
                // needed before the first build is shown, so starting late costs nothing visible.
                startIconPreload()
            }
        }
    }

    /**
     * Decodes item icons into the cache off the UI thread so they're ready (and decoded once) by the
     * time a build is shown. Purely background work: it never gates startup — items simply appear as
     * they decode, so we don't make the user wait on ~thousands of PNGs. First touch of
     * [WakfuBestBuildFinderAlgorithm.equipments] also pays its (lazy) JSON parse, here on a
     * background thread — never on the UI thread.
     */
    private fun startIconPreload() {
        scope.launch(Dispatchers.Default) {
            val paths = warmUpPaths(WakfuBestBuildFinderAlgorithm.equipments) + BreedAssets.warmUpPaths()
            IconPreloader.warmUp(scope, paths) { _, _ -> }
        }
    }

    fun setLang(lang: me.chosante.ui.i18n.Lang) {
        ui = ui.copy(lang = lang)
        libraryPreferences.saveLang(lang)
    }

    fun setClass(clazz: me.chosante.common.CharacterClass) {
        // Passives are class-specific and slot-capped — drop any that don't exist for the new class so a
        // stale chip can't linger, eat a slot, and be silently dropped at solve time.
        ui = ui.copy(clazz = clazz, forcedPassives = reconcilePassives(ui.forcedPassives, clazz, ui.level))
    }

    fun setLevel(level: String) {
        val parsed =
            level
                .onlyDigits()
                .take(3)
                .toIntOrNull()
                ?.coerceIn(1, 245) ?: return
        // Lowering the level shrinks the passive-slot budget — trim the loadout so the shown chips match
        // exactly what the engine folds (resolvedPassives caps with the same slot count).
        ui = ui.copy(level = parsed, forcedPassives = reconcilePassives(ui.forcedPassives, ui.clazz, parsed))
        reconcileForcedItemsForCurrentRequest()
    }

    /** Keep only passives that belong to [clazz], capped to [level]'s passive slots (preserving order). */
    private fun reconcilePassives(
        forced: List<String>,
        clazz: me.chosante.common.CharacterClass,
        level: Int,
    ): List<String> =
        forced
            .filter { PassiveCatalog.findByName(clazz, it) != null }
            .take(PassiveCatalog.slotsForLevel(level))

    fun setMinLevel(minLevel: String) {
        val parsed =
            minLevel
                .onlyDigits()
                .take(3)
                .toIntOrNull()
                ?.coerceIn(0, 245) ?: return
        ui = ui.copy(minLevel = parsed)
        reconcileForcedItemsForCurrentRequest()
    }

    fun openModal(modal: Modal) {
        ui = ui.copy(modal = modal)
        if (modal is Modal.ItemPicker) {
            ensureCatalogLoaded()
        }
    }

    fun closeModal() {
        ui = ui.copy(modal = null)
    }

    fun pickItem(equipment: me.chosante.common.Equipment) {
        val chip = equipment.toChip()
        when ((ui.modal as? Modal.ItemPicker)?.mode) {
            PickerMode.Forced -> pinForced(chip)
            PickerMode.Excluded -> pinExcluded(chip)
            null -> {}
        }
    }

    /**
     * Full equipment list from the embedded Wakfu data. `null` while the (heavy) JSON resource is
     * still being parsed off the UI thread, so the picker can show a loading state instead of
     * freezing on first open.
     */
    var equipmentCatalog by androidx.compose.runtime.mutableStateOf<List<me.chosante.common.Equipment>?>(null)
        private set

    private var catalogJob: Job? = null

    /** internal: also called from the Manual domain's item picker (BuildSearchModelManual.kt). */
    internal fun ensureCatalogLoaded() {
        if (equipmentCatalog != null || catalogJob != null) {
            return
        }
        catalogJob =
            scope.launch(Dispatchers.Default) {
                val loaded =
                    WakfuBestBuildFinderAlgorithm.equipments
                        .distinctBy { it.equipmentId }
                        .sortedWith(compareByDescending<me.chosante.common.Equipment> { it.level }.thenBy { it.name.fr })
                withContext(mainDispatcher) {
                    equipmentCatalog = loaded
                }
            }
    }

    /**
     * Shared core behind [openZenithBuild]/[copyZenithLink] (auto-Builder, `BuildSearchModelSearch.kt`)
     * and [openManualZenithBuild]/[copyManualZenithLink] (manual-construction screen,
     * `BuildSearchModelManual.kt`) — `internal` so both files can call it. Deliberately does NOT swap
     * `ui.build` for `ui.manualBuild` around a single implementation — that would race against the
     * async Zenith call (whichever screen isn't active when the response lands could get corrupted).
     * Instead each caller supplies its own [setZenithState]/[setZenithUrl] targets so the two screens'
     * Zenith state stay fully independent.
     */
    internal fun createZenithLink(
        build: BuildCombination?,
        setZenithState: (ZenithState) -> Unit,
        setZenithUrl: (String?) -> Unit,
        onReady: (String) -> Unit,
    ) {
        if (build == null) return
        setZenithState(ZenithState.Loading)
        ui = ui.copy(error = null, toast = null)
        val character = Character(ui.clazz, ui.level, ui.minLevel).copy(characterSkills = build.characterSkills)
        scope.launch(Dispatchers.Default) {
            try {
                val link =
                    zenithBuilder(
                        ZenithInputParameters(
                            character = character,
                            equipments = build.equipments,
                            runes = build.runes,
                            sublimations = build.sublimations
                        )
                    )
                withContext(mainDispatcher) {
                    setZenithState(ZenithState.Ready)
                    setZenithUrl(link)
                    ui = ui.copy(toast = Tr.TOAST_ZENITH_READY.value(ui.lang))
                    onReady(link)
                }
            } catch (exception: Exception) {
                withContext(mainDispatcher) {
                    setZenithState(ZenithState.Error)
                    ui = ui.copy(error = exception.message ?: "Zenith build failed")
                }
            }
        }
    }

    /** internal: shared pagination clamp used by the Market and Kamas domains (their own files). */
    internal fun clampedPage(
        page: Int,
        totalResults: Int,
    ): Int = page.coerceIn(0, (pageCount(totalResults) - 1).coerceAtLeast(0))

    fun goToScreen(screen: Screen) {
        ui = ui.copy(screen = screen)
        // Capture keeps running server-side independent of the GUI, so re-entering the Market
        // screen (after navigating away, or after an app restart) must pick up the true state --
        // not assume it's still whatever it was when we last left.
        if (screen == Screen.Market) {
            refreshCaptureStatus()
            if (ui.marketSearchResults.isEmpty() && ui.marketSearchState == MarketState.Idle) {
                searchMarketItems()
            }
        }
        if (screen == Screen.Kamas) {
            ensureKamasTabLoaded(ui.kamasTab)
        }
        if (screen == Screen.ManualBuild) {
            ensureCatalogLoaded()
            if (ui.manualBuild == null) resetManualBuild()
        }
    }

    /** Switch the result region between the discovered build and the class's spells & passives. */
    fun setBuilderTab(tab: BuilderTab) {
        ui = ui.copy(builderTab = tab)
    }
}
