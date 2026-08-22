package me.chosante.ui.market

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.chosante.common.Rarity
import me.chosante.marketclient.CaptureStatusResponse
import me.chosante.marketclient.CreateObservationRequest
import me.chosante.marketclient.FlagMotif
import me.chosante.marketclient.ItemInfoResponse
import me.chosante.marketclient.ItemSearchResult
import me.chosante.marketclient.ObservationResponse
import me.chosante.ui.components.Hairline
import me.chosante.ui.components.InfoTip
import me.chosante.ui.components.ItemBadge
import me.chosante.ui.components.ItemInfoBadge
import me.chosante.ui.components.LIST_PAGE_SIZE
import me.chosante.ui.components.MessageCard
import me.chosante.ui.components.PageControls
import me.chosante.ui.components.RarityIcon
import me.chosante.ui.components.SmallButton
import me.chosante.ui.components.SmallTextField
import me.chosante.ui.components.StatLine
import me.chosante.ui.components.TabButton
import me.chosante.ui.components.localized
import me.chosante.ui.components.pageCount
import me.chosante.ui.i18n.Lang
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.categoryLabel
import me.chosante.ui.i18n.craftDecisionLabel
import me.chosante.ui.i18n.label
import me.chosante.ui.i18n.tr
import me.chosante.ui.state.MarketState
import me.chosante.ui.state.MarketTab
import me.chosante.ui.state.UiState
import me.chosante.ui.state.color
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WDimens
import me.chosante.ui.theme.WTypography
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

@Composable
fun MarketScreen(
    ui: UiState,
    onSelectTab: (MarketTab) -> Unit,
    onCreateObservation: (CreateObservationRequest) -> Unit,
    onDeleteObservation: (Int) -> Unit,
    onUpdatePrices: (Int, Long, Long, Long?) -> Unit,
    onSetFlag: (Int, FlagMotif) -> Unit,
    onCraftCostItemIdChange: (String) -> Unit,
    onLookupCraftCost: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onRequestItemInfo: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMinLevelChange: (String) -> Unit,
    onMaxLevelChange: (String) -> Unit,
    onToggleRarityFilter: (Rarity) -> Unit,
    onToggleCategoryFilter: (String) -> Unit,
    onToggleExpandedItem: (Int) -> Unit,
    onExportCsv: () -> Unit,
    onSetPage: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(WColor.bg)) {
        MarketTabHeader(current = ui.marketTab, onSelect = onSelectTab)
        Box(modifier = Modifier.fillMaxSize().padding(WDimens.pad)) {
            when (ui.marketTab) {
                MarketTab.PRICES ->
                    PricesTab(
                        ui = ui,
                        onCreateObservation = onCreateObservation,
                        onDeleteObservation = onDeleteObservation,
                        onUpdatePrices = onUpdatePrices,
                        onSetFlag = onSetFlag,
                        onStartCapture = onStartCapture,
                        onStopCapture = onStopCapture,
                        onSearchQueryChange = onSearchQueryChange,
                        onMinLevelChange = onMinLevelChange,
                        onMaxLevelChange = onMaxLevelChange,
                        onToggleRarityFilter = onToggleRarityFilter,
                        onToggleCategoryFilter = onToggleCategoryFilter,
                        onToggleExpandedItem = onToggleExpandedItem,
                        onExportCsv = onExportCsv,
                        onSetPage = onSetPage
                    )

                MarketTab.CRAFT_COST ->
                    CraftCostTab(
                        ui = ui,
                        onCraftCostItemIdChange = onCraftCostItemIdChange,
                        onLookupCraftCost = onLookupCraftCost,
                        onRequestItemInfo = onRequestItemInfo
                    )
            }
        }
    }
}

@Composable
private fun MarketTabHeader(
    current: MarketTab,
    onSelect: (MarketTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(WColor.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WColor.bg)
                        .border(1.dp, WColor.border, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TabButton(label = tr(Tr.MARKET_TAB_PRICES), selected = current == MarketTab.PRICES) { onSelect(MarketTab.PRICES) }
                TabButton(
                    label = tr(Tr.MARKET_TAB_CRAFT_COST),
                    selected = current == MarketTab.CRAFT_COST
                ) { onSelect(MarketTab.CRAFT_COST) }
            }
        }
        Hairline()
    }
}

