package me.chosante.harvestextractor.dataretriever

import com.github.kittinunf.fuel.coroutines.awaitResult
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.serialization.kotlinxDeserializerOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.chosante.harvestextractor.dataretriever.dtos.CollectibleResource
import me.chosante.harvestextractor.dataretriever.dtos.HarvestLootRow
import me.chosante.harvestextractor.dataretriever.dtos.ResourceNode
import me.chosante.harvestextractor.dataretriever.dtos.ResourceTypeDef

const val GAMEDATA_BASE_URL = "https://wakfu.cdn.ankama.com/gamedata/:version"
val ioDispatcher = Dispatchers.IO

// Lenient JSON for the Ankama CDN payloads -- same rationale as every other extractor's identical
// constant: tolerate unknown fields so a future field addition doesn't crash this tool.
private val CDN_JSON = Json { ignoreUnknownKeys = true }

data class WakfuHarvestData(
    val resourceNodes: List<ResourceNode>,
    val resourceTypes: List<ResourceTypeDef>,
    val collectibleResources: List<CollectibleResource>,
    val harvestLoots: List<HarvestLootRow>,
)

suspend fun getWakfuHarvestData(version: String): WakfuHarvestData =
    coroutineScope {
        val baseUrlWithVersion = GAMEDATA_BASE_URL.replace(":version", version)

        suspend fun <T> fetch(
            file: String,
            serializer: kotlinx.serialization.KSerializer<List<T>>,
        ): List<T> =
            "$baseUrlWithVersion/$file"
                .httpGet()
                .awaitResult(kotlinxDeserializerOf(loader = serializer, json = CDN_JSON))
                .fold(success = { it }, failure = { throw IllegalStateException(it) })

        val resourceNodes = async(ioDispatcher) { fetch("resources.json", ListSerializer(ResourceNode.serializer())) }
        val resourceTypes = async(ioDispatcher) { fetch("resourceTypes.json", ListSerializer(ResourceTypeDef.serializer())) }
        val collectibleResources = async(ioDispatcher) { fetch("collectibleResources.json", ListSerializer(CollectibleResource.serializer())) }
        val harvestLoots = async(ioDispatcher) { fetch("harvestLoots.json", ListSerializer(HarvestLootRow.serializer())) }

        WakfuHarvestData(
            resourceNodes = resourceNodes.await(),
            resourceTypes = resourceTypes.await(),
            collectibleResources = collectibleResources.await(),
            harvestLoots = harvestLoots.await()
        )
    }
