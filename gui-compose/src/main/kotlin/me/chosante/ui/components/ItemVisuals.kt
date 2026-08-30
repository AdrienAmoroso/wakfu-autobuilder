package me.chosante.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.chosante.common.Characteristic
import me.chosante.common.Equipment
import me.chosante.common.Rarity
import me.chosante.marketclient.ItemInfoResponse
import me.chosante.marketclient.ItemSourcesResponse
import me.chosante.ui.i18n.Lang
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.label
import me.chosante.ui.i18n.tr
import me.chosante.ui.state.statColor
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WType
import me.chosante.ui.theme.WTypography
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Classpath path of an item's icon, falling back to a placeholder when the asset is missing. */
internal fun Equipment.itemResourcePath(): String = itemResourcePathFor(guiId)

/**
 * Classpath path of an item's icon keyed directly by its icon id -- [Equipment.guiId] for gear,
 * or [me.chosante.common.ItemSummary.iconKey] (the itemId itself) for encyclopedia-sourced
 * resources/consumables, since both kinds' PNGs live in the same `assets/items/<key>.png`
 * directory (see `ItemSummary`'s doc comment for why).
 */
internal fun itemResourcePathFor(iconKey: Int): String {
    val path = "assets/items/$iconKey.png"
    val loader = Thread.currentThread().contextClassLoader
    return if (loader.getResource(path) != null) path else "assets/items/0000000.png"
}

private fun Rarity.iconResourcePath(): String = "assets/rarities/${name.lowercase()}.png"

/**
 * Every icon path worth pre-decoding for [equipments]: the rarity badges, the stat/skill icons,
 * and one icon per item.
 */
internal fun warmUpPaths(equipments: List<Equipment>): List<String> =
    Rarity.entries.map { it.iconResourcePath() } +
        statIconWarmUpPaths() +
        equipments
            .asSequence()
            .map { "assets/items/${it.guiId}.png" }
            .distinct()
            .toList()

private val bitmapCache = ConcurrentHashMap<String, ImageBitmap>()
private val decodeLock = Any()

/**
 * Decodes a classpath PNG into an [ImageBitmap], caching the result so each asset is read once.
 *
 * Unlike `painterResource`, this never re-reads the underlying jar entry on recomposition. All
 * reads are serialized through [decodeLock]: the JDK throws `ZipException: invalid LOC header`
 * when several threads read the same jar entry concurrently, and serializing the reads removes
 * that race entirely (cache hits, the common case, never take the lock). Any decode failure
 * falls back to `null` so the caller can show a placeholder instead of crashing the UI thread.
 */
internal fun loadClasspathBitmap(path: String): ImageBitmap? {
    bitmapCache[path]?.let { return it }
    synchronized(decodeLock) {
        bitmapCache[path]?.let { return it }
        val loader = Thread.currentThread().contextClassLoader
        return try {
            loader.getResourceAsStream(path)?.use { stream ->
                stream.readAllBytes().decodeToImageBitmap().also { bitmapCache[path] = it }
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Remembers the decoded [ImageBitmap] for [path], or `null` if it is missing/unreadable. */
@Composable
internal fun rememberClasspathBitmap(path: String): ImageBitmap? = remember(path) { loadClasspathBitmap(path) }

/**
 * Warms [bitmapCache] in the background so item icons are ready (and decoded once, off the UI
 * thread) by the time a build is shown. Progress is reported through [onProgress] — the caller is
 * responsible for marshalling those values onto the UI thread before touching Compose state.
 * Idempotent: only the first [warmUp] call does work.
 */
object IconPreloader {
    private val started = AtomicBoolean(false)

    /** @param onProgress invoked from a background thread with `(loaded, total)`. */
    fun warmUp(
        scope: CoroutineScope,
        paths: List<String>,
        onProgress: (loaded: Int, total: Int) -> Unit,
    ) {
        if (paths.isEmpty() || !started.compareAndSet(false, true)) {
            return
        }
        val total = paths.size
        scope.launch(Dispatchers.Default) {
            paths.forEachIndexed { index, path ->
                loadClasspathBitmap(path)
                // Throttle progress reports so we don't recompose once per icon.
                if ((index + 1) % 48 == 0 || index == paths.lastIndex) {
                    onProgress(index + 1, total)
                }
            }
        }
    }
}

/** The official Wakfu rarity badge (Common…Epic). */
@Composable
internal fun RarityIcon(
    rarity: Rarity,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    val bitmap = rememberClasspathBitmap(rarity.iconResourcePath())
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = rarity.name,
            modifier = modifier.size(size)
        )
    } else {
        Box(modifier.size(size))
    }
}

/** An item's icon thumbnail in a rounded tile. */
@Composable
internal fun ItemThumbnail(
    equipment: Equipment,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) = ItemThumbnail(iconKey = equipment.guiId, modifier = modifier, size = size)

/** An item's icon thumbnail in a rounded tile, keyed directly by icon id -- see [itemResourcePathFor]. */
@Composable
internal fun ItemThumbnail(
    iconKey: Int,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(WColor.bg),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        val bitmap = rememberClasspathBitmap(itemResourcePathFor(iconKey))
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size).padding(3.dp)
            )
        }
    }
}