@Composable
private fun PricesTab(
    ui: UiState,
    onCreateObservation: (CreateObservationRequest) -> Unit,
    onDeleteObservation: (Int) -> Unit,
    onUpdatePrices: (Int, Long, Long, Long?) -> Unit,
    onSetFlag: (Int, FlagMotif) -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMinLevelChange: (String) -> Unit,
    onMaxLevelChange: (String) -> Unit,
    onToggleRarityFilter: (Rarity) -> Unit,
    onToggleCategoryFilter: (String) -> Unit,
    onToggleExpandedItem: (Int) -> Unit,
    onExportCsv: () -> Unit,
    onSetPage: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CaptureBar(status = ui.captureStatus, onStart = onStartCapture, onStop = onStopCapture)
        Spacer(modifier = Modifier.height(14.dp))
        MarketSearchBar(
            query = ui.marketSearchQuery,
            minLevel = ui.marketMinLevel,
            maxLevel = ui.marketMaxLevel,
            selectedRarities = ui.marketRarityFilter,
            selectedCategories = ui.marketCategoryFilter,
            lang = ui.lang,
            onQueryChange = onSearchQueryChange,
            onMinLevelChange = onMinLevelChange,
            onMaxLevelChange = onMaxLevelChange,
            onToggleRarity = onToggleRarityFilter,
            onToggleCategory = onToggleCategoryFilter
        )
        Spacer(modifier = Modifier.height(10.dp))
        val totalPages = pageCount(ui.marketSearchResults.size)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (ui.marketSearchResults.isNotEmpty()) {
                PageControls(page = ui.marketPage, pageCount = totalPages, onSetPage = onSetPage)
            } else {
                Spacer(modifier = Modifier)
            }
            SmallButton(text = tr(Tr.EXPORT_CSV_BUTTON), onClick = onExportCsv)
        }
        Spacer(modifier = Modifier.height(4.dp))
        val pagedResults = remember(ui.marketSearchResults, ui.marketPage) { ui.marketSearchResults.chunked(LIST_PAGE_SIZE).getOrElse(ui.marketPage) { emptyList() } }
        when {
            // A background refresh (e.g. after saving a price) failing must never blank out an
            // already-good list -- prefer showing what's still on screen over an error card, even
            // if marketSearchState flipped to Error in the meantime.
            ui.marketSearchResults.isNotEmpty() ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pagedResults, key = { it.item.itemId }) { result ->
                        ItemSearchResultCard(
                            result = result,
                            lang = ui.lang,
                            expanded = ui.marketExpandedItemId == result.item.itemId,
                            observations = if (ui.marketExpandedItemId == result.item.itemId) ui.marketObservations else emptyList(),
                            observationsLoading = ui.marketExpandedItemId == result.item.itemId && ui.marketLoadState == MarketState.Loading,
                            onToggleExpanded = { onToggleExpandedItem(result.item.itemId) },
                            onCreateObservation = onCreateObservation,
                            onDeleteObservation = onDeleteObservation,
                            onUpdatePrices = onUpdatePrices,
                            onSetFlag = onSetFlag
                        )
                    }
                }

            ui.marketSearchState == MarketState.Error ->
                MessageCard(
                    title = tr(Tr.CANT_REACH_MARKET_SERVER),
                    hint = ui.error ?: tr(Tr.START_MARKET_SERVER_HINT)
                )

            ui.marketSearchState == MarketState.Ready ->
                MessageCard(title = tr(Tr.NO_ITEMS_MATCH_TITLE), hint = tr(Tr.NO_ITEMS_MATCH_HINT))
        }
    }
}

private val MARKET_CATEGORIES =
    listOf("equipment", "creature", "resource", "consumable", "customization", "miscellaneous", "sublimation", "torch", "tool", "costume")

