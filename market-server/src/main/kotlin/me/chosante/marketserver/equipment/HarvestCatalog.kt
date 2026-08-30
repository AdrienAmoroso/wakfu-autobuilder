package me.chosante.marketserver.equipment

import kotlinx.serialization.json.Json
import me.chosante.common.HarvestDrop
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

    /** Reverse of [all]: which harvest node(s) yield a given item, and at what rate/quantity -- the
     * "where do I get this" lookup ([me.chosante.marketserver.service.ItemSourcesService]). */
    val nodesByItemId: Map<Int, List<Pair<HarvestNode, HarvestDrop>>> by lazy {
        all
            .flatMap { node -> node.drops.map { drop -> drop.itemId to (node to drop) } }
            .groupBy({ it.first }, { it.second })
    }
}
