package me.chosante.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.chosante.autobuilder.domain.SpellRotationOptimizer
import me.chosante.autobuilder.genetic.wakfu.ScoreComputationMode
import me.chosante.common.Character
import me.chosante.common.Characteristic
import me.chosante.common.history.HistoryEntry
import me.chosante.ui.history.historyJson
import me.chosante.ui.history.normalizeTags
import me.chosante.ui.history.restoredClass
import me.chosante.ui.history.restoredMode
import me.chosante.ui.history.restoredScenario
import me.chosante.ui.history.suggestedBuildName
import me.chosante.ui.history.toBuildCombination
import me.chosante.ui.history.toExcludedChips
import me.chosante.ui.history.toForcedChips
import me.chosante.ui.history.toHistoryEntry
import me.chosante.ui.history.toTargetRows
import me.chosante.ui.i18n.Tr
import kotlin.time.Duration.Companion.milliseconds

// [BuildSearchModel]'s Library/Compare functions ([Screen.Library]/[Screen.Compare]: save/load/
// import/export, edit/duplicate, folders, tags, and build comparison) -- split out of
// BuildSearchModel.kt, see BuildSearchModelSearch.kt's header. The largest of the split files,
// matching how much of the original class this domain already accounted for.
// --- Library organize (search / sort / filter / group) ---

fun BuildSearchModel.setLibrarySearch(query: String) {
    ui = ui.copy(librarySearch = query)
}

fun BuildSearchModel.setLibrarySort(sort: LibrarySort) {
    ui = ui.copy(librarySort = sort)
    libraryPreferences.saveSort(sort)
}

/** Single-select class filter; passing the already-selected class (or null) clears it. */
fun BuildSearchModel.setLibraryClassFilter(clazz: me.chosante.common.CharacterClass?) {
    ui = ui.copy(libraryClassFilter = clazz)
}

/** Toggles a tag in the active filter (OR semantics: builds matching any selected tag are shown). */
fun BuildSearchModel.toggleLibraryTag(tag: String) {
    val key = tag.lowercase()
    ui =
        ui.copy(
            librarySelectedTags = if (key in ui.librarySelectedTags) ui.librarySelectedTags - key else ui.librarySelectedTags + key
        )
}

fun BuildSearchModel.toggleLibraryGroupByClass() {
    val next = !ui.libraryGroupByClass
    ui = ui.copy(libraryGroupByClass = next)
    libraryPreferences.saveGroupByClass(next)
}

/** Resets the in-memory library filters (search + class + tags + folder). Sort/group are durable. */
fun BuildSearchModel.clearLibraryFilters() {
    ui =
        ui.copy(
            librarySearch = "",
            libraryClassFilter = null,
            librarySelectedTags = emptySet(),
            libraryFolder = LibraryFolderFilter.All
        )
}

/** [Screen.ManualBuild]'s own build, projected through [asManualView] so save/name logic below can
 * read/write it via the exact same [UiState] extension functions the auto-Builder uses. */
internal fun BuildSearchModel.saveSource(): UiState = if (ui.screen == Screen.ManualBuild) ui.asManualView() else ui

/** Opens the save dialog, pre-filling the name (existing name when editing a loaded build). */
fun BuildSearchModel.requestSaveBuild() {
    if (saveSource().build == null) return
    ui = ui.copy(modal = Modal.SaveBuild)
}

/** Default text for the save dialog's name field. */
fun BuildSearchModel.suggestedSaveName(): String = saveSource().let { it.activeBuildName ?: it.suggestedBuildName() }

/**
 * Names already used by *other* saved builds (the active build's own name is excluded so updating
 * it isn't blocked). The save dialog rejects these so two builds never share a name — which would
 * make the library and the compare view ambiguous.
 */
fun BuildSearchModel.takenBuildNames(): Set<String> =
    ui.savedBuilds
        .filter { it.id != saveSource().activeBuildId }
        .map { it.name.trim().lowercase() }
        .toSet()

/**
 * Persists the current workspace build — the auto-Builder's or [Screen.ManualBuild]'s, whichever
 * is active (via [saveSource]). When [asNew] is false and a build is already loaded, it overwrites
 * that entry (same id); otherwise it creates a new entry. Local write is the source of truth and is
 * done off the UI thread; the in-memory library is refreshed afterwards.
 */
