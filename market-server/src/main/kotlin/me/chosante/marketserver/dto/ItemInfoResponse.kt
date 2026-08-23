package me.chosante.marketserver.dto

import kotlinx.serialization.Serializable
import me.chosante.common.Characteristic
import me.chosante.common.I18nText
import me.chosante.common.ItemType
import me.chosante.common.Rarity
import me.chosante.common.Sublimation

/**
 * Unified item display info for the Market screen -- covers both [me.chosante.common.Equipment]
 * (the solver's full 14-slot gear catalog) and [me.chosante.common.ItemSummary] (resources/
 * consumables from the encyclopedia) behind one shape. [iconKey] indexes
 * `assets/items/<iconKey>.png` in gui-compose either way. [characteristics]/[itemType]/
 * [maxShardSlots] are equip-only -- empty/null for non-equipment items -- and exist so an item's
 * "view detail" surface can show what it actually does, not just name/level/rarity/price.
 */
@Serializable
data class ItemInfoResponse(
    val itemId: Int,
    val name: I18nText,
    val level: Int,
    val rarity: Rarity,
    val iconKey: Int,
    val category: String,
    val isEquipment: Boolean,
    val characteristics: Map<Characteristic, Int> = emptyMap(),
    val itemType: ItemType? = null,
    val maxShardSlots: Int? = null,
    /** Flavor text from the encyclopedia's per-item detail page (`items-extractor`). Null for
     * sources that don't crawl detail pages (equipment, sublimations, harvest materials) or when
     * the item genuinely has none. */
    val description: I18nText? = null,
    /** Set only for sublimation items -- the whole domain object, not just its display fields, so
     * the item-detail popup can render its real effect text via the GUI's existing
     * `sublimationEffectText(sub, lang)` (the same formatter the sublimation picker and paperdoll
     * tooltip already use) instead of showing nothing useful. */
    val sublimation: Sublimation? = null,
)

/**
 * One `/api/items/search` hit: the item itself plus its most recent price observation (if any),
 * embedded so the HDV-style browse list doesn't need a second round trip per row to show a price.
 * The four `latest*` fields are null together (no observation yet) or present together (they all
 * come from the same [me.chosante.marketserver.service.PriceObservationService.latestForItem] row).
 */
@Serializable
data class ItemSearchResult(
    val item: ItemInfoResponse,
    val latestMinPrice: Long? = null,
    val latestAvgPrice: Long? = null,
    val latestServer: String? = null,
    val latestObservedAt: String? = null,
)
