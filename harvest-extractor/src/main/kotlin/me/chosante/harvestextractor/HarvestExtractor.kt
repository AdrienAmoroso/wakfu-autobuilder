package me.chosante.harvestextractor

import me.chosante.common.HarvestDrop
import me.chosante.common.HarvestNode
import me.chosante.common.I18nText
import me.chosante.harvestextractor.dataretriever.WakfuHarvestData

/**
 * Joins the 4 CDN files into [HarvestNode]s -- see this module's research trail (no direct field
 * connects a resource node to its loot table; the link is indirect via `collectibleResources.json`):
 * `collectibleResources.resourceId` -> `resources.json` node, `collectLootListId` ->
 * `harvestLoots.listId` rows.
 *
 * One `resources.json` node can have several `collectibleResources` rows (growth stages, via
 * `resourceIndex`) -- each becomes its own [HarvestNode], suffixed with its stage number when a
 * node has more than one, so two stages of the same tree don't collide under one ambiguous name.
 */
fun extractHarvestNodes(data: WakfuHarvestData): List<HarvestNode> {
    val nodesById = data.resourceNodes.associateBy { it.definition.id }
    val categoryById = data.resourceTypes.associate { it.definition.id to it.title.en }
    val lootsByListId = data.harvestLoots.groupBy { it.listId }
    val stagesPerResource = data.collectibleResources.groupBy { it.resourceId }.mapValues { it.value.size }

    return data.collectibleResources.mapNotNull { collectible ->
        val node = nodesById[collectible.resourceId] ?: return@mapNotNull null
        val drops =
            lootsByListId[collectible.collectLootListId]
                ?.map { HarvestDrop(itemId = it.itemId, dropRate = it.dropRate, quantity = it.quantityPerItem) }
                ?: emptyList()
        if (drops.isEmpty()) return@mapNotNull null

        val hasMultipleStages = (stagesPerResource[collectible.resourceId] ?: 1) > 1
        val name = if (hasMultipleStages) node.title.withStageSuffix(collectible.resourceIndex) else node.title

        HarvestNode(
            resourceId = collectible.id,
            name = name,
            category = categoryById[node.definition.resourceType] ?: "unknown",
            skillLevelRequired = collectible.skillLevelRequired,
            iconKey = node.definition.iconGfxId,
            drops = drops
        )
    }
}

private fun I18nText.withStageSuffix(stage: Int) =
    I18nText(
        fr = "$fr (palier $stage)",
        en = "$en (stage $stage)",
        es = "$es (etapa $stage)",
        pt = "$pt (estágio $stage)"
    )
