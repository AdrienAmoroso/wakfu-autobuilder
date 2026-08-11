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
import me.chosante.common.Equipment
import me.chosante.marketclient.CaptureStatusResponse
import me.chosante.marketclient.CreateObservationRequest
import me.chosante.marketclient.FlagMotif
import me.chosante.marketclient.ObservationResponse
import me.chosante.ui.components.Hairline
import me.chosante.ui.components.ItemThumbnail
import me.chosante.ui.components.RarityIcon
import me.chosante.ui.components.localized
import me.chosante.ui.i18n.Lang
import me.chosante.ui.state.MarketState
import me.chosante.ui.state.MarketTab
import me.chosante.ui.state.UiState
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WDimens
import me.chosante.ui.theme.WTypography
import java.time.LocalDateTime

@Composable
fun MarketScreen(
    ui: UiState,
    onSelectTab: (MarketTab) -> Unit,
    onItemIdFilterChange: (String) -> Unit,
    onLoadObservations: () -> Unit,
    onCreateObservation: (CreateObservationRequest) -> Unit,
    onDeleteObservation: (Int) -> Unit,
    onUpdatePrices: (Int, Long, Long, Long?) -> Unit,
    onSetFlag: (Int, FlagMotif) -> Unit,
    onCraftCostItemIdChange: (String) -> Unit,
    onLookupCraftCost: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onRequestItemInfo: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(WColor.bg)) {
        MarketTabHeader(current = ui.marketTab, onSelect = onSelectTab)
        Box(modifier = Modifier.fillMaxSize().padding(WDimens.pad)) {
            when (ui.marketTab) {
                MarketTab.PRICES ->
                    PricesTab(
                        ui = ui,
                        onItemIdFilterChange = onItemIdFilterChange,
                        onLoadObservations = onLoadObservations,
                        onCreateObservation = onCreateObservation,
                        onDeleteObservation = onDeleteObservation,
                        onUpdatePrices = onUpdatePrices,
                        onSetFlag = onSetFlag,
                        onStartCapture = onStartCapture,
                        onStopCapture = onStopCapture,
                        onRequestItemInfo = onRequestItemInfo
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
    onItemIdFilterChange: (String) -> Unit,
    onLoadObservations: () -> Unit,
    onCreateObservation: (CreateObservationRequest) -> Unit,
    onDeleteObservation: (Int) -> Unit,
    onUpdatePrices: (Int, Long, Long, Long?) -> Unit,
    onSetFlag: (Int, FlagMotif) -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onRequestItemInfo: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CaptureBar(status = ui.captureStatus, onStart = onStartCapture, onStop = onStopCapture)
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallTextField(
                value = ui.marketItemIdFilter,
                onValueChange = onItemIdFilterChange,
                placeholder = "Filter by itemId",
                modifier = Modifier.width(200.dp)
            )
            SmallButton(text = "Load", onClick = onLoadObservations, filled = true)
            if (ui.marketLoadState == MarketState.Loading) {
                Text(text = "Loading…", style = WTypography.labelSmall.copy(color = WColor.muted))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        NewObservationForm(itemIdFilter = ui.marketItemIdFilter, onCreate = onCreateObservation)
        Spacer(modifier = Modifier.height(14.dp))
        when {
            ui.marketLoadState == MarketState.Error ->
                MarketMessageCard(
                    title = "Can't reach market-server",
                    hint = ui.error ?: "Start it with ./gradlew :market-server:run"
                )

            ui.marketObservations.isEmpty() && ui.marketLoadState == MarketState.Ready ->
                MarketMessageCard(title = "No observations", hint = "Try loading without a filter, or create one above.")

            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.marketObservations, key = { it.id }) { observation ->
                        ObservationRow(
                            observation = observation,
                            equipment = ui.itemInfoCache[observation.itemId],
                            lang = ui.lang,
                            onRequestItemInfo = onRequestItemInfo,
                            onDelete = { onDeleteObservation(observation.id) },
                            onUpdatePrices = onUpdatePrices,
                            onSetFlag = onSetFlag
                        )
                    }
                }
        }
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

@Composable
private fun NewObservationForm(
    itemIdFilter: String,
    onCreate: (CreateObservationRequest) -> Unit,
) {
    var itemId by remember { mutableStateOf(itemIdFilter) }
    var server by remember { mutableStateOf("") }
    var minPrice by remember { mutableStateOf("") }
    var avgPrice by remember { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WDimens.radius))
                .background(WColor.surface)
                .border(1.dp, WColor.border, RoundedCornerShape(WDimens.radius))
                .padding(14.dp)
    ) {
        Text(text = "New observation", style = WTypography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallTextField(value = itemId, onValueChange = { itemId = it.filter(Char::isDigit) }, placeholder = "itemId", modifier = Modifier.width(100.dp))
            SmallTextField(value = server, onValueChange = { server = it }, placeholder = "server", modifier = Modifier.width(120.dp))
            SmallTextField(
                value = minPrice,
                onValueChange = { minPrice = it.filter(Char::isDigit) },
                placeholder = "min price",
                modifier = Modifier.width(110.dp)
            )
            SmallTextField(
                value = avgPrice,
                onValueChange = { avgPrice = it.filter(Char::isDigit) },
                placeholder = "avg price",
                modifier = Modifier.width(110.dp)
            )
            SmallButton(
                text = "Create",
                filled = true,
                onClick = {
                    val id = itemId.toIntOrNull() ?: return@SmallButton
                    val min = minPrice.toLongOrNull() ?: return@SmallButton
                    val avg = avgPrice.toLongOrNull() ?: min
                    onCreate(
                        CreateObservationRequest(
                            itemId = id,
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
}

@Composable
private fun ObservationRow(
    observation: ObservationResponse,
    equipment: Equipment?,
    lang: Lang,
    onRequestItemInfo: (Int) -> Unit,
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
                .background(WColor.surface)
                .border(1.dp, WColor.hairline, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ItemBadge(
            itemId = observation.itemId,
            equipment = equipment,
            lang = lang,
            onRequestItemInfo = onRequestItemInfo,
            modifier = Modifier.widthIn(min = 200.dp)
        )
        Text(text = observation.server.ifBlank { "—" }, style = WTypography.bodySmall.copy(color = WColor.muted), modifier = Modifier.widthIn(min = 70.dp))
        SmallTextField(value = minPriceText, onValueChange = { minPriceText = it.filter(Char::isDigit) }, placeholder = "min", modifier = Modifier.width(90.dp))
        SmallTextField(value = avgPriceText, onValueChange = { avgPriceText = it.filter(Char::isDigit) }, placeholder = "avg", modifier = Modifier.width(90.dp))
        SmallButton(
            text = "Save",
            onClick = {
                val min = minPriceText.toLongOrNull() ?: return@SmallButton
                val avg = avgPriceText.toLongOrNull() ?: return@SmallButton
                onUpdatePrices(observation.id, min, avg, observation.medianPrice)
            }
        )
        Text(
            text = observation.comment?.takeIf { it.isNotBlank() } ?: "",
            style = WTypography.bodySmall.copy(color = WColor.muted),
            modifier = Modifier.weight(1f)
        )
        Box {
            SmallButton(text = "Flag", onClick = { flagMenuOpen = true })
            DropdownMenu(expanded = flagMenuOpen, onDismissRequest = { flagMenuOpen = false }, containerColor = WColor.surface) {
                FlagMotif.entries.forEach { motif ->
                    DropdownMenuItem(
                        text = { Text(text = motif.name.lowercase(), style = WTypography.bodyMedium) },
                        onClick = {
                            onSetFlag(observation.id, motif)
                            flagMenuOpen = false
                        }
                    )
                }
            }
        }
        SmallButton(text = "Delete", onClick = onDelete)
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
    itemInfo: Map<Int, Equipment>,
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
        ItemBadge(itemId = result.itemId, equipment = itemInfo[result.itemId], lang = lang, onRequestItemInfo = onRequestItemInfo)
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
                    equipment = itemInfo[ingredient.itemId],
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
 * An item's icon/rarity/localized-name/level, resolved lazily from [equipment] (looked up by
 * `itemId` in [UiState.itemInfoCache]) -- the same visual language the build optimizer's paperdoll
 * uses ([ItemThumbnail]/[RarityIcon]), so a price/ingredient row reads as "this specific item," not
 * a bare id. Falls back to a plain "#itemId" placeholder tile while the lookup is in flight or the
 * id is unknown to market-server's catalog.
 */
@Composable
private fun ItemBadge(
    itemId: Int,
    equipment: Equipment?,
    lang: Lang,
    onRequestItemInfo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(itemId) { onRequestItemInfo(itemId) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (equipment != null) {
            ItemThumbnail(equipment = equipment, size = 32.dp)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RarityIcon(rarity = equipment.rarity, size = 12.dp)
                    Text(
                        text = equipment.name.localized(lang),
                        style = WTypography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(text = "Lvl ${equipment.level} · #$itemId", style = WTypography.labelSmall.copy(color = WColor.muted))
            }
        } else {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(WColor.bg))
            Text(text = "#$itemId", style = WTypography.bodyMedium.copy(color = WColor.muted))
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
