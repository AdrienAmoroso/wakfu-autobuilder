package me.chosante.marketserver.equipment

import kotlinx.serialization.json.Json
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

    /** Every monster with at least one known drop, joined with its `monsters.json` entry. Monsters whose id isn't in `monsters.json` are dropped (can't display a name/level). */
    val all: List<Pair<Monster, MonsterLoot>> by lazy {
        val stream = MonsterDropCatalog::class.java.getResourceAsStream("/monster-drops.json")
        if (stream == null) {
            emptyList()
        } else {
            json
                .decodeFromString<List<MonsterLoot>>(stream.bufferedReader().readText())
                .mapNotNull { loot -> monstersById[loot.monsterId]?.let { it to loot } }
        }
    }
}
