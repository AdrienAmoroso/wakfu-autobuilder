package me.chosante.marketserver.routes

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureRouting(database: Database) {
    routing {
        itemRoutes()
        priceRoutes(database)
    }
}
