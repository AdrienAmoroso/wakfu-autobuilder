package me.chosante.common

import kotlinx.serialization.Serializable

/**
 * A monster's drop table, sourced from Ankama's encyclopedia by `items-extractor` (its detail
 * page's drops section -- no CDN file covers monster drops at all, confirmed dead end; see
 * `items-extractor`'s research trail). Deliberately carries nothing but [monsterId] and [drops]:
 * name/level/icon for display come from the already-committed `monsters.json` (bdata-extractor) via
 * a join at read time, rather than being re-scraped and duplicated here.
 */
@Serializable
data class MonsterLoot(
    val monsterId: Int,
    val drops: List<HarvestDrop>,
)
