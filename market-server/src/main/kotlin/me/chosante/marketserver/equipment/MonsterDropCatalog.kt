package me.chosante.marketserver.equipment

import kotlinx.serialization.json.Json
import me.chosante.common.HarvestDrop
import me.chosante.common.Monster
import me.chosante.common.MonsterLoot

// monster-drops.json / monsters.json reach this module's classpath via the extra
// `resources { srcDir(...) }` declared in market-server/build.gradle.kts, same as every other
// catalog here. `monster-drops.json` (items-extractor) carries only monsterId+drops -- name/level
// come from the already-committed `monsters.json` (bdata-extractor), joined here at load time
// rather than re-scraped and duplicated in the crawl.
object MonsterDropCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    private val monstersById: Map<Int, Monster> by lazy {
        val stream = MonsterDropCatalog::class.java.getResourceAsStream("/monsters.json")
        if (stream == null) {
            emptyMap()
        } else {
            json.decodeFromString<List<Monster>>(stream.bufferedReader().readText()).associateBy { it.id }
        }
    }

    private val lootByMonsterId: Map<Int, MonsterLoot> by lazy {
        val stream = MonsterDropCatalog::class.java.getResourceAsStream("/monster-drops.json")
        if (stream == null) {
            emptyMap()
        } else {
            json.decodeFromString<List<MonsterLoot>>(stream.bufferedReader().readText()).associateBy { it.monsterId }
        }
    }

    /**
     * Every monster in `monsters.json` (2846, including ~2118 with no public encyclopedia page and
     * so no possible drop table -- a confirmed hard wall, not a bug), paired with its known loot or
     * an empty one when absent. Left join, deliberately -- an earlier inner join silently hid every
     * monster without a `monster-drops.json` entry (including 8 real bosses), which is exactly the
     * kind of missing-not-shown gap this catalog exists to avoid. `MonsterLoot.drops.isEmpty()`
     * means "no known drops," which the GUI surfaces honestly rather than hiding the monster.
     */
    val all: List<Pair<Monster, MonsterLoot>> by lazy {
        monstersById.values.map { monster -> monster to (lootByMonsterId[monster.id] ?: MonsterLoot(monsterId = monster.id, drops = emptyList())) }
    }

    /** Reverse of [all]: which monster(s) drop a given item, and at what rate/quantity -- the
     * "where do I get this" lookup ([me.chosante.marketserver.service.ItemSourcesService]). */
    val monstersByItemId: Map<Int, List<Pair<Monster, HarvestDrop>>> by lazy {
        all
            .flatMap { (monster, loot) -> loot.drops.map { drop -> drop.itemId to (monster to drop) } }
            .groupBy({ it.first }, { it.second })
    }
}
