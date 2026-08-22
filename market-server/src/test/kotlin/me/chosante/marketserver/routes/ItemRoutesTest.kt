package me.chosante.marketserver.routes

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import me.chosante.marketserver.module
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ItemRoutesTest {
    @Test
    fun `known equipment id returns its data from the embedded catalog`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/items/2021")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).contains("\"itemId\":2021").contains("\"isEquipment\":true")
        }

    @Test
    fun `search by name returns matching items with an embedded (absent) latest price`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/items/search?name=Gobball+Amulet")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = response.bodyAsText()
            assertThat(body).contains("\"itemId\":2021").contains("\"latestMinPrice\":null")
        }

    @Test
    fun `search filters by level and rarity`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val tooHighLevel = client.get("/api/items/search?name=Gobball+Amulet&minLevel=50")
            val wrongRarity = client.get("/api/items/search?name=Gobball+Amulet&rarity=EPIC")
            val matching = client.get("/api/items/search?name=Gobball+Amulet&minLevel=1&maxLevel=10&rarity=UNCOMMON")

            assertThat(tooHighLevel.bodyAsText()).doesNotContain("\"itemId\":2021")
            assertThat(wrongRarity.bodyAsText()).doesNotContain("\"itemId\":2021")
            assertThat(matching.bodyAsText()).contains("\"itemId\":2021")
        }

    @Test
    fun `search filters by category`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val matching = client.get("/api/items/search?name=Gobball+Amulet&category=equipment")
            val wrongCategory = client.get("/api/items/search?name=Gobball+Amulet&category=creature")

            assertThat(matching.bodyAsText()).contains("\"itemId\":2021")
            assertThat(wrongCategory.bodyAsText()).doesNotContain("\"itemId\":2021")
        }

    @Test
    fun `search with no filters returns recently observed items, not an arbitrary catalog slice`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            client.post("/api/prices/observations") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"itemId":2021,"server":"Rushu","observedAt":"2026-08-06T12:00:00","source":"capture_full_offers",
                    "confidenceScore":1.0,"minPrice":100,"avgPrice":150}"""
                )
            }

            val response = client.get("/api/items/search")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = response.bodyAsText()
            assertThat(body).contains("\"itemId\":2021").contains("\"latestMinPrice\":100")
        }

    @Test
    fun `default view skips recently observed items that don't resolve in the catalog instead of starving the response`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            // The most recently observed item (999999998) is a bogus id with no catalog entry --
            // without over-fetching, a low `limit` could return nothing at all instead of falling
            // through to the next (resolvable) recently observed item.
            client.post("/api/prices/observations") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"itemId":2021,"server":"Rushu","observedAt":"2026-08-06T12:00:00","source":"capture_full_offers",
                    "confidenceScore":1.0,"minPrice":100,"avgPrice":150}"""
                )
            }
            client.post("/api/prices/observations") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"itemId":999999998,"server":"Rushu","observedAt":"2026-08-06T13:00:00","source":"capture_full_offers",
                    "confidenceScore":1.0,"minPrice":100,"avgPrice":150}"""
                )
            }

            val response = client.get("/api/items/search?limit=1")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).contains("\"itemId\":2021")
        }

    @Test
    fun `unknown equipment id returns 404`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/items/999999999")

            assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        }

    private fun tempDbPath(): String {
        val dbFile = Files.createTempFile("market-test", ".db")
        Files.deleteIfExists(dbFile)
        return dbFile.toString()
    }
}
