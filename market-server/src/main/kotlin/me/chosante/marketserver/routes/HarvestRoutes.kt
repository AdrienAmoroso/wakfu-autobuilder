package me.chosante.marketserver.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.chosante.marketserver.service.HarvestProfitabilityService
import org.jetbrains.exposed.v1.jdbc.Database

fun Route.harvestRoutes(database: Database) {
    // The Kamas screen's Harvesting tab: every harvest node ranked by expected kamas per harvest.
    get("/api/harvest/opportunities") {
        val server = call.request.queryParameters["server"]
        val minSkillLevel = call.request.queryParameters["minSkillLevel"]?.toIntOrNull()
        val limit = call.request.queryParameters.resolveScanLimit()

        call.respond(HarvestProfitabilityService.scanAll(database, server, minSkillLevel, limit))
    }
}
