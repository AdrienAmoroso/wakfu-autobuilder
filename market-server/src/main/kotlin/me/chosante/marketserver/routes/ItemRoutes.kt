package me.chosante.marketserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.chosante.common.Rarity
import me.chosante.marketserver.dto.ItemSearchResult
import me.chosante.marketserver.equipment.ItemCatalog
import me.chosante.marketserver.service.PriceObservationService
import org.jetbrains.exposed.v1.jdbc.Database

private const val DEFAULT_SEARCH_LIMIT = 50
private const val MAX_SEARCH_LIMIT = 200

fun Route.itemRoutes(database: Database) {
    get("/api/items/{id}") {
        val id =
            call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "id must be an integer")
        val item = ItemCatalog.findById(id)
        if (item == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(item)
        }
    }

    // HDV-style browse: search the item catalog (equipment + resources/consumables) by name/level/
    // rarity, embedding each hit's latest known price so the Market screen's Prices tab can render
    // a full "browse the auction house" list without a follow-up request per row.
    get("/api/items/search") {
        val params = call.request.queryParameters
        val name = params["name"]?.trim()?.ifBlank { null }
        val minLevel = params["minLevel"]?.toIntOrNull()
        val maxLevel = params["maxLevel"]?.toIntOrNull()
        val rarities =
            params["rarity"]
                ?.split(",")
                ?.mapNotNull { token -> runCatching { Rarity.valueOf(token.trim().uppercase()) }.getOrNull() }
                ?.toSet()
        val limit = minOf(params["limit"]?.toIntOrNull() ?: DEFAULT_SEARCH_LIMIT, MAX_SEARCH_LIMIT)

        // No filter at all = the tab's default view on opening the Market screen: show what's
        // actually been captured (most recent first), like walking into the HDV and seeing what's
        // listed, rather than an arbitrary slice of the ~10k-item catalog sorted by level.
        val items =
            if (name == null && minLevel == null && maxLevel == null && rarities.isNullOrEmpty()) {
                // Over-fetch candidate ids: some recently-observed items may fall outside
                // items-extractor's coverage (see ItemCatalog's doc comment) and resolve to nothing,
                // so fetching exactly `limit` ids up front could silently return fewer than `limit`
                // results -- or none, if the most recent captures all happen to miss the catalog.
                PriceObservationService
                    .recentlyObservedItemIds(database, limit * 4)
                    .asSequence()
                    .mapNotNull(ItemCatalog::findById)
                    .take(limit)
                    .toList()
            } else {
                ItemCatalog.search(name, minLevel, maxLevel, rarities, limit)
            }
        val latestByItemId = PriceObservationService.latestForItems(database, items.map { it.itemId })
        val results =
            items.map { item ->
                val latest = latestByItemId[item.itemId]
                ItemSearchResult(
                    item = item,
                    latestMinPrice = latest?.minPrice,
                    latestAvgPrice = latest?.avgPrice,
                    latestServer = latest?.server,
                    latestObservedAt = latest?.observedAt
                )
            }
        call.respond(results)
    }
}
