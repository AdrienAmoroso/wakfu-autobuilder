package me.chosante.marketserver.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.chosante.marketserver.service.MonsterFarmingService
import org.jetbrains.exposed.v1.jdbc.Database

fun Route.monsterFarmingRoutes(database: Database) {
    // The Kamas screen's Monster Farming tab: every monster with a known drop table, ranked by
    // expected kamas per kill.
    get("/api/monster-drops/opportunities") {
        val server = call.request.queryParameters["server"]
        val minLevel = call.request.queryParameters["minLevel"]?.toIntOrNull()
        val maxLevel = call.request.queryParameters["maxLevel"]?.toIntOrNull()
        val limit = call.request.queryParameters.resolveScanLimit()

        call.respond(MonsterFarmingService.scanAll(database, server, minLevel, maxLevel, limit))
    }
}
