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
 *
 * Same multi-stage disambiguation as [extractHarvestNodes] -- one node's several growth stages can
 * yield genuinely different [collectItemId]s, so the stage count is computed AFTER the
 * [distinctBy] dedup, per source node, not reused from [extractHarvestNodes]'s raw per-node stage
 * count (a different thing: that counts EVERY collectible row per node, this counts only the ones
 * that survived dedup to a distinct item). Skipping this step was a real regression this session:
 * 428/434 (98.6%) harvest materials shared an ambiguous bare node name (e.g. three different real
 * items all displaying as "Iced Cranberry") before this fix.
 *
 * [resourceIndex] alone doesn't ALWAYS disambiguate, though: confirmed against the live CDN feed,
 * two rows can share both [resourceId] and [resourceIndex] yet still yield different
 * [collectItemId]s (e.g. resourceId 763, resourceIndex 3 has both collectItemId 27851 and 27852 --
 * different `collectLootListId`/`visualFeedbackId`, likely an alternate harvest outcome at the same
 * stage rather than a distinct growth stage). Rather than model that extra dimension, any name that
 * still collides after stage-suffixing falls back to appending the item's own id -- guarantees no
 * two distinct items ever share a display name, by construction.
 */
fun extractHarvestMaterials(data: WakfuHarvestData): List<ItemSummary> {
    val nodesById = data.resourceNodes.associateBy { it.definition.id }
    val distinctCollectibles = data.collectibleResources.filter { it.collectItemId != 0 }.distinctBy { it.collectItemId }
    val stagesPerResource = distinctCollectibles.groupBy { it.resourceId }.mapValues { it.value.size }

    val provisional =
        distinctCollectibles.mapNotNull { collectible ->
            val node = nodesById[collectible.resourceId] ?: return@mapNotNull null
            val hasMultipleStages = (stagesPerResource[collectible.resourceId] ?: 1) > 1
            val name = if (hasMultipleStages) node.title.withStageSuffix(collectible.resourceIndex) else node.title
            Triple(collectible.collectItemId, name, node.definition.iconGfxId)
        }

    val nameCounts = provisional.groupingBy { it.second.en }.eachCount()
    return provisional.map { (itemId, name, iconKey) ->
        val finalName = if ((nameCounts[name.en] ?: 0) > 1) name.withIdSuffix(itemId) else name
        ItemSummary(itemId = itemId, name = finalName, level = 0, rarity = Rarity.COMMON, category = "harvest-material", iconKey = iconKey)
    }
}

private fun I18nText.withStageSuffix(stage: Int) =
    I18nText(
        fr = "$fr (palier $stage)",
        en = "$en (stage $stage)",
        es = "$es (etapa $stage)",
        pt = "$pt (estágio $stage)"
    )

private fun I18nText.withIdSuffix(id: Int) = I18nText(fr = "$fr #$id", en = "$en #$id", es = "$es #$id", pt = "$pt #$id")
