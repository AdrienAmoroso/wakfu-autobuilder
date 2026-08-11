package me.chosante.marketserver.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.chosante.marketserver.capture.CaptureService

fun Route.captureRoutes(
    dbPath: String,
    captureService: CaptureService,
) {
    route("/api/capture") {
        post("/start") {
            call.respond(captureService.start(dbPath))
        }
        post("/stop") {
            call.respond(captureService.stop(dbPath))
        }
        get("/status") {
            call.respond(captureService.status())
        }
    }
}
