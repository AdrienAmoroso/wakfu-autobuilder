package me.chosante.marketserver.service

import me.chosante.marketserver.dto.MonsterFarmingOpportunity
import me.chosante.marketserver.equipment.MonsterDropCatalog
import org.jetbrains.exposed.v1.jdbc.Database

object MonsterFarmingService {
    /**
     * Ranks every monster in the game (not just ones with a known drop table -- see
     * `MonsterDropCatalog`'s doc comment) by expected kamas per kill, monsters with no known drops
     * sorted last. One batched price lookup across every monster's drops.
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
            .map { (monster, loot) -> Triple(monster, loot.drops, expectedDropValue(loot.drops, prices)) }
            .sortedWith(compareByDescending { it.third.value ?: -1L })
            .take(limit)
            .map { (monster, drops, expected) ->
                MonsterFarmingOpportunity(
                    monster = monster,
                    expectedValue = expected.value,
                    missingDropCount = expected.missingDropCount,
                    totalDropCount = drops.size,
                    drops = toDropInfos(drops, prices)
                )
            }.toList()
    }
}
