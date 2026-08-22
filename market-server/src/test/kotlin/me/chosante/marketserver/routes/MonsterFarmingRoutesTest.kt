package me.chosante.marketserver.routes

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.chosante.marketserver.dto.MonsterFarmingOpportunity
import me.chosante.marketserver.module
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

// Real monster (id 2, "Black Gobbly", verified live against the encyclopedia and against the
// committed monster-drops.json): drops itemId 11528 (Gobball Skin) at 25%.
private const val BLACK_GOBBLY_ID = 2
private const val GOBBALL_SKIN_ITEM_ID = 11528

class MonsterFarmingRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun tempDbPath(): String {
        val dbFile = Files.createTempFile("market-test", ".db")
        Files.deleteIfExists(dbFile)
        return dbFile.toString()
    }

    @Test
    fun `no captured prices yields monsters with a null expected value, not an error`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/monster-drops/opportunities")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val results = json.decodeFromString(ListSerializer(MonsterFarmingOpportunity.serializer()), response.bodyAsText())
            assertThat(results).isNotEmpty()
            assertThat(results).allMatch { it.expectedValue == null }
        }

    @Test
    fun `a captured drop price surfaces its monster with a computed expected value`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            client.post("/api/prices/observations") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"itemId":$GOBBALL_SKIN_ITEM_ID,"server":"Rushu","observedAt":"2026-08-06T12:00:00","source":"capture_full_offers",
                    "confidenceScore":1.0,"minPrice":1000,"avgPrice":1000}"""
                )
            }

            val response = client.get("/api/monster-drops/opportunities")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val results = json.decodeFromString(ListSerializer(MonsterFarmingOpportunity.serializer()), response.bodyAsText())
            val blackGobbly = results.first { it.monster.id == BLACK_GOBBLY_ID }
            // 0.25 * 1 * 1000 = 250.
            assertThat(blackGobbly.expectedValue).isEqualTo(250L)
            // Black Gobbly drops 8 items total (including 3 fractional-rate drops, e.g. 0.8% --
            // confirmed against the real committed monster-drops.json); only Gobball Skin has a
            // captured price here.
            assertThat(blackGobbly.missingDropCount).isEqualTo(7)
        }

    @Test
    fun `level range filters out monsters outside it`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/monster-drops/opportunities?minLevel=500")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val results = json.decodeFromString(ListSerializer(MonsterFarmingOpportunity.serializer()), response.bodyAsText())
            assertThat(results).allMatch { it.monster.level >= 500 }
        }
}
