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
 * that share its [collectLootListId]. [collectItemId] is the id of the item this stage's harvest
 * actually yields -- DISTINCT from [resourceId] (the node's own id, e.g. "Iced Cranberry" the bush)
 * -- confirmed by a live CDN fetch: [resourceId] resolves through [ResourceNode.title] to the same
 * name as the harvested item (Wakfu names materials after their source node), which is how
 * `extractHarvestMaterials` gives every harvested material a real name/icon without any encyclopedia
 * scraping. Not every row necessarily carries one (default 0 = none).
 */
@Serializable
data class CollectibleResource(
    val id: Int,
    val resourceId: Int,
    val resourceIndex: Int,
    val skillLevelRequired: Int,
    val collectLootListId: Int,
    val collectItemId: Int = 0,
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