@Composable
private fun MarketSearchBar(
    query: String,
    minLevel: String,
    maxLevel: String,
    selectedRarities: Set<Rarity>,
    selectedCategories: Set<String>,
    lang: Lang,
    onQueryChange: (String) -> Unit,
    onMinLevelChange: (String) -> Unit,
    onMaxLevelChange: (String) -> Unit,
    onToggleRarity: (Rarity) -> Unit,
    onToggleCategory: (String) -> Unit,
) {
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
            SmallTextField(value = query, onValueChange = onQueryChange, placeholder = tr(Tr.MARKET_SEARCH_BY_NAME), modifier = Modifier.weight(1f))
            SmallTextField(value = minLevel, onValueChange = onMinLevelChange, placeholder = tr(Tr.MARKET_MIN_LVL), modifier = Modifier.width(90.dp))
            SmallTextField(value = maxLevel, onValueChange = onMaxLevelChange, placeholder = tr(Tr.MARKET_MAX_LVL), modifier = Modifier.width(90.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Rarity.entries.forEach { rarity ->
                RarityChip(rarity = rarity, lang = lang, selected = rarity in selectedRarities, onClick = { onToggleRarity(rarity) })
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MARKET_CATEGORIES.forEach { category ->
                CategoryChip(category = category, lang = lang, selected = category in selectedCategories, onClick = { onToggleCategory(category) })
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: String,
    lang: Lang,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) WColor.accent.copy(alpha = 0.22f) else WColor.bg)
                .border(1.dp, if (selected) WColor.accent else WColor.border, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = categoryLabel(category, lang), style = WTypography.labelSmall.copy(color = if (selected) WColor.text else WColor.muted))
    }
}

@Composable
private fun RarityChip(
    rarity: Rarity,
    lang: Lang,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) rarity.color().copy(alpha = 0.22f) else WColor.bg)
                .border(1.dp, if (selected) rarity.color() else WColor.border, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        RarityIcon(rarity = rarity, size = 11.dp)
        Text(text = rarity.label(lang), style = WTypography.labelSmall.copy(color = if (selected) WColor.text else WColor.muted))
    }
}

@Composable
private fun CaptureBar(
    status: CaptureStatusResponse?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val phase = status?.phase ?: "idle"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WDimens.radius))
                .background(WColor.surface)
                .border(1.dp, WColor.border, RoundedCornerShape(WDimens.radius))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (phase) {
            "capturing" -> SmallButton(text = tr(Tr.CAPTURE_STOP), onClick = onStop, filled = true)
            "processing" -> Text(text = tr(Tr.CAPTURE_PROCESSING), style = WTypography.labelMedium.copy(color = WColor.muted))
            else -> SmallButton(text = tr(Tr.CAPTURE_START), onClick = onStart, filled = true)
        }
        val statusText =
            when (phase) {
                "capturing" -> tr(Tr.CAPTURE_CAPTURING_SINCE).format(elapsedLabel(status?.startedAt))
                "processing" -> tr(Tr.CAPTURE_PARSING)
                "error" -> tr(Tr.CAPTURE_ERROR).format(status?.message ?: tr(Tr.CAPTURE_UNKNOWN_ERROR))
                else -> status?.lastImportedCount?.let { tr(Tr.CAPTURE_IDLE_LAST_IMPORT).format(it) } ?: tr(Tr.CAPTURE_IDLE)
            }
        Text(text = statusText, style = WTypography.bodySmall.copy(color = WColor.muted), modifier = Modifier.weight(1f))
    }
}

