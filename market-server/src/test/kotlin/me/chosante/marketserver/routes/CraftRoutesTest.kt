package me.chosante.marketserver.routes

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import me.chosante.marketserver.dto.CraftCostResponse
import me.chosante.marketserver.module
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

// Real recipe (recipeId 1160, verified live against the Ankama CDN and against the committed
// recipes.json): crafts itemId 12464 from 4 ingredients.
private const val CRAFTED_ITEM_ID = 12464
private val INGREDIENT_IDS = listOf(5439, 5434, 11469, 21015)
private val INGREDIENT_QUANTITIES = mapOf(5439 to 3, 5434 to 2, 11469 to 15, 21015 to 1)

class CraftRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun tempDbPath(): String {
        val dbFile = Files.createTempFile("market-test", ".db")
        Files.deleteIfExists(dbFile)
        return dbFile.toString()
    }

    private fun observationBody(
        itemId: Int,
        minPrice: Long,
    ) = """{"itemId":$itemId,"server":"Rushu","observedAt":"2026-08-06T12:00:00","source":"capture_full_offers","confidenceScore":1.0,"minPrice":$minPrice,"avgPrice":$minPrice}"""

    @Test
    fun `unknown item returns 404`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/crafts/999999999/cost")

            assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        }

    @Test
    fun `missing ingredient prices are flagged insufficient_data`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/crafts/$CRAFTED_ITEM_ID/cost")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val result = json.decodeFromString<CraftCostResponse>(response.bodyAsText())
            assertThat(result.decision).isEqualTo("insufficient_data")
            assertThat(result.missingPriceCount).isEqualTo(INGREDIENT_IDS.size)
            assertThat(result.ingredients).hasSize(INGREDIENT_IDS.size)
        }

    @Test
    fun `full price data computes craft cost margin and roi`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            for (ingredientId in INGREDIENT_IDS) {
                client.post("/api/prices/observations") {
                    contentType(ContentType.Application.Json)
                    setBody(observationBody(ingredientId, minPrice = 100))
                }
            }
            client.post("/api/prices/observations") {
                contentType(ContentType.Application.Json)
                setBody(observationBody(CRAFTED_ITEM_ID, minPrice = 100000))
            }

            val response = client.get("/api/crafts/$CRAFTED_ITEM_ID/cost")

            val result = json.decodeFromString<CraftCostResponse>(response.bodyAsText())
            val expectedCraftCost = INGREDIENT_QUANTITIES.values.sumOf { it * 100L }
            assertThat(result.missingPriceCount).isZero()
            assertThat(result.craftCost).isEqualTo(expectedCraftCost)
            assertThat(result.marketPrice).isEqualTo(100000)
            assertThat(result.grossMargin).isEqualTo(100000 - expectedCraftCost)
            assertThat(result.decision).isIn("craft", "buy")
        }
}
