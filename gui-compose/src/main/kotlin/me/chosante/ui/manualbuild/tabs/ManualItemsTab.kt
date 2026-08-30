package me.chosante.ui.manualbuild.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.chosante.common.Equipment
import me.chosante.common.ItemType
import me.chosante.common.Rarity
import me.chosante.ui.components.CharacteristicIcon
import me.chosante.ui.components.ItemThumbnail
import me.chosante.ui.components.RarityIcon
import me.chosante.ui.components.isEquippableForPicker
import me.chosante.ui.components.localizedName
import me.chosante.ui.i18n.Lang
import me.chosante.ui.i18n.LocalLang
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.label
import me.chosante.ui.i18n.sortedByLocalized
import me.chosante.ui.i18n.tr
import me.chosante.ui.paperdoll.naturalSlotIdFor
import me.chosante.ui.paperdoll.slotAssignments
import me.chosante.ui.state.BuildSearchModel
import me.chosante.ui.state.UiState
import me.chosante.ui.state.color
import me.chosante.ui.state.equipItemInManualSlot
import me.chosante.ui.state.resetManualItemFilters
import me.chosante.ui.state.setManualItemMaxLevel
import me.chosante.ui.state.setManualItemMinLevel
import me.chosante.ui.state.setManualItemQuery
import me.chosante.ui.state.toggleManualItemRarity
import me.chosante.ui.state.toggleManualItemType
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WDimens
import me.chosante.ui.theme.WType
import me.chosante.ui.theme.WTypography

/**
 * The manual-construction screen's Items tab: search + level range + type/rarity filter bars, then a
 * 2-column grid of full-stat item cards -- clicking one equips it directly (via
 * [me.chosante.ui.paperdoll.naturalSlotIdFor] to resolve which slot, then
 * [BuildSearchModel.equipItemInManualSlot]). Mirrors `MarketScreen.kt`'s `MarketSearchBar` filter
 * idiom (its chips are `private` there, so this tab's are its own).
 */
@Composable
fun ManualItemsTab(
    ui: UiState,
    equipmentCatalog: List<Equipment>?,
    model: BuildSearchModel,
    modifier: Modifier = Modifier,
) {
    val lang = LocalLang.current
    val assignments = remember(ui.manualBuild) { slotAssignments(ui.manualBuild?.equipments.orEmpty()) }
    val results =
        remember(
            equipmentCatalog,
            ui.manualItemQuery,
            ui.manualItemMinLevel,
            ui.manualItemMaxLevel,
            ui.manualItemTypeFilter,
            ui.manualItemRarityFilter,
            lang
        ) {
            val catalog = equipmentCatalog ?: return@remember emptyList()
            val minLevel = ui.manualItemMinLevel.toIntOrNull() ?: 0
            val maxLevel = ui.manualItemMaxLevel.toIntOrNull() ?: 245
            val query = ui.manualItemQuery.trim()
            catalog
                .asSequence()
                // Level range only here -- rarity is exclusively this tab's own chip row (Rarity.EPIC/
                // emptySet() below are neutral upper-bound/exclusion args, not the auto-Builder's config;
                // the manual screen intentionally lets you equip any rarity it doesn't otherwise filter out).
                .filter { it.isEquippableForPicker(maxLevel, minLevel, Rarity.EPIC, emptySet()) }
                .filter { ui.manualItemTypeFilter.isEmpty() || it.itemType in ui.manualItemTypeFilter }
                .filter { ui.manualItemRarityFilter.isEmpty() || it.rarity in ui.manualItemRarityFilter }
                .filter { query.isBlank() || it.name.fr.contains(query, ignoreCase = true) || it.name.en.contains(query, ignoreCase = true) }
                .toList()
                .sortedByLocalized(lang) { it.localizedName(lang) }
                .take(if (query.isBlank()) 120 else 240)
        }
    Column(modifier = modifier.fillMaxSize().padding(WDimens.gap)) {
        ManualItemFilters(ui = ui, model = model)
        Spacer(modifier = Modifier.height(WDimens.gap))
        if (equipmentCatalog == null) {
            Text(text = "…", style = WTypography.bodyMedium.copy(color = WColor.muted))
        } else if (results.isEmpty()) {
            Text(text = tr(Tr.MANUAL_NO_ITEMS_MATCH), style = WTypography.bodyMedium.copy(color = WColor.muted), modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.equipmentId }) { equipment ->
                    ManualItemCard(
                        equipment = equipment,
                        lang = lang,
                        onClick = {
                            val slotId = naturalSlotIdFor(equipment.itemType, assignments)
                            model.equipItemInManualSlot(slotId, equipment)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualItemFilters(
    ui: UiState,
    model: BuildSearchModel,
) {
    val lang = LocalLang.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WDimens.radius))
                .background(WColor.surface)
                .border(1.dp, WColor.border, RoundedCornerShape(WDimens.radius))
                .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ManualFilterField(value = ui.manualItemQuery, onValueChange = model::setManualItemQuery, placeholder = tr(Tr.SEARCH_ITEMS), modifier = Modifier.weight(1f))
            ManualFilterField(
                value = ui.manualItemMinLevel,
                onValueChange = model::setManualItemMinLevel,
                placeholder = tr(Tr.MANUAL_ITEM_MIN_LEVEL),
                modifier = Modifier.width(80.dp)
            )
            ManualFilterField(
                value = ui.manualItemMaxLevel,
                onValueChange = model::setManualItemMaxLevel,
                placeholder = tr(Tr.MANUAL_ITEM_MAX_LEVEL),
                modifier = Modifier.width(80.dp)
            )
            ManualResetButton(onClick = model::resetManualItemFilters)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemType.entries.forEach { type ->
                ManualFilterChip(label = type.label(lang), selected = type in ui.manualItemTypeFilter, onClick = { model.toggleManualItemType(type) })
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Rarity.entries.forEach { rarity ->
                ManualFilterChip(
                    label = rarity.label(lang),
                    selected = rarity in ui.manualItemRarityFilter,
                    accent = rarity.color(),
                    onClick = { model.toggleManualItemRarity(rarity) }
                )
            }
        }
    }
}

@Composable
private fun ManualFilterField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    // Same visual idiom as MarketScreen's SmallTextField (private there) -- kept local since it's tiny.
    Box(
        modifier =
            modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WColor.bg)
                .border(1.dp, WColor.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush =
                androidx.compose.ui.graphics
                    .SolidColor(WColor.accent),
            textStyle = WTypography.bodySmall.copy(color = WColor.text)
        )
        if (value.isEmpty()) {
            Text(text = placeholder, style = WTypography.bodySmall.copy(color = WColor.faint))
        }
    }
}