private fun elapsedLabel(startedAt: Long?): String {
    if (startedAt == null) return "0s"
    val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

/**
 * Best-effort "N ago" for an observation's `observedAt` -- tolerant of the handful of timestamp
 * shapes in play (manual entries stamp `LocalDateTime.now().toString()`; the external capture
 * pipeline's own format isn't controlled by this repo). Falls back to the raw string rather than
 * guessing or crashing when it doesn't parse.
 */
@Composable
@ReadOnlyComposable
private fun observedAgoLabel(observedAt: String): String {
    val parsed =
        runCatching { LocalDateTime.parse(observedAt) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(observedAt).toLocalDateTime() }.getOrNull()
            ?: runCatching { Instant.parse(observedAt).atZone(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
            ?: return observedAt
    val minutes = Duration.between(parsed, LocalDateTime.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> tr(Tr.JUST_NOW)
        minutes < 60 -> tr(Tr.AGO_MINUTES).format(minutes)
        minutes < 60 * 24 -> tr(Tr.AGO_HOURS).format(minutes / 60)
        else -> tr(Tr.AGO_DAYS).format(minutes / (60 * 24))
    }
}

/**
 * One row of the HDV-style browse list: the item itself, its latest known price (or "No price
 * captured yet"), and — expanded — its full observation history with edit/flag/delete and a form to
 * add a missing price by hand. This is the Prices tab's main content, replacing a flat table of raw
 * observation rows with an item-first view closer to the in-game auction house.
 */
@Composable
private fun ItemSearchResultCard(
    result: ItemSearchResult,
    lang: Lang,
    expanded: Boolean,
    observations: List<ObservationResponse>,
    observationsLoading: Boolean,
    onToggleExpanded: () -> Unit,
    onCreateObservation: (CreateObservationRequest) -> Unit,
    onDeleteObservation: (Int) -> Unit,
    onUpdatePrices: (Int, Long, Long, Long?) -> Unit,
    onSetFlag: (Int, FlagMotif) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(WColor.surface)
                .border(1.dp, if (expanded) WColor.accent else WColor.hairline, RoundedCornerShape(9.dp))
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ItemInfoBadge(item = result.item, lang = lang, modifier = Modifier.widthIn(min = 220.dp))
            PriceSummary(result = result, modifier = Modifier.weight(1f))
            Text(text = if (expanded) "▲" else "▼", style = WTypography.labelMedium.copy(color = WColor.muted))
        }
        if (expanded) {
            Hairline()
            Column(modifier = Modifier.padding(12.dp)) {
                ObservationHistoryPanel(
                    itemId = result.item.itemId,
                    observations = observations,
                    loading = observationsLoading,
                    onCreateObservation = onCreateObservation,
                    onDeleteObservation = onDeleteObservation,
                    onUpdatePrices = onUpdatePrices,
                    onSetFlag = onSetFlag
                )
            }
        }
    }
}

@Composable
private fun PriceSummary(
    result: ItemSearchResult,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (result.latestMinPrice == null) {
            Text(text = tr(Tr.NO_PRICE_CAPTURED_YET), style = WTypography.bodySmall.copy(color = WColor.faint))
        } else {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = tr(Tr.LOWEST_AVERAGE_SUMMARY).format(result.latestMinPrice, result.latestAvgPrice),
                        style = WTypography.bodyMedium
                    )
                    InfoTip(text = tr(Tr.PRICE_SUMMARY_INFO))
                }
                Text(
                    text =
                        listOfNotNull(result.latestServer, result.latestObservedAt?.let { observedAgoLabel(it) })
                            .joinToString(" · "),
                    style = WTypography.labelSmall.copy(color = WColor.muted)
                )
            }
        }
    }
}

/**
 * The expanded item's full price-observation history: one row per capture session (or manual
 * entry), each independently editable/flaggable/deletable, plus a compact form to add a price by
 * hand when the item was never captured.
 */
@Composable
private fun ObservationHistoryPanel(
    itemId: Int,
    observations: List<ObservationResponse>,
    loading: Boolean,
    onCreateObservation: (CreateObservationRequest) -> Unit,
    onDeleteObservation: (Int) -> Unit,
    onUpdatePrices: (Int, Long, Long, Long?) -> Unit,
    onSetFlag: (Int, FlagMotif) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = tr(Tr.PRICE_HISTORY_TITLE), style = WTypography.titleMedium)
            InfoTip(text = tr(Tr.PRICE_HISTORY_INFO))
            if (loading) Text(text = tr(Tr.LOADING_ELLIPSIS), style = WTypography.labelSmall.copy(color = WColor.muted))
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (observations.isEmpty() && !loading) {
            Text(text = tr(Tr.NO_OBSERVATIONS_YET), style = WTypography.bodySmall.copy(color = WColor.muted))
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            observations.forEach { observation ->
                ObservationRow(
                    observation = observation,
                    onDelete = { onDeleteObservation(observation.id) },
                    onUpdatePrices = onUpdatePrices,
                    onSetFlag = onSetFlag
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        AddObservationForm(itemId = itemId, onCreate = onCreateObservation)
    }
}

@Composable
private fun AddObservationForm(
    itemId: Int,
    onCreate: (CreateObservationRequest) -> Unit,
) {
    var server by remember(itemId) { mutableStateOf("") }
    var minPrice by remember(itemId) { mutableStateOf("") }
    var avgPrice by remember(itemId) { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = tr(Tr.ADD_PRICE_BY_HAND), style = WTypography.bodySmall.copy(color = WColor.muted))
        SmallTextField(value = server, onValueChange = { server = it }, placeholder = tr(Tr.SERVER_PLACEHOLDER), modifier = Modifier.width(120.dp))
        SmallTextField(
            value = minPrice,
            onValueChange = { minPrice = it.filter(Char::isDigit) },
            placeholder = tr(Tr.LOWEST_PRICE_PLACEHOLDER),
            modifier = Modifier.width(110.dp)
        )
        SmallTextField(
            value = avgPrice,
            onValueChange = { avgPrice = it.filter(Char::isDigit) },
            placeholder = tr(Tr.AVERAGE_PRICE_PLACEHOLDER),
            modifier = Modifier.width(120.dp)
        )
        SmallButton(
            text = tr(Tr.ADD_BUTTON),
            filled = true,
            onClick = {
                val min = minPrice.toLongOrNull() ?: return@SmallButton
                val avg = avgPrice.toLongOrNull() ?: min
                onCreate(
                    CreateObservationRequest(
                        itemId = itemId,
                        server = server.ifBlank { "manual" },
                        observedAt = LocalDateTime.now().toString(),
                        source = "manual_entry",
                        confidenceScore = 1.0,
                        minPrice = min,
                        avgPrice = avg
                    )
                )
                minPrice = ""
                avgPrice = ""
            }
        )
    }
}

