package me.chosante.recipesextractor

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.chosante.common.Recipe
import me.chosante.common.WakfuData
import me.chosante.common.findRepositoryRoot
import me.chosante.recipesextractor.dataretriever.getWakfuRawData
import java.io.File

suspend fun main() {
    // Fetch the CDN game data for the version pinned in common-lib's WakfuData.VERSION -- the single
    // source of truth shared by every extractor and app. See equipments-extractor/Main.kt for why
    // this doesn't probe the CDN for "latest" on its own.
    val version = WakfuData.VERSION
    println("Using pinned Wakfu data version (common-lib WakfuData.VERSION): $version")
    val wakfuRawData = getWakfuRawData(version)
    val recipes = extractData(wakfuRawData)
    val repositoryRoot = findRepositoryRoot()
    val outputDirectory = File(repositoryRoot, "autobuilder/src/main/resources").apply { mkdirs() }

    File(outputDirectory, "recipes.json")
        .writeText(Json.encodeToString(ListSerializer(Recipe.serializer()), recipes))
    println("Wrote ${recipes.size} recipes -> recipes.json (data version $version)")
}
