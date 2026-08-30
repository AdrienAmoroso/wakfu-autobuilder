package me.chosante.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import me.chosante.autobuilder.domain.BuildCombination
import me.chosante.autobuilder.genetic.wakfu.computeCharacteristicsValues
import me.chosante.common.Character
import me.chosante.common.Characteristic
import me.chosante.common.Equipment
import me.chosante.common.ItemType
import me.chosante.common.Rarity
import me.chosante.ui.i18n.Tr

// [BuildSearchModel]'s manual-construction-screen functions ([Screen.ManualBuild]: equip/unequip,
// rune-slot placement + gold toggle, sublimations, skill allocation, tab/filter state, and its own
// Zenith export) -- split out of BuildSearchModel.kt, see BuildSearchModelSearch.kt's header.
// --- Manual build construction (Screen.ManualBuild) ---
// A second, solver-independent way to reach a build (à la Zenith/Wakfuli): equip/socket/allocate
// directly. Writes straight into ui.manualBuild's actual BuildCombination -- never ui.build,
// ui.forcedItems, or ui.forcedRunesByItem, which are solver INPUT constraints interpreted by a
// future search run, not assembled state. Every mutation re-checks BuildCombination.isValid()
// (which also covers sublimation legality) before committing, and toast-rejects otherwise.

internal fun BuildSearchModel.manualCharacterBaseCharacteristics(): Map<Characteristic, Int> = Character(ui.clazz, ui.level, ui.minLevel).baseCharacteristicValues

internal fun BuildSearchModel.commitManualBuild(
    candidate: BuildCombination,
    closeModal: Boolean = false,
): Boolean {
    if (!candidate.isValid()) {
        ui = ui.copy(toast = Tr.TOAST_INVALID_MANUAL_BUILD.value(ui.lang))
        return false
    }
    ui =
        ui.copy(
            manualBuild = candidate,
            manualAchieved = computeCharacteristicsValues(candidate, manualCharacterBaseCharacteristics(), emptyMap(), emptyMap()),
            modal = if (closeModal) null else ui.modal
        )
    return true
}

/** Jump to the Items tab, pre-filtered to [slotId]'s compatible types -- works for an empty OR an
 * already-filled slot (picking a card there replaces whatever is equipped). Replaces the old
 * modal-based picker: the Items tab IS the picker now. */
fun BuildSearchModel.openManualItemsTabForSlot(slotId: String) {
    ensureCatalogLoaded()
    ui =
        ui.copy(
            manualActiveTab = ManualTab.ITEMS,
            manualItemTypeFilter =
                me.chosante.ui.paperdoll
                    .itemTypesForSlot(slotId)
        )
}

/**
 * Equip [equipment] directly into [slotId] -- replace-in-slot semantics, no solver constraint
 * involved (contrast [pickItem]). The two weapon slots special-case: a two-handed weapon occupies
 * BOTH, so equipping either one clears both first, restoring the other slot's previous occupant
 * only when it's still compatible (neither pick is a two-hander).
 */
fun BuildSearchModel.equipItemInManualSlot(
    slotId: String,
    equipment: Equipment,
) {
    val current =
        ui.manualBuild ?: BuildCombination(
            equipments = emptyList(),
            characterSkills =
                me.chosante.common.skills
                    .CharacterSkills(ui.level)
        )
    val assignments =
        me.chosante.ui.paperdoll
            .slotAssignments(current.equipments)
    val newEquipments = current.equipments.toMutableList()
    when (slotId) {
        "weapon", "weapon2" -> {
            val other = assignments[if (slotId == "weapon") "weapon2" else "weapon"]
            newEquipments.removeAll {
                it.itemType == ItemType.ONE_HANDED_WEAPONS || it.itemType == ItemType.TWO_HANDED_WEAPONS || it.itemType == ItemType.OFF_HAND_WEAPONS
            }
            if (equipment.itemType != ItemType.TWO_HANDED_WEAPONS && other != null && other.itemType != ItemType.TWO_HANDED_WEAPONS) {
                newEquipments += other
            }
        }
        else -> assignments[slotId]?.let { newEquipments.remove(it) }
    }
    newEquipments += equipment
    commitManualBuild(current.copy(equipments = newEquipments), closeModal = true)
}

/** Right-click "Unequip" on a filled manual-build slot; drops its runes/sublimations/slot-state
 * with it. */
