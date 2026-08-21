package me.chosante.marketserver.routes

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import me.chosante.marketserver.capture.CaptureService
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureRouting(
    database: Database,
    dbPath: String,
    captureService: CaptureService = CaptureService(),
) {
    routing {
        itemRoutes(database)
        priceRoutes(database)
        craftRoutes(database)
        captureRoutes(dbPath, captureService)
    }
}
