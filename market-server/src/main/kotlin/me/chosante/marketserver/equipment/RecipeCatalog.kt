package me.chosante.marketserver.equipment

import kotlinx.serialization.json.Json
import me.chosante.common.Recipe

// recipes.json reaches this module's classpath the same way equipments.json does -- see the
// `resources { srcDir(...) }` declaration in market-server/build.gradle.kts.
object RecipeCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    // First-recipe-wins if an item somehow has more than one recipe, same assumption the old
    // WakfuMarket.App made (`processedItems.Add(resultItemId)` in its StaticDataImporter).
    private val byItemId: Map<Int, Recipe> by lazy {
        val text =
            requireNotNull(RecipeCatalog::class.java.getResourceAsStream("/recipes.json")) {
                "recipes.json not found on the classpath"
            }.bufferedReader().readText()
        json.decodeFromString<List<Recipe>>(text).associateBy { it.itemId }
    }

    fun findByItemId(id: Int): Recipe? = byItemId[id]

    fun all(): List<Recipe> = byItemId.values.toList()
}