fun BuildSearchModel.saveBuild(
    name: String,
    note: String?,
    asNew: Boolean,
) {
    val isManual = ui.screen == Screen.ManualBuild
    val source = saveSource()
    val trimmedName = name.trim().ifBlank { source.suggestedBuildName() }
    val overwrite = !asNew && source.activeBuildId != null
    val id = if (overwrite) source.activeBuildId!! else idGenerator()
    // Overwriting rebuilds the entry from the workspace, which doesn't carry user metadata (tags,
    // folder) — re-read them from the existing entry so an "Update build" never silently wipes them.
    val existing = if (overwrite) ui.savedBuilds.firstOrNull { it.id == id } else null
    // On the manual screen, an empty save-dialog note falls back to the Note tab's own text — the
    // dialog's own input still wins when the user typed something there.
    val effectiveNote = note?.ifBlank { null } ?: (if (isManual) ui.manualNote.ifBlank { null } else null)
    val entry =
        source.toHistoryEntry(
            id = id,
            name = trimmedName,
            note = effectiveNote,
            createdAt = clock(),
            dataVersion = dataVersion,
            tags = existing?.tags ?: emptyList(),
            folder = existing?.folder
        ) ?: return
    // Note: saving does NOT lock search. The lock guards *revisiting* a build loaded from the
    // library (a deliberate act); right after saving you should stay free to keep iterating.
    ui =
        if (isManual) {
            ui.copy(modal = null, manualActiveBuildId = id, manualActiveBuildName = trimmedName)
        } else {
            ui.copy(modal = null, activeBuildId = id, activeBuildName = trimmedName)
        }
    scope.launch(ioDispatcher) {
        runCatching { historyRepository.save(entry) }
            .onSuccess {
                val all = historyRepository.loadAll()
                withContext(mainDispatcher) { ui = ui.copy(savedBuilds = all, knownTags = computeKnownTags(all), toast = Tr.TOAST_BUILD_SAVED.value(ui.lang)) }
            }.onFailure { throwable ->
                withContext(mainDispatcher) { ui = ui.copy(error = throwable.message ?: "Could not save build") }
            }
    }
}

/**
 * Loads a saved build into the workspace: restores its request (so it can be tweaked & re-run)
 * and its result (shown without re-running), marks it as the active build, and locks the search
 * button. Returns to the Builder screen.
 */
