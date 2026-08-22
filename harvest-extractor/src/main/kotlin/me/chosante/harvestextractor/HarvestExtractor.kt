package me.chosante.harvestextractor

import me.chosante.common.HarvestDrop
import me.chosante.common.HarvestNode
import me.chosante.common.I18nText
import me.chosante.common.ItemSummary
import me.chosante.common.Rarity
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

/**
 * The item catalog's "Unknown item" gap was overwhelmingly harvest-node drop materials (445/452
 * ids, 98.5% -- see the research behind this function): almost every raw harvestable (ores, wood,
 * fish, fruit…) had no name/icon anywhere, because it's neither in the CDN equip feed (`items.json`,
 * confirmed absent by a live fetch) nor in `items-extractor`'s encyclopedia crawl. This closes that
 * gap using data `harvest-extractor` already has: dedupe [WakfuHarvestData.collectibleResources] by
 * [me.chosante.harvestextractor.dataretriever.dtos.CollectibleResource.collectItemId] (the item id,
 * distinct from the node id), then borrow the source node's name/icon for it (see
 * [CollectibleResource]'s doc comment for why that's a valid substitute name, not a guess).
 *
 * [level] and [rarity] aren't present in this data (`resources.json` has no such fields) --
 * defaulted honestly rather than fabricated: `level = 0` (unknown, not "level-less" the way a
 * sublimation's 0 is) and `rarity = COMMON` (a safe inference here, unlike sublimations which
 * genuinely span every tier: raw harvestable materials are near-universally Common tier by Wakfu's
 * own conventions).
 */
fun extractHarvestMaterials(data: WakfuHarvestData): List<ItemSummary> {
    val nodesById = data.resourceNodes.associateBy { it.definition.id }
    return data.collectibleResources
        .filter { it.collectItemId != 0 }
        .distinctBy { it.collectItemId }
        .mapNotNull { collectible ->
            val node = nodesById[collectible.resourceId] ?: return@mapNotNull null
            ItemSummary(
                itemId = collectible.collectItemId,
                name = node.title,
                level = 0,
                rarity = Rarity.COMMON,
                category = "harvest-material",
                iconKey = node.definition.iconGfxId
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