/**
 * A passive's icon — the spell sprite at `assets/spells/<gfxId>.png` (extracted from the local client's
 * gui.jar by generateAssets). Renders nothing when
 * the asset is absent. Shared by the passive picker row and the result card so the path/sizing live in one
 * place.
 */
@Composable
fun PassiveIcon(
    gfxId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    rememberClasspathBitmap("assets/spells/$gfxId.png")?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(RoundedCornerShape(5.dp))
        )
    }
}

/**
 * An item's icon/rarity/localized-name/level, resolved lazily from [item] (looked up by `itemId` in
 * `UiState.itemInfoCache`) -- shared by the Market screen's Craft Cost tab and the Kamas screen's
 * opportunity lists, wherever an item isn't already part of a richer row that resolved it another
 * way. Falls back to a plain "#itemId" tile while the lookup is in flight or the id is genuinely
 * outside the catalog (a category items-extractor doesn't cover yet).
 */
@Composable
internal fun ItemBadge(
    itemId: Int,
    item: ItemInfoResponse?,
    lang: Lang,
    onRequestItemInfo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sources: ItemSourcesResponse? = null,
    onRequestSources: (Int) -> Unit = {},
) {
    LaunchedEffect(itemId) { onRequestItemInfo(itemId) }
    if (item != null) {
        ItemInfoBadge(item = item, lang = lang, modifier = modifier, sources = sources, onRequestSources = onRequestSources)
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(WColor.bg))
            Text(text = "Unknown item · #$itemId", style = WTypography.bodyMedium.copy(color = WColor.muted))
        }
    }
}

/**
 * Renders an already-resolved [ItemInfoResponse]: icon, rarity badge, localized name, and level.
 * Clicking it opens a popup with everything else the app knows about the item — full stat list,
 * slot type, rune-socket count — so any place an item badge shows up (Market's Prices/Craft Cost
 * tabs, Kamas's ingredient/drop rows) gets a "view detail" affordance for free.
 */
@Composable
internal fun ItemInfoBadge(
    item: ItemInfoResponse,
    lang: Lang,
    modifier: Modifier = Modifier,
    sources: ItemSourcesResponse? = null,
    onRequestSources: (Int) -> Unit = {},
) {
    var detailsOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.clickable { detailsOpen = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
        DropdownMenu(expanded = detailsOpen, onDismissRequest = { detailsOpen = false }, containerColor = WColor.surface) {
            ItemDetailContent(item = item, lang = lang, sources = sources, onRequestSources = onRequestSources)
        }
    }
}

/** The full "what does this item actually do" panel opened from [ItemInfoBadge] -- including, once
 * [onRequestSources] resolves [sources], a compact "how do I get this" section (recipe / monster
 * drops / harvest-node drops). This is the minimal surfacing of the item-sources foundation; a full
 * build-explanation page reusing the same [ItemSourcesResponse] data is future work. */