fun BuildSearchModel.loadBuild(id: String) {
    val entry = ui.savedBuilds.firstOrNull { it.id == id } ?: return
    job?.cancel()
    // Cancel any in-flight optimality proof: it is proving the PREVIOUS build, and the loaded build has no
    // certificate (its stored CP-SAT `optimal` flag is restored below). Without this, a running proof could
    // leave "Proving optimality…" stuck, or a prior ProvenOptimal could paint a green badge on this build
    // that the certificate never saw (proofState is reset to Idle in the copy below).
    proofCancelled.set(true) // B8: stop the certifier DP inside the job, not just the coroutine
    proofJob?.cancel()
    val loadedBuild = entry.toBuildCombination()
    // Recompute the spell rotation for a loaded max-damage build (else the Rotation card would show a
    // rotation left over from a prior search, or nothing). Cheap — no solver, just one rotation DP.
    val isMaxDamage = entry.restoredMode() == ScoreComputationMode.FIND_BUILD_WITH_MAX_DAMAGE
    val restoredCharacter =
        me.chosante.common.Character(entry.restoredClass(), entry.request.level, entry.request.minLevel, loadedBuild.characterSkills)
    val rotation =
        if (isMaxDamage) {
            SpellRotationOptimizer.bestSequencedRotation(loadedBuild, restoredCharacter, restoredCharacter.clazz, entry.restoredScenario())
        } else {
            null
        }
    ui =
        ui.copy(
            screen = Screen.Builder,
            modal = null,
            clazz = entry.restoredClass(),
            level = entry.request.level,
            minLevel = entry.request.minLevel,
            mode = entry.restoredMode(),
            scenario = entry.restoredScenario(),
            maxRarity = entry.request.maxRarity,
            duration = entry.request.duration,
            stopAtMatch = entry.request.stopAtMatch,
            targets = entry.toTargetRows(),
            forcedItems = entry.toForcedChips(),
            excludedItems = entry.toExcludedChips(),
            useSublimations = entry.request.useSublimations,
            maxSublimationTier = entry.request.maxSublimationTier,
            forcedSublimations = entry.request.forcedSublimations,
            excludedSublimations = entry.request.excludedSublimations,
            excludedRarities = entry.request.excludedRarities,
            forcedPassives = entry.request.forcedPassives,
            forcedRunesByItem = entry.request.forcedRunesByItem,
            phase = Phase.Done,
            progress = 100,
            match = entry.result.match.toBigDecimal(),
            optimal = entry.result.optimal,
            // A loaded build is not re-proven by the certificate (only its stored CP-SAT `optimal` flag is
            // restored above) — reset the proof state so a prior search's verdict can't leak onto it.
            proofState = ProofState.Idle,
            build = loadedBuild,
            spellRotation = rotation,
            scenarioDamages = emptyList(),
            achieved = entry.result.achieved,
            lastLandedEquipmentId = null,
            zenith = if (entry.zenithUrl != null) ZenithState.Ready else ZenithState.Idle,
            zenithUrl = entry.zenithUrl,
            error = null,
            toast = null,
            activeBuildId = entry.id,
            activeBuildName = entry.name,
            searchLocked = true
        )
    // The per-position breakdown runs 3-4 more rotations, so compute it OFF the UI thread (the rotation
    // card already renders from `rotation` above) and patch it in when ready — only if this build is still
    // the active one, so a quick load-another-build doesn't get a stale breakdown.
    if (isMaxDamage && rotation != null) {
        scope.launch(Dispatchers.Default) {
            val breakdown =
                SpellRotationOptimizer.scenarioBreakdown(
                    loadedBuild,
                    restoredCharacter,
                    restoredCharacter.clazz,
                    entry.restoredScenario(),
                    includeBerserk = (entry.result.achieved[Characteristic.MASTERY_BERSERK] ?: 0) > 0,
                    configuredRotationTotal = rotation.totalExpectedDamage
                )
            withContext(mainDispatcher) {
                if (ui.activeBuildId == entry.id) ui = ui.copy(scenarioDamages = breakdown)
            }
        }
    }
}

/** Opens the import dialog, where a build exported via [exportBuild] is pasted. See [importBuild]. */
fun BuildSearchModel.requestImport() {
    ui = ui.copy(modal = Modal.ImportBuild)
}

/** Best-effort read of the system clipboard for the import dialog's "Paste" button. */
fun BuildSearchModel.clipboardText(): String = readClipboard()

/** True when [rawJson] decodes to a valid exported build — gates the import dialog's confirm button. */
fun BuildSearchModel.canParseImport(rawJson: String): Boolean =
    rawJson.isNotBlank() && runCatching { historyJson.decodeFromString(HistoryEntry.serializer(), rawJson.trim()) }.isSuccess

/**
 * Imports a build exported via [exportBuild]: parses the pasted [HistoryEntry] JSON, saves it as a
 * fresh library entry (new id + a name made unique, so it never overwrites an existing build), then
 * loads it into the workspace so it's visible at once. The denormalized result lets it display even
 * if its items left the catalog or the data version differs. Invalid JSON is a no-op (toast only).
 */
fun BuildSearchModel.importBuild(rawJson: String) {
    val parsed = runCatching { historyJson.decodeFromString(HistoryEntry.serializer(), rawJson.trim()) }.getOrNull()
    if (parsed == null) {
        ui = ui.copy(modal = null, toast = Tr.IMPORT_INVALID.value(ui.lang))
        return
    }
    val entry = parsed.copy(id = idGenerator(), name = uniqueLibraryName(parsed.name), createdAt = clock())
    ui = ui.copy(modal = null)
    scope.launch(ioDispatcher) {
        runCatching { historyRepository.save(entry) }
            .onSuccess {
                val all = historyRepository.loadAll()
                withContext(mainDispatcher) {
                    ui = ui.copy(savedBuilds = all, knownTags = computeKnownTags(all))
                    loadBuild(entry.id)
                    ui = ui.copy(toast = Tr.TOAST_BUILD_IMPORTED.value(ui.lang))
                }
            }.onFailure { throwable ->
                withContext(mainDispatcher) { ui = ui.copy(error = throwable.message ?: "Could not import build") }
            }
    }
}

