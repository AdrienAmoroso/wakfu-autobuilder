package me.chosante.marketserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.chosante.marketserver.service.CraftCostService
import org.jetbrains.exposed.v1.jdbc.Database

fun Route.craftRoutes(database: Database) {
    get("/api/crafts/{itemId}/cost") {
        val itemId =
            call.parameters["itemId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "itemId must be an integer")
        val server = call.request.queryParameters["server"]
        val taxRate = call.request.queryParameters["taxRate"]?.toDoubleOrNull()

        val result = CraftCostService.compute(database, itemId, server, taxRate)
        if (result == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }

    // The Kamas screen's Crafting tab: every recipe with enough captured price data to score,
    // ranked by ROI -- "what's worth crafting and reselling right now."
    get("/api/crafts/opportunities") {
        val server = call.request.queryParameters["server"]
        val taxRate = call.request.queryParameters["taxRate"]?.toDoubleOrNull()
        val limit = call.request.queryParameters.resolveScanLimit()

        call.respond(CraftCostService.scanAll(database, server, taxRate, limit))
    }
}
