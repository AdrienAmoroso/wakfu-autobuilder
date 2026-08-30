package me.chosante.marketclient

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObjectResponse
import com.github.kittinunf.fuel.coroutines.awaitStringResponse
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPatch
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.kotlinxDeserializerOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.chosante.common.Rarity

private const val DEFAULT_BASE_URL = "http://localhost:8085"

/**
 * HTTP client for the local, maintainer-only `market-server` (see AGENTS.md/market-server module) --
 * not a general end-user dependency, the way `bdata-extractor`/`generateAssets` aren't either. A
 * connection failure (server not running) simply propagates as an exception, exactly like any other
 * network failure the Zenith flow already handles.
 *
 * Lives in its own module rather than inside `gui-compose` directly: applying
 * `kotlin("plugin.serialization")` inside `gui-compose` hits a real Kotlin/Compose Multiplatform
 * Gradle plugin conflict (the serialization compiler plugin reports "current kotlinx.serialization
 * core version is unknown" for any @Serializable class declared there, reproduced with a clean
 * daemon-less build, independent of dependency versions/forcing). This module mirrors
 * `zenith-builder`'s already-working `kotlin("plugin.serialization")` + Fuel setup, and
 * `gui-compose` only ever consumes it as a project dependency.
 */
class MarketRepository(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listObservations(
        itemId: Int? = null,
        limit: Int? = null,
    ): List<ObservationResponse> =
        withContext(ioDispatcher) {
            val params =
                buildList {
                    itemId?.let { add("itemId" to it) }
                    limit?.let { add("limit" to it) }
                }
            val (_, _, result) =
                "$baseUrl/api/prices/observations"
                    .httpGet(params)
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = ListSerializer(ObservationResponse.serializer()), json = json))
            result
        }

    suspend fun createObservation(request: CreateObservationRequest): ObservationResponse =
        withContext(ioDispatcher) {
            val (_, _, result) =
                "$baseUrl/api/prices/observations"
                    .httpPost()
                    .jsonBody(json.encodeToString(CreateObservationRequest.serializer(), request))
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = ObservationResponse.serializer(), json = json))
            result
        }

    suspend fun deleteObservation(id: Int) {
        withContext(ioDispatcher) {
            "$baseUrl/api/prices/observations/$id".httpDelete().awaitStringResponse()
        }
    }

    suspend fun updatePrices(
        id: Int,
        request: UpdatePricesRequest,
    ): ObservationResponse =
        withContext(ioDispatcher) {
            val (_, _, result) =
                "$baseUrl/api/prices/observations/$id/prices"
                    .httpPatch()
                    .jsonBody(json.encodeToString(UpdatePricesRequest.serializer(), request))
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = ObservationResponse.serializer(), json = json))
            result
        }

    suspend fun setFlag(
        id: Int,
        motif: FlagMotif,
    ): ObservationResponse =
        withContext(ioDispatcher) {
            val (_, _, result) =
                "$baseUrl/api/prices/observations/$id/flag"
                    .httpPatch()
                    .jsonBody(json.encodeToString(FlagRequest.serializer(), FlagRequest(motif)))
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = ObservationResponse.serializer(), json = json))
            result
        }

    suspend fun craftCost(
        itemId: Int,
        server: String? = null,
        taxRate: Double? = null,
    ): CraftCostResponse =
        withContext(ioDispatcher) {
            val params =
                buildList {
                    server?.let { add("server" to it) }
                    taxRate?.let { add("taxRate" to it) }
                }
            val (_, _, result) =
                "$baseUrl/api/crafts/$itemId/cost"
                    .httpGet(params)
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = CraftCostResponse.serializer(), json = json))
            result
        }

    /**
     * The Kamas screen's Crafting tab: every recipe with enough captured price data to score,
     * ranked by ROI best-first.
     */
    suspend fun craftOpportunities(
        server: String? = null,
        taxRate: Double? = null,
        limit: Int? = null,
    ): List<CraftCostResponse> =
        withContext(ioDispatcher) {
            val params =
                buildList {
                    server?.let { add("server" to it) }
                    taxRate?.let { add("taxRate" to it) }
                    limit?.let { add("limit" to it) }
                }
            val (_, _, result) =
                "$baseUrl/api/crafts/opportunities"
                    .httpGet(params)
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = ListSerializer(CraftCostResponse.serializer()), json = json))
            result
        }

    /**
     * The Kamas screen's Harvesting tab: every harvest node with enough captured drop-price data
     * to score, ranked by expected kamas per harvest best-first.
     */
    suspend fun harvestOpportunities(
        server: String? = null,
        minSkillLevel: Int? = null,
        limit: Int? = null,
    ): List<HarvestOpportunity> =
        withContext(ioDispatcher) {
            val params =
                buildList {
                    server?.let { add("server" to it) }
                    minSkillLevel?.let { add("minSkillLevel" to it) }
                    limit?.let { add("limit" to it) }
                }
            val (_, _, result) =
                "$baseUrl/api/harvest/opportunities"
                    .httpGet(params)
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = ListSerializer(HarvestOpportunity.serializer()), json = json))
            result
        }

    /**
     * The Kamas screen's Monster Farming tab: every monster with a known drop table, ranked by
     * expected kamas per kill best-first.
     */
    suspend fun monsterFarmingOpportunities(
        server: String? = null,
        minLevel: Int? = null,
        maxLevel: Int? = null,
        limit: Int? = null,
    ): List<MonsterFarmingOpportunity> =
        withContext(ioDispatcher) {
            val params =
                buildList {
                    server?.let { add("server" to it) }
                    minLevel?.let { add("minLevel" to it) }
                    maxLevel?.let { add("maxLevel" to it) }
                    limit?.let { add("limit" to it) }
                }
            val (_, _, result) =
                "$baseUrl/api/monster-drops/opportunities"
                    .httpGet(params)
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = ListSerializer(MonsterFarmingOpportunity.serializer()), json = json))
            result
        }

    /**
     * Item details (name/rarity/icon key/level/…) for enriching a bare `itemId` in the Market
     * screen's Prices/Craft Cost tabs -- the same visual language the build optimizer renders via
     * `ItemThumbnail`/`RarityIcon`, served by market-server's `/api/items/{id}` off its unified
     * `ItemCatalog` (equipment + resources/consumables, no separate lookup per kind). Null on a 404
     * (unknown itemId) or any other failure -- this is best-effort enrichment, never on the critical
     * path for prices/craft cost themselves.
     */
    suspend fun getItem(itemId: Int): ItemInfoResponse? =
        withContext(ioDispatcher) {
            try {
                val (_, _, result) =
                    "$baseUrl/api/items/$itemId"
                        .httpGet()
                        .awaitObjectResponse(kotlinxDeserializerOf(loader = ItemInfoResponse.serializer(), json = json))
                result
            } catch (_: Exception) {
                null
            }
        }

    /**
     * "How do I get this item" -- craft recipe (if any) + monster/harvest-node drop sources (if
     * any), off market-server's `/api/items/{id}/sources`. Same best-effort shape as [getItem]:
     * null on any failure, never on a critical path (this is enrichment for the item-detail popup).
     */
    suspend fun itemSources(itemId: Int): ItemSourcesResponse? =
        withContext(ioDispatcher) {
            try {
                val (_, _, result) =
                    "$baseUrl/api/items/$itemId/sources"
                        .httpGet()
                        .awaitObjectResponse(kotlinxDeserializerOf(loader = ItemSourcesResponse.serializer(), json = json))
                result
            } catch (_: Exception) {
                null
            }
        }

    /**
     * HDV-style browse: search the item catalog by name/level range/rarity/category, each hit
     * carrying its latest known price. Throws on failure like [listObservations] -- unlike [getItem]'s
     * best-effort enrichment, this list IS the Prices tab's primary content, so the caller must be
     * able to tell "no results" (empty list) apart from "couldn't reach market-server" (exception).
     */
    suspend fun searchItems(
        name: String? = null,
        minLevel: Int? = null,
        maxLevel: Int? = null,
        rarities: Set<Rarity> = emptySet(),
        categories: Set<String> = emptySet(),
        limit: Int? = null,
    ): List<ItemSearchResult> =
        withContext(ioDispatcher) {
            val params =
                buildList {
                    name?.let { add("name" to it) }
                    minLevel?.let { add("minLevel" to it) }
                    maxLevel?.let { add("maxLevel" to it) }
                    if (rarities.isNotEmpty()) add("rarity" to rarities.joinToString(",") { it.name })
                    if (categories.isNotEmpty()) add("category" to categories.joinToString(","))
                    limit?.let { add("limit" to it) }
                }
            val (_, _, result) =
                "$baseUrl/api/items/search"
                    .httpGet(params)
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = ListSerializer(ItemSearchResult.serializer()), json = json))
            result
        }

    suspend fun startCapture(): CaptureStatusResponse =
        withContext(ioDispatcher) {
            val (_, _, result) =
                "$baseUrl/api/capture/start"
                    .httpPost()
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = CaptureStatusResponse.serializer(), json = json))
            result
        }

    suspend fun stopCapture(): CaptureStatusResponse =
        withContext(ioDispatcher) {
            val (_, _, result) =
                "$baseUrl/api/capture/stop"
                    .httpPost()
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = CaptureStatusResponse.serializer(), json = json))
            result
        }

    suspend fun captureStatus(): CaptureStatusResponse =
        withContext(ioDispatcher) {
            val (_, _, result) =
                "$baseUrl/api/capture/status"
                    .httpGet()
                    .awaitObjectResponse(kotlinxDeserializerOf(loader = CaptureStatusResponse.serializer(), json = json))
            result
        }
}
