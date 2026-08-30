package me.chosante.marketserver.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import me.chosante.marketserver.dto.ItemSourcesResponse
import me.chosante.marketserver.module
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

// Same real, live-verified fixtures CraftRoutesTest/MonsterFarmingRoutesTest/HarvestRoutesTest
// already lock in -- reused here rather than re-deriving new ones.
private const val CRAFTED_ITEM_ID = 12464
private const val CRAFTED_ITEM_INGREDIENT_COUNT = 4
private const val BLACK_GOBBLY_ID = 2
private const val GOBBALL_SKIN_ITEM_ID = 11528
private const val API_TREE_RESOURCE_ID = 226
private const val API_TREE_MAIN_DROP_ITEM_ID = 1718

class ItemSourcesRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun tempDbPath(): String {
        val dbFile = Files.createTempFile("market-test", ".db")
        Files.deleteIfExists(dbFile)
        return dbFile.toString()
    }

    @Test
    fun `an item with no known source returns 200 with all-empty fields, not a 404`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/items/999999999/sources")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val result = json.decodeFromString<ItemSourcesResponse>(response.bodyAsText())
            assertThat(result.recipe).isNull()
            assertThat(result.monsterSources).isEmpty()
            assertThat(result.harvestSources).isEmpty()
        }

    @Test
    fun `a craftable item returns its recipe`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/items/$CRAFTED_ITEM_ID/sources")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val result = json.decodeFromString<ItemSourcesResponse>(response.bodyAsText())
            assertThat(result.recipe).isNotNull
            assertThat(result.recipe!!.ingredients).hasSize(CRAFTED_ITEM_INGREDIENT_COUNT)
        }

    @Test
    fun `a monster-dropped item returns the monster, name and rate included`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/items/$GOBBALL_SKIN_ITEM_ID/sources")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val result = json.decodeFromString<ItemSourcesResponse>(response.bodyAsText())
            // Other Gobball-family monsters may also drop this item -- assert Black Gobbly is
            // among the sources, not that it's the only one.
            assertThat(result.monsterSources).isNotEmpty
            val source = result.monsterSources.first { it.monsterId == BLACK_GOBBLY_ID }
            assertThat(source.name.en).isNotBlank()
            assertThat(source.dropRate).isGreaterThan(0.0)
        }

    @Test
    fun `a harvest-dropped item returns the node, name and rate included`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/items/$API_TREE_MAIN_DROP_ITEM_ID/sources")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val result = json.decodeFromString<ItemSourcesResponse>(response.bodyAsText())
            assertThat(result.harvestSources).isNotEmpty
            val source = result.harvestSources.first { it.resourceId == API_TREE_RESOURCE_ID }
            assertThat(source.name.en).isNotBlank()
            assertThat(source.dropRate).isGreaterThan(0.0)
        }

    @Test
    fun `a non-integer id is rejected`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/items/not-a-number/sources")

            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        }
}
