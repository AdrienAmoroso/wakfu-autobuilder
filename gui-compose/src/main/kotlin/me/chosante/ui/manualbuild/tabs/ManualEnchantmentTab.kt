package me.chosante.ui.manualbuild.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.chosante.autobuilder.genetic.wakfu.WakfuBestBuildFinderAlgorithm
import me.chosante.common.Equipment
import me.chosante.common.RuneColor
import me.chosante.common.RuneType
import me.chosante.common.Sublimation
import me.chosante.common.SublimationRarity
import me.chosante.ui.components.ItemThumbnail
import me.chosante.ui.components.localized
import me.chosante.ui.components.sublimationEffectText
import me.chosante.ui.i18n.Lang
import me.chosante.ui.i18n.LocalLang
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.label
import me.chosante.ui.i18n.sortedByLocalized
import me.chosante.ui.i18n.tr
import me.chosante.ui.paperdoll.RuneShape
import me.chosante.ui.state.BuildSearchModel
import me.chosante.ui.state.UiState
import me.chosante.ui.state.clearManualRuneSlot
import me.chosante.ui.state.placeManualRuneInSlot
import me.chosante.ui.state.removeManualSublimationFromItem
import me.chosante.ui.state.runeOptions
import me.chosante.ui.state.setManualSublimationForItem
import me.chosante.ui.state.toggleManualRuneSlotGold
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WDimens
import me.chosante.ui.theme.WTypography

/**
 * The manual-construction screen's Enchantment tab: the build's own items (left, one row each --
 * icon, then its rune sockets in physical-slot order, then its one sublimation slot, all reflecting
 * live state) and the rune ("chasse") / sublimation catalogs to pick from (right). Click a rune/sub on
 * the right to "arm" it, then click a slot on the left to place it there (overwriting whatever was in
 * it); click a filled slot with nothing armed to clear it; right-click a filled rune slot to flag it
 * "gold" (wildcard socket colour, the in-game gold-rune mechanic).
 */
