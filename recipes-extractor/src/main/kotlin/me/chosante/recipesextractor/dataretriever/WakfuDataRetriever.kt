package me.chosante.recipesextractor.dataretriever

import com.github.kittinunf.fuel.coroutines.awaitResult
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.serialization.kotlinxDeserializerOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.chosante.recipesextractor.dataretriever.dtos.Jobs
import me.chosante.recipesextractor.dataretriever.dtos.RecipeDefinition
import me.chosante.recipesextractor.dataretriever.dtos.RecipeIngredientDto
import me.chosante.recipesextractor.dataretriever.dtos.RecipeResult

const val GAMEDATA_BASE_URL = "https://wakfu.cdn.ankama.com/gamedata/:version"
val ioDispatcher = Dispatchers.IO

// Lenient JSON for the Ankama CDN payloads: tolerate unknown fields so a future field addition
// does not crash the extractor, same rationale as equipments-extractor's identical constant.
private val CDN_JSON = Json { ignoreUnknownKeys = true }

suspend fun getWakfuRawData(version: String): WakfuData =
    coroutineScope {
        val baseUrlWithVersion = GAMEDATA_BASE_URL.replace(":version", version)

        val recipes =
            async(ioDispatcher) {
                "$baseUrlWithVersion/recipes.json"
                    .httpGet()
                    .awaitResult(kotlinxDeserializerOf(loader = ListSerializer(RecipeDefinition.serializer()), json = CDN_JSON))
                    .fold(
                        success = { it },
                        failure = { throw IllegalStateException(it) }
                    )
            }

        val recipeResults =
            async(ioDispatcher) {
                "$baseUrlWithVersion/recipeResults.json"
                    .httpGet()
                    .awaitResult(kotlinxDeserializerOf(loader = ListSerializer(RecipeResult.serializer()), json = CDN_JSON))
                    .fold(
                        success = { it },
                        failure = { throw IllegalStateException(it) }
                    )
            }

        val recipeIngredients =
            async(ioDispatcher) {
                "$baseUrlWithVersion/recipeIngredients.json"
                    .httpGet()
                    .awaitResult(
                        kotlinxDeserializerOf(
                            loader = ListSerializer(RecipeIngredientDto.serializer()),
                            json = CDN_JSON
                        )
                    ).fold(
                        success = { it },
                        failure = { throw IllegalStateException(it) }
                    )
            }

        val jobs =
            async(ioDispatcher) {
                "$baseUrlWithVersion/recipeCategories.json"
                    .httpGet()
                    .awaitResult(kotlinxDeserializerOf(loader = ListSerializer(Jobs.serializer()), json = CDN_JSON))
                    .fold(
                        success = { it },
                        failure = { throw IllegalStateException(it) }
                    )
            }

        WakfuData(
            recipes = recipes.await(),
            recipeResults = recipeResults.await(),
            recipeIngredients = recipeIngredients.await(),
            jobs = jobs.await()
        )
    }