@Composable
private fun ObservationRow(
    observation: ObservationResponse,
    onDelete: () -> Unit,
    onUpdatePrices: (Int, Long, Long, Long?) -> Unit,
    onSetFlag: (Int, FlagMotif) -> Unit,
) {
    var minPriceText by remember(observation.id) { mutableStateOf(observation.minPrice.toString()) }
    var avgPriceText by remember(observation.id) { mutableStateOf(observation.avgPrice.toString()) }
    var flagMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(WColor.bg)
                .border(1.dp, WColor.hairline, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.widthIn(min = 110.dp)) {
            Text(text = observation.server.ifBlank { "—" }, style = WTypography.bodySmall)
            Text(text = observedAgoLabel(observation.observedAt), style = WTypography.labelSmall.copy(color = WColor.muted))
        }
        LabeledField(label = tr(Tr.LOWEST_FIELD_LABEL), value = minPriceText, onValueChange = { minPriceText = it.filter(Char::isDigit) })
        LabeledField(label = tr(Tr.AVERAGE_FIELD_LABEL), value = avgPriceText, onValueChange = { avgPriceText = it.filter(Char::isDigit) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallButton(
                text = tr(Tr.SAVE_BUTTON),
                onClick = {
                    val min = minPriceText.toLongOrNull() ?: return@SmallButton
                    val avg = avgPriceText.toLongOrNull() ?: return@SmallButton
                    onUpdatePrices(observation.id, min, avg, observation.medianPrice)
                }
            )
            InfoTip(text = tr(Tr.OBSERVATION_CORRECT_HINT))
        }
        Text(
            text = observation.comment?.takeIf { it.isNotBlank() } ?: "",
            style = WTypography.bodySmall.copy(color = WColor.muted),
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                SmallButton(text = tr(Tr.FLAG_BUTTON), onClick = { flagMenuOpen = true })
                DropdownMenu(expanded = flagMenuOpen, onDismissRequest = { flagMenuOpen = false }, containerColor = WColor.surface) {
                    flagMotifOptions().forEach { (motif, label, explanation) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(text = label, style = WTypography.bodyMedium)
                                    Text(text = explanation, style = WTypography.labelSmall.copy(color = WColor.muted))
                                }
                            },
                            onClick = {
                                onSetFlag(observation.id, motif)
                                flagMenuOpen = false
                            }
                        )
                    }
                }
            }
            InfoTip(text = tr(Tr.FLAG_INFO_HINT))
        }
        SmallButton(text = tr(Tr.DELETE_BUTTON), onClick = onDelete)
    }
}

@Composable
@ReadOnlyComposable
private fun flagMotifOptions() =
    listOf(
        Triple(FlagMotif.PARSING_ERROR, tr(Tr.FLAG_PARSING_ERROR), tr(Tr.FLAG_PARSING_ERROR_HINT)),
        Triple(FlagMotif.OUTLIER, tr(Tr.FLAG_OUTLIER), tr(Tr.FLAG_OUTLIER_HINT)),
        Triple(FlagMotif.DUPLICATE, tr(Tr.FLAG_DUPLICATE), tr(Tr.FLAG_DUPLICATE_HINT)),
        Triple(FlagMotif.MANUAL_CHECK, tr(Tr.FLAG_NEEDS_REVIEW), tr(Tr.FLAG_NEEDS_REVIEW_HINT))
    )

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.wrapContentWidth()) {
        Text(text = label, style = WTypography.labelSmall.copy(color = WColor.muted))
        SmallTextField(value = value, onValueChange = onValueChange, placeholder = label, modifier = Modifier.width(90.dp))
    }
}

