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
import me.chosante.common.Equipment

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
     * Item details (name/rarity/icon id/level/…) for enriching a bare `itemId` in the Market
     * screen's Prices/Craft Cost tabs -- the same [Equipment] the build optimizer already renders
     * via `ItemThumbnail`/`RarityIcon`, served by market-server's `/api/items/{id}` off its
     * existing `EquipmentCatalog` (reusing `equipments.json`, no separate item database). Null on
     * a 404 (unknown itemId) or any other failure -- this is best-effort enrichment, never on the
     * critical path for prices/craft cost themselves.
     */
    suspend fun getItem(itemId: Int): Equipment? =
        withContext(ioDispatcher) {
            try {
                val (_, _, result) =
                    "$baseUrl/api/items/$itemId"
                        .httpGet()
                        .awaitObjectResponse(kotlinxDeserializerOf(loader = Equipment.serializer(), json = json))
                result
            } catch (_: Exception) {
                null
            }
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
