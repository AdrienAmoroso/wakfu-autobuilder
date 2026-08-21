package me.chosante.common

import kotlinx.serialization.Serializable

/**
 * One possible drop from a [HarvestNode] (or, reused identically, a monster kill -- see
 * `MonsterLoot`): [dropRate] is an independent 0-1 probability (not a weight normalized within the
 * node/monster's loot table -- rows are evaluated separately, so their sum isn't meaningful), and
 * [quantity] is how many of [itemId] that row grants when it hits.
 */
@Serializable
data class HarvestDrop(
    val itemId: Int,
    val dropRate: Double,
    val quantity: Int,
)

/**
 * A harvestable resource node (e.g. "Api Tree"), sourced from Ankama's CDN by `harvest-extractor`
 * (`resources.json` + `collectibleResources.json` + `harvestLoots.json` + `resourceTypes.json` --
 * none of which market-server's existing catalogs cover; see that module's research trail). One
 * `resources.json` node can have several growth-stage entries in `collectibleResources.json`; each
 * becomes its own [HarvestNode] here (disambiguated in [name] when a node has more than one stage).
 */
@Serializable
data class HarvestNode(
    val resourceId: Int,
    val name: I18nText,
    val category: String,
    val skillLevelRequired: Int,
    val iconKey: Int,
    val drops: List<HarvestDrop>,
)