@Composable
fun ManualEnchantmentTab(
    ui: UiState,
    model: BuildSearchModel,
    modifier: Modifier = Modifier,
) {
    val lang = LocalLang.current
    val equipments = ui.manualBuild?.equipments.orEmpty()
    var armedRune by remember { mutableStateOf<RuneType?>(null) }
    var armedSub by remember { mutableStateOf<Sublimation?>(null) }

    Row(modifier = modifier.fillMaxSize().padding(WDimens.gap), horizontalArrangement = Arrangement.spacedBy(WDimens.gap)) {
        EnchantMyItemsPane(
            ui = ui,
            equipments = equipments,
            lang = lang,
            armedRune = armedRune,
            armedSub = armedSub,
            onPlaceRune = { itemName, slotIndex ->
                armedRune?.let { rune -> model.placeManualRuneInSlot(itemName, slotIndex, rune) }
                armedRune = null
            },
            onClearRuneSlot = model::clearManualRuneSlot,
            onToggleGold = model::toggleManualRuneSlotGold,
            onApplySub = { itemName ->
                armedSub?.let { sub -> model.setManualSublimationForItem(itemName, sub) }
                armedSub = null
            },
            onRemoveSub = model::removeManualSublimationFromItem,
            modifier = Modifier.weight(0.42f).fillMaxHeight()
        )
        Column(modifier = Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(WDimens.gap)) {
            EnchantRunesPane(
                runeOptions = model.runeOptions,
                lang = lang,
                armed = armedRune,
                onArm = { rune ->
                    armedRune = if (armedRune == rune) null else rune
                    armedSub = null
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            EnchantSublimationsPane(
                lang = lang,
                armed = armedSub,
                onArm = { sub ->
                    armedSub = if (armedSub == sub) null else sub
                    armedRune = null
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PaneCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(WDimens.radius))
                .background(WColor.surface)
                .border(1.dp, WColor.border, RoundedCornerShape(WDimens.radius))
                .padding(WDimens.pad)
    ) {
        Text(text = title, style = WTypography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun EnchantMyItemsPane(
    ui: UiState,
    equipments: List<Equipment>,
    lang: Lang,
    armedRune: RuneType?,
    armedSub: Sublimation?,
    onPlaceRune: (String, Int) -> Unit,
    onClearRuneSlot: (String, Int) -> Unit,
    onToggleGold: (String, Int) -> Unit,
    onApplySub: (String) -> Unit,
    onRemoveSub: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaneCard(title = tr(Tr.MANUAL_ENCHANT_MY_ITEMS), modifier = modifier) {
        if (armedRune != null || armedSub != null) {
            Text(text = tr(Tr.MANUAL_ENCHANT_ARMED_HINT), style = WTypography.labelSmall.copy(color = WColor.accent), modifier = Modifier.padding(bottom = 8.dp))
        }
        if (equipments.isEmpty()) {
            Text(text = tr(Tr.MANUAL_NO_ITEMS_MATCH), style = WTypography.bodySmall.copy(color = WColor.muted))
            return@PaneCard
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(equipments, key = { it.equipmentId }) { equipment ->
                val itemName = equipment.name.fr
                EnchantItemRow(
                    equipment = equipment,
                    lang = lang,
                    runeSlots = ui.manualRuneSlots[itemName].orEmpty(),
                    goldSlots = ui.manualGoldRuneSlots[itemName].orEmpty(),
                    sub =
                        ui.manualBuild
                            ?.sublimations
                            ?.get(equipment)
                            ?.firstOrNull(),
                    hasArmedRune = armedRune != null,
                    hasArmedSub = armedSub != null,
                    onSlotClick = { index, filled ->
                        if (armedRune != null) {
                            onPlaceRune(itemName, index)
                        } else if (filled) {
                            onClearRuneSlot(itemName, index)
                        }
                    },
                    onSlotRightClick = { index -> onToggleGold(itemName, index) },
                    onSubClick = { filled ->
                        if (armedSub != null) {
                            onApplySub(itemName)
                        } else if (filled) {
                            onRemoveSub(itemName)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnchantItemRow(
    equipment: Equipment,
    lang: Lang,
    runeSlots: List<RuneType?>,
    goldSlots: Set<Int>,
    sub: Sublimation?,
    hasArmedRune: Boolean,
    hasArmedSub: Boolean,
    onSlotClick: (Int, Boolean) -> Unit,
    onSlotRightClick: (Int) -> Unit,
    onSubClick: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(WColor.raised)
                .border(1.dp, WColor.hairline, RoundedCornerShape(9.dp))
                .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ItemThumbnail(equipment = equipment, size = 32.dp)
        Text(
            text = equipment.name.localized(lang),
            style = WTypography.labelSmall.copy(color = WColor.text, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        repeat(equipment.maxShardSlots) { index ->
            val rune = runeSlots.getOrNull(index)
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) { onSlotRightClick(index) }
                        .onClick { onSlotClick(index, rune != null) },
                contentAlignment = Alignment.Center
            ) {
                RuneShape(color = rune?.color, size = 24.dp, gold = index in goldSlots)
            }
        }
        SubSlot(sub = sub, lang = lang, onClick = { onSubClick(sub != null) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubSlot(
    sub: Sublimation?,
    lang: Lang,
    onClick: () -> Unit,
) {
    val content =
        @Composable {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (sub != null) WColor.accent.copy(alpha = 0.2f) else WColor.bg)
                        .border(1.dp, if (sub != null) WColor.accent else WColor.border, RoundedCornerShape(7.dp))
                        .onClick { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✦", style = WTypography.labelSmall.copy(color = if (sub != null) WColor.accent else WColor.faint))
            }
        }
    if (sub != null) {
        TooltipArea(tooltip = {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WColor.raised)
                        .border(1.dp, WColor.border, RoundedCornerShape(8.dp))
                        .padding(10.dp)
            ) {
                Column {
                    Text(text = sub.name.localized(lang), style = WTypography.labelSmall.copy(color = WColor.accent, fontWeight = FontWeight.Medium))
                    val effect = sublimationEffectText(sub, lang)
                    if (effect.isNotBlank()) {
                        Text(text = effect, style = WTypography.labelSmall.copy(color = WColor.muted))
                    }
                }
            }
        }) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun EnchantRunesPane(
    runeOptions: List<RuneType>,
    lang: Lang,
    armed: RuneType?,
    onArm: (RuneType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var colorFilter by remember { mutableStateOf<RuneColor?>(null) }
    val filtered =
        remember(runeOptions, query, colorFilter, lang) {
            val q = query.trim()
            runeOptions
                .asSequence()
                .filter { colorFilter == null || it.color == colorFilter }
                .filter { q.isBlank() || it.name.fr.contains(q, ignoreCase = true) || it.name.en.contains(q, ignoreCase = true) }
                .toList()
                .sortedByLocalized(lang) { it.name.localized(lang) }
        }
    PaneCard(title = tr(Tr.MANUAL_ENCHANT_RUNES), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null, RuneColor.RED, RuneColor.GREEN, RuneColor.BLUE).forEach { color ->
                RuneColorChip(color = color, selected = colorFilter == color, onClick = { colorFilter = if (colorFilter == color) null else color })
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        SmallSearchField(value = query, onValueChange = { query = it }, placeholder = tr(Tr.MANUAL_ENCHANT_SEARCH_RUNES))
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.id }) { rune ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (armed == rune) WColor.accent.copy(alpha = 0.18f) else WColor.raised)
                            .border(1.dp, if (armed == rune) WColor.accent else WColor.hairline, RoundedCornerShape(7.dp))
                            .clickable { onArm(rune) }
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RuneShape(color = rune.color, size = 14.dp)
                    Text(
                        text = rune.name.localized(lang),
                        style = WTypography.labelSmall.copy(color = WColor.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = rune.characteristic.label(lang), style = WTypography.labelSmall.copy(color = WColor.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EnchantSublimationsPane(
    lang: Lang,
    armed: Sublimation?,
    onArm: (Sublimation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var rarityFilter by remember { mutableStateOf<SublimationRarity?>(null) }
    val allSubs = remember { WakfuBestBuildFinderAlgorithm.sublimations.distinctBy { it.stateId } }
    val filtered =
        remember(allSubs, query, rarityFilter, lang) {
            val q = query.trim()
            allSubs
                .asSequence()
                .filter { rarityFilter == null || it.rarity == rarityFilter }
                .filter { sub -> q.isBlank() || sub.name.fr.contains(q, ignoreCase = true) || sub.name.en.contains(q, ignoreCase = true) }
                .toList()
                .sortedByLocalized(lang) { it.name.localized(lang) }
                .take(150)
        }
    PaneCard(title = tr(Tr.MANUAL_ENCHANT_SUBLIMATIONS), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null, SublimationRarity.NORMAL, SublimationRarity.EPIC, SublimationRarity.RELIC).forEach { rarity ->
                RarityFilterChip(rarity = rarity, selected = rarityFilter == rarity, onClick = { rarityFilter = if (rarityFilter == rarity) null else rarity })
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        SmallSearchField(value = query, onValueChange = { query = it }, placeholder = tr(Tr.MANUAL_ENCHANT_SEARCH_SUBS))
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.stateId }) { sub ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (armed == sub) WColor.accent.copy(alpha = 0.18f) else WColor.raised)
                            .border(1.dp, if (armed == sub) WColor.accent else WColor.hairline, RoundedCornerShape(7.dp))
                            .clickable { onArm(sub) }
                            .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = sub.name.localized(lang),
                            style = WTypography.labelSmall.copy(color = WColor.accent, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = sub.rarity.name, style = WTypography.labelSmall.copy(color = WColor.muted))
                    }
                    val effect = sublimationEffectText(sub, lang)
                    if (effect.isNotBlank()) {
                        Text(text = effect, style = WTypography.labelSmall.copy(color = WColor.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuneColorChip(
    color: RuneColor?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint =
        when (color) {
            RuneColor.RED -> WColor.danger
            RuneColor.GREEN -> WColor.success
            RuneColor.BLUE -> WColor.accent2
            null -> WColor.muted
        }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) tint.copy(alpha = 0.22f) else WColor.bg)
                .border(1.dp, if (selected) tint else WColor.border, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = color?.name ?: tr(Tr.RARITY_ALL), style = WTypography.labelSmall.copy(color = if (selected) WColor.text else WColor.muted))
    }
}

@Composable
private fun RarityFilterChip(
    rarity: SublimationRarity?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) WColor.accent.copy(alpha = 0.22f) else WColor.bg)
                .border(1.dp, if (selected) WColor.accent else WColor.border, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = rarity?.name ?: tr(Tr.RARITY_ALL), style = WTypography.labelSmall.copy(color = if (selected) WColor.text else WColor.muted))
    }
}

@Composable
private fun SmallSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WColor.bg)
                .border(1.dp, WColor.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(WColor.accent),
            textStyle = WTypography.bodySmall.copy(color = WColor.text)
        )
        if (value.isEmpty()) {
            Text(text = placeholder, style = WTypography.bodySmall.copy(color = WColor.faint))
        }
    }
}