/** A library name unique against existing builds: keeps [base] if free, else appends " (2)", " (3)"… */
internal fun BuildSearchModel.uniqueLibraryName(base: String): String {
    val trimmed = base.trim().ifBlank { Tr.IMPORTED_BUILD_NAME.value(ui.lang) }
    val taken = ui.savedBuilds.map { it.name.trim().lowercase() }.toSet()
    if (trimmed.lowercase() !in taken) return trimmed
    var n = 2
    while ("$trimmed ($n)".lowercase() in taken) n++
    return "$trimmed ($n)"
}

/** Clears the active-build identity (the workspace becomes an "unsaved build" again, unlocked). */
fun BuildSearchModel.clearActiveBuild() {
    ui = ui.copy(activeBuildId = null, activeBuildName = null, searchLocked = false)
}

/**
 * Starts a fresh, blank build: resets the whole workspace to defaults (request + result), drops
 * any active-build link, and unlocks search. Keeps the language and the saved-build library.
 * This is the explicit "New build" escape from editing a loaded build.
 */
fun BuildSearchModel.newBuild() {
    job?.cancel()
    ui = UiState(lang = ui.lang, savedBuilds = ui.savedBuilds, screen = Screen.Builder)
}

/** Opens the Edit-build dialog (name + note + tags + folder). The dialog resolves the entry by id. */
fun BuildSearchModel.requestEdit(id: String) {
    ui = ui.copy(modal = Modal.EditBuild(id))
}

/**
 * Saves edited metadata for a saved build (name, note, tags, folder). This is also how a build
 * moves between folders and how a new folder is created (a folder exists iff a build references
 * it). Keeps names unique and updates the active-build name.
 */
fun BuildSearchModel.editBuild(
    id: String,
    newName: String,
    note: String?,
    tags: List<String>,
    folder: String?,
) {
    val trimmed = newName.trim()
    val entry = ui.savedBuilds.firstOrNull { it.id == id }
    if (trimmed.isBlank() || entry == null) {
        ui = ui.copy(modal = null)
        return
    }
    // Reject an edit that would collide with a *different* build's name, keeping names unique.
    val collides = ui.savedBuilds.any { it.id != id && it.name.trim().equals(trimmed, ignoreCase = true) }
    if (collides) {
        ui = ui.copy(modal = null, toast = Tr.SAVE_NAME_TAKEN.value(ui.lang))
        return
    }
    val normalizedTags = normalizeTags(tags)
    val edited =
        entry.copy(
            name = trimmed,
            note = note?.takeIf { it.isNotBlank() },
            tags = normalizedTags,
            folder = canonicalFolder(folder)
        )
    // Assigning a tag also registers it (so it persists even once removed from every build).
    registerTags(normalizedTags)
    ui = ui.copy(modal = null, activeBuildName = if (ui.activeBuildId == id) trimmed else ui.activeBuildName)
    scope.launch(ioDispatcher) {
        runCatching { historyRepository.save(edited) }
        val all = historyRepository.loadAll()
        withContext(mainDispatcher) {
            ui =
                ui.copy(
                    savedBuilds = all,
                    knownTags = computeKnownTags(all),
                    libraryFolder = ui.libraryFolder.coercedTo(all),
                    librarySelectedTags = ui.librarySelectedTags.coercedToTags(all, tagRegistry)
                )
        }
    }
}

/**
 * Duplicates a saved build (#141): persists a brand-new library entry carrying the same request +
 * result but a fresh id and a unique "(copy)" name, leaving the original untouched. This lets the
 * user tweak the copy and compare it against the source without overwriting it. Stays on the
 * current screen; the copy lands at the top of the library (newest first). Written off the UI
 * thread, like every other history write.
 */
fun BuildSearchModel.duplicateBuild(id: String) {
    val source = ui.savedBuilds.firstOrNull { it.id == id } ?: return
    val copy = source.copy(id = idGenerator(), name = uniqueCopyName(source.name), createdAt = clock())
    scope.launch(ioDispatcher) {
        runCatching { historyRepository.save(copy) }
            .onSuccess {
                val all = historyRepository.loadAll()
                withContext(mainDispatcher) {
                    ui =
                        ui.copy(
                            savedBuilds = all,
                            lastDuplicatedBuildId = copy.id,
                            toast = Tr.TOAST_BUILD_DUPLICATED.value(ui.lang)
                        )
                    clearDuplicatedMarkerLater(copy.id)
                }
            }.onFailure { throwable ->
                withContext(mainDispatcher) { ui = ui.copy(error = throwable.message ?: "Could not duplicate build") }
            }
    }
}

