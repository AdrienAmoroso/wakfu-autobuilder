package me.chosante.harvestextractor

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.chosante.common.HarvestNode
import me.chosante.common.WakfuData
import me.chosante.common.findRepositoryRoot
import me.chosante.harvestextractor.dataretriever.getWakfuHarvestData
import java.io.File

/**
 * Builds `autobuilder/src/main/resources/harvest-nodes.json` from 4 Ankama CDN files not otherwise
 * ingested anywhere in this repo (`resources.json`, `collectibleResources.json`,
 * `harvestLoots.json`, `resourceTypes.json`) -- see `HarvestExtractor.kt` for how they join.
 *
 * Node icons (`HarvestNode.iconKey`, the same `gfxId` convention as `Equipment.guiId`) live in the
 * local game client's `gui.jar` at the same `icons/items/64/<id>.tga` path equipment icons do
 * (confirmed directly against the jar) -- extracted by `:gui-compose:generateAssets`, not here.
 *
 * Run with: `./gradlew :harvest-extractor:run`.
 */
suspend fun main() {
    val version = WakfuData.VERSION
    println("Using pinned Wakfu data version (common-lib WakfuData.VERSION): $version")

    val rawData = getWakfuHarvestData(version)
    println(
        "Fetched ${rawData.resourceNodes.size} resource nodes, ${rawData.resourceTypes.size} categories, " +
            "${rawData.collectibleResources.size} harvestable stages, ${rawData.harvestLoots.size} loot rows"
    )

    val nodes = extractHarvestNodes(rawData)
    val withDrops = nodes.count { it.drops.isNotEmpty() }
    println("Built ${nodes.size} harvest nodes ($withDrops with at least one drop)")

    val repositoryRoot = findRepositoryRoot()
    val outputDirectory = File(repositoryRoot, "autobuilder/src/main/resources").apply { mkdirs() }
    File(outputDirectory, "harvest-nodes.json")
        .writeText(Json.encodeToString(ListSerializer(HarvestNode.serializer()), nodes))

    println("Wrote ${nodes.size} nodes -> harvest-nodes.json (data version $version)")
}