fun BuildSearchModel.unequipManualItem(equipment: Equipment) {
    val current = ui.manualBuild ?: return
    ui =
        ui.copy(
            manualRuneSlots = ui.manualRuneSlots - equipment.name.fr,
            manualGoldRuneSlots = ui.manualGoldRuneSlots - equipment.name.fr
        )
    commitManualBuild(
        current.copy(
            equipments = current.equipments - equipment,
            runes = current.runes - equipment,
            sublimations = current.sublimations - equipment
        )
    )
}

/** Re-derives [BuildCombination.runes] for [equipment] from [UiState.manualRuneSlots] -- a
 * compacted (nulls filtered), order-independent list, which is all stat computation/Zenith
 * export ever reads. The slot positions/gold flags themselves live only in [UiState.manualRuneSlots]/
 * [UiState.manualGoldRuneSlots], never in [BuildCombination] (see [placeManualRuneInSlot]'s doc). */
internal fun BuildSearchModel.syncManualRunesFromSlots(equipment: Equipment) {
    val current = ui.manualBuild ?: return
    val compacted = ui.manualRuneSlots[equipment.name.fr].orEmpty().filterNotNull()
    val newRunes = if (compacted.isEmpty()) current.runes - equipment else current.runes + (equipment to compacted)
    commitManualBuild(current.copy(runes = newRunes))
}

/**
 * Enchantment tab: socket [rune] into [itemName]'s physical slot [slotIndex] (0-based, < its
 * [me.chosante.common.Equipment.maxShardSlots]) -- overwrites whatever was there, including any
 * gold flag (a freshly-placed rune is never gold until explicitly toggled). [UiState.manualRuneSlots]
 * is the slot-addressed source of truth; [BuildCombination.runes] is re-derived from it
 * ([syncManualRunesFromSlots]) since stat totals/Zenith export only ever need the compacted list.
 */
fun BuildSearchModel.placeManualRuneInSlot(
    itemName: String,
    slotIndex: Int,
    rune: me.chosante.common.RuneType,
) {
    val equipment = ui.manualBuild?.equipments?.firstOrNull { it.name.fr == itemName } ?: return
    if (slotIndex !in 0 until equipment.maxShardSlots) return
    val slots = (ui.manualRuneSlots[itemName] ?: List(equipment.maxShardSlots) { null }).toMutableList()
    slots[slotIndex] = rune
    ui =
        ui.copy(
            manualRuneSlots = ui.manualRuneSlots + (itemName to slots),
            manualGoldRuneSlots = ui.manualGoldRuneSlots + (itemName to (ui.manualGoldRuneSlots[itemName].orEmpty() - slotIndex))
        )
    syncManualRunesFromSlots(equipment)
}

/** Enchantment tab: empty [itemName]'s slot [slotIndex] (click a filled slot with nothing armed). */
fun BuildSearchModel.clearManualRuneSlot(
    itemName: String,
    slotIndex: Int,
) {
    val equipment = ui.manualBuild?.equipments?.firstOrNull { it.name.fr == itemName } ?: return
    val slots = ui.manualRuneSlots[itemName]?.toMutableList() ?: return
    if (slotIndex !in slots.indices) return
    slots[slotIndex] = null
    ui =
        ui.copy(
            manualRuneSlots = ui.manualRuneSlots + (itemName to slots),
            manualGoldRuneSlots = ui.manualGoldRuneSlots + (itemName to (ui.manualGoldRuneSlots[itemName].orEmpty() - slotIndex))
        )
    syncManualRunesFromSlots(equipment)
}

/** Enchantment tab: right-click a FILLED slot to flag it "gold" (wildcard socket colour) -- a
 * cosmetic-only override matching the in-game gold-rune mechanic ([UiState.manualGoldRuneSlots]);
 * it never changes [BuildCombination.runes] or any computed stat. No-ops on an empty slot. */
fun BuildSearchModel.toggleManualRuneSlotGold(
    itemName: String,
    slotIndex: Int,
) {
    if (ui.manualRuneSlots[itemName]?.getOrNull(slotIndex) == null) return
    val current = ui.manualGoldRuneSlots[itemName].orEmpty()
    ui = ui.copy(manualGoldRuneSlots = ui.manualGoldRuneSlots + (itemName to (if (slotIndex in current) current - slotIndex else current + slotIndex)))
}