/** Drops the just-duplicated highlight after a beat, so the cue fades on its own. */
internal fun BuildSearchModel.clearDuplicatedMarkerLater(id: String) {
    scope.launch {
        delay(2200.milliseconds)
        withContext(mainDispatcher) {
            if (ui.lastDuplicatedBuildId == id) {
                ui = ui.copy(lastDuplicatedBuildId = null)
            }
        }
    }
}

/**
 * A unique "<name> (copy)" — falling back to "(copy 2)", "(copy 3)", … when needed — so a
 * duplicate never collides with an existing build name. Names are kept unique so the library and
 * compare view stay unambiguous, mirroring the [editBuild]/[saveBuild] guards.
 */
internal fun BuildSearchModel.uniqueCopyName(baseName: String): String {
    val suffix = Tr.DUPLICATE_SUFFIX.value(ui.lang)
    val base = baseName.trim()
    val taken = ui.savedBuilds.map { it.name.trim().lowercase() }.toSet()
    val first = "$base ($suffix)"
    if (first.lowercase() !in taken) return first
    var n = 2
    while ("$base ($suffix $n)".lowercase() in taken) n++
    return "$base ($suffix $n)"
}

/** Adds [tags] to the persisted registry (case-insensitively de-duped) and saves it. */
internal fun BuildSearchModel.registerTags(tags: List<String>) {
    val merged = (tagRegistry + tags).map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
    if (merged.size != tagRegistry.size) {
        tagRegistry = merged
        libraryPreferences.saveTags(merged)
    }
}

// --- Folders (implicit: a folder exists iff ≥1 build references it) ---

fun BuildSearchModel.setLibraryFolderFilter(filter: LibraryFolderFilter) {
    ui = ui.copy(libraryFolder = filter)
}

fun BuildSearchModel.requestRenameFolder(name: String) {
    ui = ui.copy(modal = Modal.RenameFolder(name))
}

fun BuildSearchModel.requestDeleteFolder(name: String) {
    ui = ui.copy(modal = Modal.ConfirmDeleteFolder(name))
}

/**
 * Renames [oldName] to [newNameRaw] across every member. If another folder already matches
 * case-insensitively, this **merges** into that folder's canonical casing. No-op when blank or
 * unchanged. Runs as a single IO pass with one reload at the end.
 */
fun BuildSearchModel.renameFolder(
    oldName: String,
    newNameRaw: String,
) {
    val newName = newNameRaw.trim()
    if (newName.isBlank() || newName == oldName) {
        ui = ui.copy(modal = null)
        return
    }
    // Merge when the target name already exists (case-insensitively): adopt its canonical casing.
    val existingMatch = ui.savedBuilds.mapNotNull { it.folder }.firstOrNull { it.equals(newName, ignoreCase = true) && it != oldName }
    val canonical = existingMatch ?: newName
    val merged = existingMatch != null
    val members = ui.savedBuilds.filter { it.folder == oldName }
    ui =
        ui.copy(
            modal = null,
            toast = (if (merged) Tr.TOAST_FOLDERS_MERGED else Tr.TOAST_FOLDER_RENAMED).value(ui.lang),
            libraryFolder = if (ui.libraryFolder == LibraryFolderFilter.Named(oldName)) LibraryFolderFilter.Named(canonical) else ui.libraryFolder,
            activeBuildName = ui.activeBuildName
        )
    scope.launch(ioDispatcher) {
        members.forEach { runCatching { historyRepository.save(it.copy(folder = canonical)) } }
        val all = historyRepository.loadAll()
        withContext(mainDispatcher) { ui = ui.copy(savedBuilds = all, libraryFolder = ui.libraryFolder.coercedTo(all)) }
    }
}

/** Deletes [name] by unfiling its members (the builds themselves are kept). */
fun BuildSearchModel.deleteFolder(name: String) {
    val members = ui.savedBuilds.filter { it.folder == name }
    ui =
        ui.copy(
            modal = null,
            toast = Tr.TOAST_FOLDER_DELETED.value(ui.lang),
            libraryFolder = if (ui.libraryFolder == LibraryFolderFilter.Named(name)) LibraryFolderFilter.All else ui.libraryFolder
        )
    scope.launch(ioDispatcher) {
        members.forEach { runCatching { historyRepository.save(it.copy(folder = null)) } }
        val all = historyRepository.loadAll()
        withContext(mainDispatcher) { ui = ui.copy(savedBuilds = all, libraryFolder = ui.libraryFolder.coercedTo(all)) }
    }
}

