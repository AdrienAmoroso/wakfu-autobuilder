package me.chosante.harvestextractor.dataretriever.dtos

import kotlinx.serialization.Serializable
import me.chosante.common.I18nText

/** `resources.json` -- a harvestable NODE (e.g. "Api Tree"), keyed by [Definition.id]. */
@Serializable
data class ResourceNode(
    val definition: Definition,
    val title: I18nText,
) {
    @Serializable
    data class Definition(
        val id: Int,
        val resourceType: Int,
        val iconGfxId: Int,
    )
}

/** `resourceTypes.json` -- the node category (Trees, Crops, Ores, …), keyed by [Definition.id]. */
@Serializable
data class ResourceTypeDef(
    val definition: Definition,
    val title: I18nText,
) {
    @Serializable
    data class Definition(
        val id: Int,
    )
}

/**
 * `collectibleResources.json` -- the missing link between a [ResourceNode] and its loot table: one
 * row per harvestable growth stage of a node ([resourceIndex]), pointing at the [HarvestLoot] rows
 * that share its [collectLootListId].
 */
@Serializable
data class CollectibleResource(
    val id: Int,
    val resourceId: Int,
    val resourceIndex: Int,
    val skillLevelRequired: Int,
    val collectLootListId: Int,
)

/** `harvestLoots.json` -- one possible drop; several rows share a [listId] (one node stage's loot table). */
@Serializable
data class HarvestLootRow(
    val id: Int,
    val itemId: Int,
    val dropRate: Double,
    val listId: Int,
    val quantityPerItem: Int,
)