@Composable
private fun ManualResetButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WColor.border, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = tr(Tr.MANUAL_RESET_FILTERS), style = WTypography.labelSmall.copy(color = WColor.muted))
    }
}

@Composable
private fun ManualFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: androidx.compose.ui.graphics.Color = WColor.accent,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) accent.copy(alpha = 0.22f) else WColor.bg)
                .border(1.dp, if (selected) accent else WColor.border, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = label, style = WTypography.labelSmall.copy(color = if (selected) WColor.text else WColor.muted))
    }
}

/** Past this many characteristic rows, a card shows "+N more" instead -- keeps every card in the
 * 2-column grid roughly the same height (see [MIN_CARD_HEIGHT]) regardless of how many stats an item
 * actually carries; [LazyVerticalGrid] doesn't stretch sibling cells to match each other, so capping +
 * a floor height is the standard way to make a grid of variable-content cards line up. */
private const val MAX_STATS_SHOWN = 6
private val MIN_CARD_HEIGHT = 168.dp

@Composable
private fun ManualItemCard(
    equipment: Equipment,
    lang: Lang,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_CARD_HEIGHT)
                .clip(RoundedCornerShape(WDimens.radius))
                .background(WColor.surface)
                .border(1.dp, WColor.hairline, RoundedCornerShape(WDimens.radius))
                .clickable(onClick = onClick)
                .padding(WDimens.pad)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ItemThumbnail(equipment = equipment, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RarityIcon(rarity = equipment.rarity, size = 13.dp)
                    Text(
                        text = equipment.localizedName(lang),
                        style = WTypography.bodyMedium.copy(color = WColor.text, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Lv ${equipment.level} · ${equipment.itemType.label(lang)}",
                    style = WTypography.labelSmall.copy(fontFamily = WType.mono, color = WColor.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val allStats = equipment.characteristics.entries.sortedBy { it.key.ordinal }
        if (allStats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                allStats.take(MAX_STATS_SHOWN).forEach { (characteristic, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CharacteristicIcon(characteristic = characteristic, size = 13.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = characteristic.label(lang),
                            style = WTypography.labelSmall.copy(color = WColor.muted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (value > 0) "+$value" else "$value",
                            style = WTypography.labelSmall.copy(fontFamily = WType.mono, color = WColor.text)
                        )
                    }
                }
                val remaining = allStats.size - MAX_STATS_SHOWN
                if (remaining > 0) {
                    Text(
                        text = tr(Tr.MANUAL_ITEM_MORE_STATS).format(remaining),
                        style = WTypography.labelSmall.copy(color = WColor.faint),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
