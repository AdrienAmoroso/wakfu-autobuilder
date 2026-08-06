package me.chosante.marketserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.chosante.marketserver.equipment.EquipmentCatalog

fun Route.itemRoutes() {
    get("/api/items/{id}") {
        val id =
            call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "id must be an integer")
        val equipment = EquipmentCatalog.findById(id)
        if (equipment == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(equipment)
        }
    }
}
