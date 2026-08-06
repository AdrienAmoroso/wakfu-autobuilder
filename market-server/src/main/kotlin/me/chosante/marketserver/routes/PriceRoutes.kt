package me.chosante.marketserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.chosante.marketserver.dto.CreateObservationRequest
import me.chosante.marketserver.dto.FlagRequest
import me.chosante.marketserver.dto.UpdatePricesRequest
import me.chosante.marketserver.service.PriceObservationService
import org.jetbrains.exposed.v1.jdbc.Database

fun Route.priceRoutes(database: Database) {
    route("/api/prices") {
        get("/observations") {
            val itemId = call.request.queryParameters["itemId"]?.toIntOrNull()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()
            call.respond(PriceObservationService.listObservations(database, itemId, limit))
        }

        post("/observations") {
            val req = call.receive<CreateObservationRequest>()
            call.respond(HttpStatusCode.Created, PriceObservationService.create(database, req))
        }

        delete("/observations/{id}") {
            val id =
                call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "id must be an integer")
            if (PriceObservationService.delete(database, id)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        patch("/observations/{id}/prices") {
            val id =
                call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "id must be an integer")
            val req = call.receive<UpdatePricesRequest>()
            val updated = PriceObservationService.updatePrices(database, id, req)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(updated)
            }
        }

        patch("/observations/{id}/flag") {
            val id =
                call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "id must be an integer")
            val req = call.receive<FlagRequest>()
            val updated = PriceObservationService.setFlag(database, id, req.motif)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(updated)
            }
        }

        get("/{itemId}/latest") {
            val itemId =
                call.parameters["itemId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "itemId must be an integer")
            val latest = PriceObservationService.latestForItem(database, itemId)
            if (latest == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(latest)
            }
        }
    }
}