/** If a Named filter points at a folder no build references anymore, fall back to All. */
private fun LibraryFolderFilter.coercedTo(builds: List<me.chosante.common.history.HistoryEntry>): LibraryFolderFilter =
    if (this is LibraryFolderFilter.Named && builds.none { it.folder == name }) LibraryFolderFilter.All else this

/**
 * Normalizes a folder name on assignment: blank → null, and a case-variant of an existing folder
 * adopts that folder's canonical casing (so picking "pvp" when "PvP" exists doesn't split them).
 */
internal fun BuildSearchModel.canonicalFolder(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return ui.savedBuilds.mapNotNull { it.folder }.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
}

// --- Tags (first-class: a registry of named tags, assignable to builds, persisted) ---

fun BuildSearchModel.requestCreateTag() {
    ui = ui.copy(modal = Modal.CreateTag)
}

fun BuildSearchModel.requestRenameTag(name: String) {
    ui = ui.copy(modal = Modal.RenameTag(name))
}

fun BuildSearchModel.requestDeleteTag(name: String) {
    ui = ui.copy(modal = Modal.ConfirmDeleteTag(name))
}

/** Creates a standalone tag in the registry (no build assignment). No-op on blank/duplicate. */
fun BuildSearchModel.createTag(nameRaw: String) {
    val name = nameRaw.trim()
    if (name.isBlank() || tagRegistry.any { it.equals(name, ignoreCase = true) }) {
        ui = ui.copy(modal = null)
        return
    }
    tagRegistry = tagRegistry + name
    libraryPreferences.saveTags(tagRegistry)
    ui = ui.copy(modal = null, knownTags = computeKnownTags(ui.savedBuilds))
}

/**
 * Renames tag [oldName] to [newNameRaw] across every build that carries it. If another tag already
 * matches case-insensitively, this **merges** into that tag's canonical casing (de-duped per build).
 * No-op when blank or unchanged. One IO pass, one reload.
 */
fun BuildSearchModel.renameTag(
    oldName: String,
    newNameRaw: String,
) {
    val newName = newNameRaw.trim()
    if (newName.isBlank() || newName.equals(oldName, ignoreCase = true)) {
        ui = ui.copy(modal = null)
        return
    }
    val existingMatch = tagRegistry.firstOrNull { it.equals(newName, ignoreCase = true) && !it.equals(oldName, ignoreCase = true) }
    val canonical = existingMatch ?: newName
    val merged = existingMatch != null
    // Update the registry: drop the old name, ensure the canonical one is present.
    tagRegistry = (tagRegistry.filterNot { it.equals(oldName, ignoreCase = true) } + canonical).distinctBy { it.lowercase() }
    libraryPreferences.saveTags(tagRegistry)
    val members = ui.savedBuilds.filter { entry -> entry.tags.any { it.equals(oldName, ignoreCase = true) } }
    ui =
        ui.copy(
            modal = null,
            toast = (if (merged) Tr.TOAST_TAGS_MERGED else Tr.TOAST_TAG_RENAMED).value(ui.lang),
            knownTags = computeKnownTags(ui.savedBuilds)
        )
    scope.launch(ioDispatcher) {
        members.forEach { entry ->
            val renamed = entry.tags.map { if (it.equals(oldName, ignoreCase = true)) canonical else it }
            runCatching { historyRepository.save(entry.copy(tags = normalizeTags(renamed))) }
        }
        val all = historyRepository.loadAll()
        withContext(mainDispatcher) {
            ui = ui.copy(savedBuilds = all, knownTags = computeKnownTags(all), librarySelectedTags = ui.librarySelectedTags.coercedToTags(all, tagRegistry))
        }
    }
}

