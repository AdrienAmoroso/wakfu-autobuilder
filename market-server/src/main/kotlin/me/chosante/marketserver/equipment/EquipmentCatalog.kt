package me.chosante.marketserver.equipment

import kotlinx.serialization.json.Json
import me.chosante.common.Equipment

// equipments.json reaches this module's classpath via the extra `resources { srcDir(...) }`
// declared in market-server/build.gradle.kts (pointing at autobuilder's committed resource
// directory), not via a project dependency on :autobuilder -- see that build file for why.
object EquipmentCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    private val byId: Map<Int, Equipment> by lazy {
        val text =
            requireNotNull(EquipmentCatalog::class.java.getResourceAsStream("/equipments.json")) {
                "equipments.json not found on the classpath"
            }.bufferedReader().readText()
        json.decodeFromString<List<Equipment>>(text).associateBy { it.equipmentId }
    }

    fun findById(id: Int): Equipment? = byId[id]
}
