package me.chosante.marketserver.equipment

import kotlinx.serialization.json.Json
import me.chosante.common.HarvestNode

// harvest-nodes.json reaches this module's classpath via the extra `resources { srcDir(...) }`
// declared in market-server/build.gradle.kts, same as equipments.json/resource-items.json.
object HarvestCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    val all: List<HarvestNode> by lazy {
        val stream = HarvestCatalog::class.java.getResourceAsStream("/harvest-nodes.json")
        if (stream == null) {
            emptyList()
        } else {
            json.decodeFromString<List<HarvestNode>>(stream.bufferedReader().readText())
        }
    }
}