/** Deletes tag [name] entirely: from the registry and from every build (the builds are kept). */
fun BuildSearchModel.deleteTag(name: String) {
    tagRegistry = tagRegistry.filterNot { it.equals(name, ignoreCase = true) }
    libraryPreferences.saveTags(tagRegistry)
    val members = ui.savedBuilds.filter { entry -> entry.tags.any { it.equals(name, ignoreCase = true) } }
    ui = ui.copy(modal = null, toast = Tr.TOAST_TAG_DELETED.value(ui.lang), knownTags = computeKnownTags(ui.savedBuilds))
    scope.launch(ioDispatcher) {
        members.forEach { entry ->
            runCatching { historyRepository.save(entry.copy(tags = entry.tags.filterNot { it.equals(name, ignoreCase = true) })) }
        }
        val all = historyRepository.loadAll()
        withContext(mainDispatcher) {
            ui = ui.copy(savedBuilds = all, knownTags = computeKnownTags(all), librarySelectedTags = ui.librarySelectedTags.coercedToTags(all, tagRegistry))
        }
    }
}

/**
 * Drops any active tag-filter key that no longer exists — neither carried by a build nor in the
 * registry. A renamed/deleted tag is cleared, but a still-valid standalone (0-build) tag the user
 * is filtering by is kept.
 *
 * Takes the tag registry explicitly (rather than reading the outer `tagRegistry` field) because
 * this is a plain top-level extension on `Set<String>`, living outside the [BuildSearchModel] class
 * body -- a member extension function's implicit access to the enclosing class's fields doesn't
 * survive that move, so the caller (still a `BuildSearchModel` extension, with `tagRegistry` in
 * scope) passes it in.
 */
private fun Set<String>.coercedToTags(
    builds: List<me.chosante.common.history.HistoryEntry>,
    registry: List<String>,
): Set<String> {
    val present = (builds.flatMap { it.tags } + registry).map { it.lowercase() }.toSet()
    return this intersect present
}

fun BuildSearchModel.requestDelete(
    id: String,
    name: String,
) {
    ui = ui.copy(modal = Modal.ConfirmDelete(id, name))
}

/** Opens the compare view with [id] pre-selected in the first column. */
fun BuildSearchModel.startCompare(id: String) {
    ui = ui.copy(screen = Screen.Compare, compareSlots = listOf(id, null), modal = null)
}

/** Pin build [id] into compare column [index] (no-op if the index is out of range). */
fun BuildSearchModel.setCompareSlot(
    index: Int,
    id: String,
) {
    if (index !in ui.compareSlots.indices) return
    ui = ui.copy(compareSlots = ui.compareSlots.mapIndexed { i, slot -> if (i == index) id else slot })
}

/**
 * The ✕ on compare column [index]: removes the column when more than the two base columns exist,
 * otherwise just empties it — so there are always at least [MIN_COMPARE_SLOTS] columns to compare.
 */
fun BuildSearchModel.clearCompareSlot(index: Int) {
    if (index !in ui.compareSlots.indices) return
    ui =
        if (ui.compareSlots.size > MIN_COMPARE_SLOTS) {
            ui.copy(compareSlots = ui.compareSlots.filterIndexed { i, _ -> i != index })
        } else {
            ui.copy(compareSlots = ui.compareSlots.mapIndexed { i, slot -> if (i == index) null else slot })
        }
}

/** Append an empty compare column, up to [MAX_COMPARE_SLOTS]. */
fun BuildSearchModel.addCompareSlot() {
    if (ui.compareSlots.size >= MAX_COMPARE_SLOTS) return
    ui = ui.copy(compareSlots = ui.compareSlots + null)
}

fun BuildSearchModel.deleteBuild(id: String) {
    ui = ui.copy(modal = null)
    scope.launch(ioDispatcher) {
        runCatching { historyRepository.delete(id) }
        val all = historyRepository.loadAll()
        withContext(mainDispatcher) {
            val wasActive = ui.activeBuildId == id
            ui =
                ui.copy(
                    savedBuilds = all,
                    activeBuildId = if (wasActive) null else ui.activeBuildId,
                    activeBuildName = if (wasActive) null else ui.activeBuildName,
                    searchLocked = if (wasActive) false else ui.searchLocked,
                    compareSlots = ui.compareSlots.map { if (it == id) null else it },
                    knownTags = computeKnownTags(all),
                    libraryFolder = ui.libraryFolder.coercedTo(all),
                    librarySelectedTags = ui.librarySelectedTags.coercedToTags(all, tagRegistry)
                )
        }
    }
}
