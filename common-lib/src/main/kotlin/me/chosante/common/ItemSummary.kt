package me.chosante.common

import kotlinx.serialization.Serializable

/**
 * Minimal display info for an item that has no place in [Equipment] -- a resource, consumable,
 * cosmetic, or misc item that carries no combat characteristics, no equip slot, and isn't in
 * Ankama's CDN `items.json` (that feed only covers equip-related items: gear, runes, sublimations,
 * pets/mounts/emblems). Sourced instead from Ankama's public encyclopedia by `items-extractor`.
 * [category] is one of `resource`/`consumable`/`customization`/`miscellaneous` (see that module's
 * `CATEGORIES` list) -- most cosmetics and misc items never actually trade on the HDV, but are still
 * cataloged here for the Kamas/Market screens' category filter to work against the full item space.
 *
 * [iconKey] is the itemId itself: `items-extractor` downloads each item's hosted icon directly into
 * `gui-compose/src/main/resources/assets/items/<iconKey>.png` -- the same directory (and the same
 * `assets/items/<key>.png` lookup convention) [Equipment.guiId] icons already live in, so a market
 * item badge renders both kinds of item through one lookup path with no branching.
 */
@Serializable
data class ItemSummary(
    val itemId: Int,
    val name: I18nText,
    val level: Int,
    val rarity: Rarity,
    val category: String,
    val iconKey: Int,
)
