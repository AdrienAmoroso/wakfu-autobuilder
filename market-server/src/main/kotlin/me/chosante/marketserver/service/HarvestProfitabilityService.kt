package me.chosante.marketserver.service

import me.chosante.marketserver.dto.HarvestOpportunity
import me.chosante.marketserver.equipment.HarvestCatalog
import org.jetbrains.exposed.v1.jdbc.Database

object HarvestProfitabilityService {
    /**
     * Ranks every harvest node by expected kamas per harvest -- the Kamas screen's Harvesting tab.
     * One batched price lookup across every node's drops (not one query per drop per node).
     */
    fun scanAll(
        db: Database,
        server: String?,
        minSkillLevel: Int?,
        limit: Int,
    ): List<HarvestOpportunity> {
        val nodes = if (minSkillLevel == null) HarvestCatalog.all else HarvestCatalog.all.filter { it.skillLevelRequired >= minSkillLevel }
        val neededIds = nodes.flatMapTo(mutableSetOf()) { node -> node.drops.map { it.itemId } }
        val prices = PriceObservationService.latestForItems(db, neededIds, server)

        return nodes
            .asSequence()
            .map { it to expectedDropValue(it.drops, prices) }
            .sortedWith(compareByDescending { it.second.value ?: -1L })
            .take(limit)
            .map { (node, expected) ->
                HarvestOpportunity(node = node, expectedValue = expected.value, missingDropCount = expected.missingDropCount)
            }.toList()
    }
}
