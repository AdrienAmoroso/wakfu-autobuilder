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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import me.chosante.ui.components.ItemThumbnail
import me.chosante.ui.components.RarityIcon
import me.chosante.ui.components.localized
import me.chosante.ui.i18n.Lang
import me.chosante.ui.i18n.label
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
    onToggleExpandedItem: (Int) -> Unit,
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
                        onToggleExpandedItem = onToggleExpandedItem
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
                MarketTabButton(label = "Prices", selected = current == MarketTab.PRICES) { onSelect(MarketTab.PRICES) }
                MarketTabButton(label = "Craft Cost", selected = current == MarketTab.CRAFT_COST) { onSelect(MarketTab.CRAFT_COST) }
            }
        }
        Hairline()
    }
}

@Composable
private fun MarketTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) WColor.raised else WColor.bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, style = WTypography.labelMedium.copy(color = if (selected) WColor.text else WColor.muted))
    }
}

@Composable
private fun SmallButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (filled) WColor.accent else WColor.raised)
                .border(1.dp, if (filled) WColor.accent else WColor.border, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = text, style = WTypography.labelMedium.copy(color = if (filled) WColor.bg else WColor.text))
    }
}

@Composable
private fun SmallTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WColor.surface)
                .border(1.dp, WColor.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(WColor.accent),
            textStyle = WTypography.bodyMedium.copy(color = WColor.text),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) {
            Text(text = placeholder, style = WTypography.bodyMedium.copy(color = WColor.faint))
        }
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
    onToggleExpandedItem: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CaptureBar(status = ui.captureStatus, onStart = onStartCapture, onStop = onStopCapture)
        Spacer(modifier = Modifier.height(14.dp))
        MarketSearchBar(
            query = ui.marketSearchQuery,
            minLevel = ui.marketMinLevel,
            maxLevel = ui.marketMaxLevel,
            selectedRarities = ui.marketRarityFilter,
            lang = ui.lang,
            onQueryChange = onSearchQueryChange,
            onMinLevelChange = onMinLevelChange,
            onMaxLevelChange = onMaxLevelChange,
            onToggleRarity = onToggleRarityFilter
        )
        Spacer(modifier = Modifier.height(14.dp))
        when {
            // A background refresh (e.g. after saving a price) failing must never blank out an
            // already-good list -- prefer showing what's still on screen over an error card, even
            // if marketSearchState flipped to Error in the meantime.
            ui.marketSearchResults.isNotEmpty() ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.marketSearchResults, key = { it.item.itemId }) { result ->
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
                MarketMessageCard(
                    title = "Can't reach market-server",
                    hint = ui.error ?: "Start it with ./gradlew :market-server:run"
                )

            ui.marketSearchState == MarketState.Ready ->
                MarketMessageCard(title = "No items match", hint = "Try a broader name, level range, or fewer rarity filters.")
        }
    }
}

