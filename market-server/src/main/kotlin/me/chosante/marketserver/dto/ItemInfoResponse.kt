package me.chosante.marketserver.dto

import kotlinx.serialization.Serializable
import me.chosante.common.I18nText
import me.chosante.common.Rarity

/**
 * Unified item display info for the Market screen -- covers both [me.chosante.common.Equipment]
 * (the solver's full 14-slot gear catalog) and [me.chosante.common.ItemSummary] (resources/
 * consumables from the encyclopedia) behind one shape, since the GUI only ever needs
 * name/level/rarity/icon to render an item badge, never the full equip-only fields (characteristics,
 * itemType, shard slots). [iconKey] indexes `assets/items/<iconKey>.png` in gui-compose either way.
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