/**
 * Enchantment tab: socket [sub] onto the carrier item named [itemName] -- REPLACES whatever
 * sublimation it already carries (there is exactly one sublimation slot per item in this UI,
 * matching [BuildCombination.hasLegalSublimations]'s "at most one per item" rule already enforced
 * for normal subs, and the global 1-epic/1-relic cap for those). Rejects (toast) when
 * [BuildCombination.isValid]'s sublimation-legality rules refuse it (wrong-rarity carrier, etc.).
 */
fun BuildSearchModel.setManualSublimationForItem(
    itemName: String,
    sub: me.chosante.common.Sublimation,
) {
    val current = ui.manualBuild ?: return
    val equipment = current.equipments.firstOrNull { it.name.fr == itemName } ?: return
    commitManualBuild(current.copy(sublimations = current.sublimations + (equipment to listOf(sub))))
}

/** Enchantment tab: empty [itemName]'s sublimation slot (click it with nothing armed). */
fun BuildSearchModel.removeManualSublimationFromItem(itemName: String) {
    val current = ui.manualBuild ?: return
    val equipment = current.equipments.firstOrNull { it.name.fr == itemName } ?: return
    commitManualBuild(current.copy(sublimations = current.sublimations - equipment))
}

/** Apply a full skill re-allocation (see [me.chosante.ui.manualbuild.manualSkillBranches]). */
fun BuildSearchModel.setManualSkills(skills: me.chosante.common.skills.CharacterSkills) {
    val current = ui.manualBuild ?: return
    commitManualBuild(current.copy(characterSkills = skills))
}

/** Start a brand-new, empty manual build for the current class/level (clears any in-progress one). */
fun BuildSearchModel.resetManualBuild() {
    ui =
        ui.copy(
            manualBuild =
                BuildCombination(
                    equipments = emptyList(),
                    characterSkills =
                        me.chosante.common.skills
                            .CharacterSkills(ui.level)
                ),
            manualAchieved = emptyMap(),
            manualZenithState = ZenithState.Idle,
            manualZenithUrl = null,
            manualActiveBuildId = null,
            manualActiveBuildName = null
        )
}

fun BuildSearchModel.setManualTab(tab: ManualTab) {
    ui = ui.copy(manualActiveTab = tab)
}

fun BuildSearchModel.setManualNote(text: String) {
    ui = ui.copy(manualNote = text)
}

fun BuildSearchModel.setManualItemQuery(query: String) {
    ui = ui.copy(manualItemQuery = query)
}

fun BuildSearchModel.setManualItemMinLevel(value: String) {
    ui = ui.copy(manualItemMinLevel = value.onlyDigits())
}

fun BuildSearchModel.setManualItemMaxLevel(value: String) {
    ui = ui.copy(manualItemMaxLevel = value.onlyDigits())
}

fun BuildSearchModel.toggleManualItemType(type: ItemType) {
    ui = ui.copy(manualItemTypeFilter = if (type in ui.manualItemTypeFilter) ui.manualItemTypeFilter - type else ui.manualItemTypeFilter + type)
}

fun BuildSearchModel.toggleManualItemRarity(rarity: Rarity) {
    ui =
        ui.copy(
            manualItemRarityFilter = if (rarity in ui.manualItemRarityFilter) ui.manualItemRarityFilter - rarity else ui.manualItemRarityFilter + rarity
        )
}

fun BuildSearchModel.resetManualItemFilters() {
    ui =
        ui.copy(
            manualItemQuery = "",
            manualItemMinLevel = "0",
            manualItemMaxLevel = "245",
            manualItemTypeFilter = emptySet(),
            manualItemRarityFilter = emptySet()
        )
}

fun BuildSearchModel.openManualZenithBuild() {
    createManualZenithLink { link ->
        runCatching {
            openBrowser(link)
        }.onFailure { exception ->
            ui = ui.copy(manualZenithState = ZenithState.Error, error = exception.message ?: "Unable to open Zenith")
        }
    }
}

fun BuildSearchModel.copyManualZenithLink() {
    createManualZenithLink { link ->
        copyToClipboard(link)
        ui = ui.copy(toast = Tr.TOAST_ZENITH_COPIED.value(ui.lang))
    }
}

internal fun BuildSearchModel.createManualZenithLink(onReady: (String) -> Unit) {
    createZenithLink(
        build = ui.manualBuild,
        setZenithState = { state -> ui = ui.copy(manualZenithState = state) },
        setZenithUrl = { url -> ui = ui.copy(manualZenithUrl = url) },
        onReady = onReady
    )
}
