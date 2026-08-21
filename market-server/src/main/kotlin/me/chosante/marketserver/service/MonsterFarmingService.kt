package me.chosante.marketserver.service

import me.chosante.marketserver.dto.MonsterFarmingOpportunity
import me.chosante.marketserver.equipment.MonsterDropCatalog
import org.jetbrains.exposed.v1.jdbc.Database

object MonsterFarmingService {
    /**
     * Ranks every monster with a known drop table by expected kamas per kill -- the Kamas screen's
     * Monster Farming tab. One batched price lookup across every monster's drops.
     */
    fun scanAll(
        db: Database,
        server: String?,
        minLevel: Int?,
        maxLevel: Int?,
        limit: Int,
    ): List<MonsterFarmingOpportunity> {
        val entries =
            MonsterDropCatalog.all.filter { (monster, _) ->
                (minLevel == null || monster.level >= minLevel) && (maxLevel == null || monster.level <= maxLevel)
            }
        val neededIds = entries.flatMapTo(mutableSetOf()) { (_, loot) -> loot.drops.map { it.itemId } }
        val prices = PriceObservationService.latestForItems(db, neededIds, server)

        return entries
            .asSequence()
            .map { (monster, loot) -> monster to expectedDropValue(loot.drops, prices) }
            .sortedWith(compareByDescending { it.second.value ?: -1L })
            .take(limit)
            .map { (monster, expected) ->
                MonsterFarmingOpportunity(monster = monster, expectedValue = expected.value, missingDropCount = expected.missingDropCount)
            }.toList()
    }
}