@Composable
private fun MarketSearchBar(
    query: String,
    minLevel: String,
    maxLevel: String,
    selectedRarities: Set<Rarity>,
    lang: Lang,
    onQueryChange: (String) -> Unit,
    onMinLevelChange: (String) -> Unit,
    onMaxLevelChange: (String) -> Unit,
    onToggleRarity: (Rarity) -> Unit,
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
            SmallTextField(value = query, onValueChange = onQueryChange, placeholder = "Search by name…", modifier = Modifier.weight(1f))
            SmallTextField(value = minLevel, onValueChange = onMinLevelChange, placeholder = "Min lvl", modifier = Modifier.width(90.dp))
            SmallTextField(value = maxLevel, onValueChange = onMaxLevelChange, placeholder = "Max lvl", modifier = Modifier.width(90.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Rarity.entries.forEach { rarity ->
                RarityChip(rarity = rarity, lang = lang, selected = rarity in selectedRarities, onClick = { onToggleRarity(rarity) })
            }
        }
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
            "capturing" -> SmallButton(text = "Stop Capture", onClick = onStop, filled = true)
            "processing" -> Text(text = "Processing…", style = WTypography.labelMedium.copy(color = WColor.muted))
            else -> SmallButton(text = "Start Capture", onClick = onStart, filled = true)
        }
        val statusText =
            when (phase) {
                "capturing" -> "Capturing — started ${elapsedLabel(status?.startedAt)} ago"
                "processing" -> "Parsing and importing captured prices…"
                "error" -> "Error: ${status?.message ?: "unknown"}"
                else -> status?.lastImportedCount?.let { "Idle — last capture imported $it price(s)" } ?: "Idle"
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
private fun observedAgoLabel(observedAt: String): String {
    val parsed =
        runCatching { LocalDateTime.parse(observedAt) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(observedAt).toLocalDateTime() }.getOrNull()
            ?: runCatching { Instant.parse(observedAt).atZone(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
            ?: return observedAt
    val minutes = Duration.between(parsed, LocalDateTime.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}

@Composable
private fun MarketMessageCard(
    title: String,
    hint: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WDimens.radius))
                .background(WColor.surface)
                .border(1.dp, WColor.hairline, RoundedCornerShape(WDimens.radius))
                .padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = WTypography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = hint, style = WTypography.bodySmall.copy(color = WColor.muted))
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
            Text(text = "No price captured yet", style = WTypography.bodySmall.copy(color = WColor.faint))
        } else {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Lowest ${result.latestMinPrice} · Average ${result.latestAvgPrice}",
                        style = WTypography.bodyMedium
                    )
                    InfoTip(
                        text =
                            "From the most recent HDV capture for this item: \"Lowest\" is the cheapest listing seen, " +
                                "\"Average\" is the mean of every listing seen in that same capture."
                    )
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
            Text(text = "Price history", style = WTypography.titleMedium)
            InfoTip(text = "Every capture session for this item, most recent first. Edit a row's numbers and hit Save to correct a misread price.")
            if (loading) Text(text = "Loading…", style = WTypography.labelSmall.copy(color = WColor.muted))
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (observations.isEmpty() && !loading) {
            Text(text = "No observations yet for this item.", style = WTypography.bodySmall.copy(color = WColor.muted))
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
        Text(text = "Add a price by hand:", style = WTypography.bodySmall.copy(color = WColor.muted))
        SmallTextField(value = server, onValueChange = { server = it }, placeholder = "server", modifier = Modifier.width(120.dp))
        SmallTextField(
            value = minPrice,
            onValueChange = { minPrice = it.filter(Char::isDigit) },
            placeholder = "lowest price",
            modifier = Modifier.width(110.dp)
        )
        SmallTextField(
            value = avgPrice,
            onValueChange = { avgPrice = it.filter(Char::isDigit) },
            placeholder = "average price",
            modifier = Modifier.width(120.dp)
        )
        SmallButton(
            text = "Add",
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
        LabeledField(label = "Lowest", value = minPriceText, onValueChange = { minPriceText = it.filter(Char::isDigit) })
        LabeledField(label = "Average", value = avgPriceText, onValueChange = { avgPriceText = it.filter(Char::isDigit) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallButton(
                text = "Save",
                onClick = {
                    val min = minPriceText.toLongOrNull() ?: return@SmallButton
                    val avg = avgPriceText.toLongOrNull() ?: return@SmallButton
                    onUpdatePrices(observation.id, min, avg, observation.medianPrice)
                }
            )
            InfoTip(text = "Corrects this row's prices, e.g. if the capture misread a digit. The original comment is kept, marked [corrected_manually].")
        }
        Text(
            text = observation.comment?.takeIf { it.isNotBlank() } ?: "",
            style = WTypography.bodySmall.copy(color = WColor.muted),
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                SmallButton(text = "Flag", onClick = { flagMenuOpen = true })
                DropdownMenu(expanded = flagMenuOpen, onDismissRequest = { flagMenuOpen = false }, containerColor = WColor.surface) {
                    flagMotifOptions.forEach { (motif, label, explanation) ->
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
            InfoTip(text = "Tags this row for a data-quality reviewer -- e.g. \"this price looks wrong.\" Replaces its comment with the flag reason.")
        }
        SmallButton(text = "Delete", onClick = onDelete)
    }
}

private val flagMotifOptions =
    listOf(
        Triple(FlagMotif.PARSING_ERROR, "Parsing error", "The capture misread this price"),
        Triple(FlagMotif.OUTLIER, "Outlier", "Looks abnormally high or low"),
        Triple(FlagMotif.DUPLICATE, "Duplicate", "Same listing captured twice"),
        Triple(FlagMotif.MANUAL_CHECK, "Needs review", "Flag for manual verification")
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
                placeholder = "itemId to craft",
                modifier = Modifier.width(200.dp)
            )
            SmallButton(text = "Lookup", onClick = onLookupCraftCost, filled = true)
            if (ui.craftCostState == MarketState.Loading) {
                Text(text = "Computing…", style = WTypography.labelSmall.copy(color = WColor.muted))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        when {
            ui.craftCostState == MarketState.Error ->
                MarketMessageCard(title = "Can't reach market-server", hint = ui.error ?: "Start it with ./gradlew :market-server:run")

            ui.craftCostResult != null ->
                CraftCostResultCard(
                    result = ui.craftCostResult,
                    itemInfo = ui.itemInfoCache,
                    lang = ui.lang,
                    onRequestItemInfo = onRequestItemInfo
                )

            else -> MarketMessageCard(title = "No lookup yet", hint = "Enter a craftable item's id and hit Lookup.")
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
        Spacer(modifier = Modifier.height(10.dp))
        val decisionColor =
            when (result.decision) {
                "craft" -> WColor.success
                "buy" -> WColor.danger
                else -> WColor.muted
            }
        Text(text = result.decision.uppercase(), style = WTypography.headlineSmall.copy(color = decisionColor), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        StatLine("Craft cost", result.craftCost?.toString() ?: "—")
        StatLine("Market price", result.marketPrice?.toString() ?: "—")
        StatLine("Gross margin", result.grossMargin?.toString() ?: "—")
        StatLine("Net margin", result.netMargin?.toString() ?: "—")
        StatLine("ROI", result.roi?.let { "${(it * 100).toInt()}%" } ?: "—")
        StatLine("Confidence", "${(result.confidence * 100).toInt()}%")
        if (result.missingPriceCount > 0) {
            StatLine("Missing prices", "${result.missingPriceCount} ingredient(s)")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Hairline()
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "Ingredients", style = WTypography.titleMedium)
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
                    text = "× ${ingredient.quantity} — ${ingredient.subtotal?.toString() ?: "no price"}",
                    style = WTypography.bodySmall.copy(color = WColor.muted)
                )
            }
        }
    }
}

/**
 * An item's icon/rarity/localized-name/level, resolved lazily from [item] (looked up by `itemId` in
 * [UiState.itemInfoCache]) -- used where the item isn't already part of a search result (the Craft
 * Cost tab, whose ingredients aren't a browse list). Falls back to a plain "#itemId" tile while the
 * lookup is in flight or the id is genuinely outside the catalog (a category items-extractor
 * doesn't cover yet).
 */
@Composable
private fun ItemBadge(
    itemId: Int,
    item: ItemInfoResponse?,
    lang: Lang,
    onRequestItemInfo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(itemId) { onRequestItemInfo(itemId) }
    if (item != null) {
        ItemInfoBadge(item = item, lang = lang, modifier = modifier)
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(WColor.bg))
            Text(text = "Unknown item · #$itemId", style = WTypography.bodyMedium.copy(color = WColor.muted))
        }
    }
}

/** Renders an already-resolved [ItemInfoResponse]: icon, rarity badge, localized name, and level. */
@Composable
private fun ItemInfoBadge(
    item: ItemInfoResponse,
    lang: Lang,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemThumbnail(iconKey = item.iconKey, size = 32.dp)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RarityIcon(rarity = item.rarity, size = 12.dp)
                Text(
                    text = item.name.localized(lang),
                    style = WTypography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(text = "Lvl ${item.level} · #${item.itemId}", style = WTypography.labelSmall.copy(color = WColor.muted))
        }
    }
}

@Composable
private fun StatLine(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = WTypography.bodyMedium.copy(color = WColor.muted))
        Text(text = value, style = WTypography.bodyMedium)
    }
}