@Composable
private fun CraftCostTab(
    ui: UiState,
    onCraftCostItemIdChange: (String) -> Unit,
    onLookupCraftCost: () -> Unit,
    onRequestItemInfo: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallTextField(
                value = ui.craftCostItemId,
                onValueChange = onCraftCostItemIdChange,
                placeholder = tr(Tr.CRAFT_COST_ITEM_ID_PLACEHOLDER),
                modifier = Modifier.width(200.dp)
            )
            SmallButton(text = tr(Tr.LOOKUP_BUTTON), onClick = onLookupCraftCost, filled = true)
            if (ui.craftCostState == MarketState.Loading) {
                Text(text = tr(Tr.COMPUTING_ELLIPSIS), style = WTypography.labelSmall.copy(color = WColor.muted))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        when {
            ui.craftCostState == MarketState.Error ->
                MessageCard(title = tr(Tr.CANT_REACH_MARKET_SERVER), hint = ui.error ?: tr(Tr.START_MARKET_SERVER_HINT))

            ui.craftCostResult != null ->
                CraftCostResultCard(
                    result = ui.craftCostResult,
                    itemInfo = ui.itemInfoCache,
                    lang = ui.lang,
                    onRequestItemInfo = onRequestItemInfo
                )

            else -> MessageCard(title = tr(Tr.NO_LOOKUP_YET_TITLE), hint = tr(Tr.NO_LOOKUP_YET_HINT))
        }
    }
}

@Composable
private fun CraftCostResultCard(
    result: me.chosante.marketclient.CraftCostResponse,
    itemInfo: Map<Int, ItemInfoResponse>,
    lang: Lang,
    onRequestItemInfo: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WDimens.radius))
                .background(WColor.surface)
                .border(1.dp, WColor.border, RoundedCornerShape(WDimens.radius))
                .padding(16.dp)
    ) {
        ItemBadge(itemId = result.itemId, item = itemInfo[result.itemId], lang = lang, onRequestItemInfo = onRequestItemInfo)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = tr(Tr.CRAFT_JOB_LABEL).format(result.jobName.localized(lang)), style = WTypography.labelSmall.copy(color = WColor.muted))
        Spacer(modifier = Modifier.height(10.dp))
        val decisionColor =
            when (result.decision) {
                "craft" -> WColor.success
                "buy" -> WColor.danger
                else -> WColor.muted
            }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = craftDecisionLabel(result.decision, lang),
                style = WTypography.headlineSmall.copy(color = decisionColor),
                fontWeight = FontWeight.Bold
            )
            InfoTip(text = tr(Tr.CRAFT_DECISION_HINT))
        }
        Spacer(modifier = Modifier.height(10.dp))
        StatLine(tr(Tr.CRAFT_COST_LABEL), result.craftCost?.toString() ?: "—", hint = tr(Tr.CRAFT_COST_HINT))
        StatLine(tr(Tr.MARKET_PRICE_LABEL), result.marketPrice?.toString() ?: "—", hint = tr(Tr.MARKET_PRICE_HINT))
        StatLine(tr(Tr.GROSS_MARGIN), result.grossMargin?.toString() ?: "—", hint = tr(Tr.GROSS_MARGIN_HINT))
        StatLine(tr(Tr.NET_MARGIN), result.netMargin?.toString() ?: "—", hint = tr(Tr.NET_MARGIN_HINT))
        StatLine(tr(Tr.ROI_LABEL), result.roi?.let { "${(it * 100).toInt()}%" } ?: "—", hint = tr(Tr.ROI_HINT))
        StatLine(
            tr(Tr.CONFIDENCE_LABEL),
            "${(result.confidence * 100).toInt()}%",
            hint = tr(Tr.CONFIDENCE_HINT)
        )
        if (result.missingPriceCount > 0) {
            StatLine(
                tr(Tr.MISSING_PRICES_LABEL),
                tr(Tr.MISSING_PRICES_VALUE).format(result.missingPriceCount),
                hint = tr(Tr.MISSING_PRICES_HINT)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Hairline()
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = tr(Tr.INGREDIENTS_TITLE), style = WTypography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        result.ingredients.forEach { ingredient ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ItemBadge(
                    itemId = ingredient.itemId,
                    item = itemInfo[ingredient.itemId],
                    lang = lang,
                    onRequestItemInfo = onRequestItemInfo,
                    modifier = Modifier.widthIn(min = 200.dp)
                )
                Text(
                    text = tr(Tr.INGREDIENT_QUANTITY_SUBTOTAL).format(ingredient.quantity, ingredient.subtotal?.toString() ?: tr(Tr.NO_PRICE_SHORT)),
                    style = WTypography.bodySmall.copy(color = WColor.muted)
                )
            }
        }
    }
}