@Composable
private fun ItemDetailContent(
    item: ItemInfoResponse,
    lang: Lang,
    sources: ItemSourcesResponse? = null,
    onRequestSources: (Int) -> Unit = {},
) {
    LaunchedEffect(item.itemId) { onRequestSources(item.itemId) }
    Column(
        modifier = Modifier.widthIn(min = 200.dp, max = 300.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item.description?.localized(lang)?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = WTypography.bodySmall.copy(color = WColor.muted, fontStyle = FontStyle.Italic)
            )
        }
        item.itemType?.let { itemType ->
            Text(text = itemType.label(lang), style = WTypography.labelSmall.copy(color = WColor.muted))
        }
        item.maxShardSlots?.takeIf { it > 0 }?.let { sockets ->
            Text(text = "${tr(Tr.RUNE_SOCKETS_LABEL)}: $sockets", style = WTypography.labelSmall.copy(color = WColor.muted))
        }
        item.sublimation?.let { sub ->
            Text(text = sublimationEffectText(sub, lang), style = WTypography.bodySmall.copy(color = WColor.muted))
        }
        if (item.characteristics.isEmpty() && item.sublimation == null) {
            Text(text = tr(Tr.NO_ITEM_STATS), style = WTypography.labelSmall.copy(color = WColor.faint))
        } else {
            item.characteristics.entries.sortedBy { it.key.ordinal }.forEach { (characteristic, value) ->
                ItemStatRow(characteristic = characteristic, value = value, lang = lang)
            }
        }
        sources?.let { ItemSourcesSection(sources = it, lang = lang) }
    }
}

/** "How do I get this item" -- recipe / monster drops / harvest-node drops, whichever apply (an
 * item can have several at once, or none, which renders nothing at all rather than an empty title). */
@Composable
private fun ItemSourcesSection(
    sources: ItemSourcesResponse,
    lang: Lang,
) {
    if (sources.recipe == null && sources.monsterSources.isEmpty() && sources.harvestSources.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = tr(Tr.ITEM_SOURCES_TITLE), style = WTypography.labelSmall.copy(color = WColor.muted, fontWeight = FontWeight.SemiBold))
        sources.recipe?.let { recipe ->
            Text(
                text = tr(Tr.ITEM_SOURCES_RECIPE).format(recipe.jobName.localized(lang), recipe.ingredients.size),
                style = WTypography.labelSmall.copy(color = WColor.muted)
            )
        }
        if (sources.monsterSources.isNotEmpty()) {
            Text(
                text = tr(Tr.ITEM_SOURCES_MONSTERS).format(namesWithMore(sources.monsterSources.map { it.name.localized(lang) })),
                style = WTypography.labelSmall.copy(color = WColor.muted)
            )
        }
        if (sources.harvestSources.isNotEmpty()) {
            Text(
                text = tr(Tr.ITEM_SOURCES_NODES).format(namesWithMore(sources.harvestSources.map { it.name.localized(lang) })),
                style = WTypography.labelSmall.copy(color = WColor.muted)
            )
        }
    }
}

private const val MAX_SOURCE_NAMES_SHOWN = 5

/** Joins up to [MAX_SOURCE_NAMES_SHOWN] names, collapsing the rest into a localized "+N more". */
@Composable
private fun namesWithMore(names: List<String>): String =
    if (names.size <= MAX_SOURCE_NAMES_SHOWN) {
        names.joinToString(", ")
    } else {
        names.take(MAX_SOURCE_NAMES_SHOWN).joinToString(", ") + " " + tr(Tr.ITEM_SOURCES_MORE).format(names.size - MAX_SOURCE_NAMES_SHOWN)
    }

/** One characteristic's label+value row inside [ItemDetailContent] — mirrors the paperdoll's own
 * tooltip row (`PaperdollPanel.TooltipStatRow`) so an item's stats look the same everywhere they're
 * shown, without either screen depending on the other's private composables. */
@Composable
private fun ItemStatRow(
    characteristic: Characteristic,
    value: Int,
    lang: Lang,
) {
    val color = characteristic.statColor()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        val iconBitmap = characteristic.iconResourcePath()?.let { rememberClasspathBitmap(it) }
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(color))
        }
        Text(
            text = if (value > 0) "+$value" else value.toString(),
            style =
                WTypography.bodySmall.copy(
                    fontFamily = WType.mono,
                    fontWeight = FontWeight.SemiBold,
                    color = if (value < 0) WColor.danger else color
                )
        )
        Text(
            text = characteristic.label(lang),
            style = WTypography.bodySmall.copy(color = WColor.muted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
