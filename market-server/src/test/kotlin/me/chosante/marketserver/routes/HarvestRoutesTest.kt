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
import me.chosante.marketserver.dto.HarvestOpportunity
import me.chosante.marketserver.module
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

// Real node (resourceId 226, "Api Tree (stage 3)", verified live against the Ankama CDN and
// against the committed harvest-nodes.json): drops itemId 1718 at 100% (split 0.5/0.5 by quantity).
private const val API_TREE_RESOURCE_ID = 226
private const val API_TREE_MAIN_DROP_ITEM_ID = 1718

class HarvestRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun tempDbPath(): String {
        val dbFile = Files.createTempFile("market-test", ".db")
        Files.deleteIfExists(dbFile)
        return dbFile.toString()
    }

    @Test
    fun `no captured prices yields nodes with a null expected value, not an error`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/harvest/opportunities")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val results = json.decodeFromString(ListSerializer(HarvestOpportunity.serializer()), response.bodyAsText())
            assertThat(results).isNotEmpty()
            assertThat(results).allMatch { it.expectedValue == null }
        }

    @Test
    fun `a captured drop price surfaces its node with a computed expected value, ranked first`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            client.post("/api/prices/observations") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"itemId":$API_TREE_MAIN_DROP_ITEM_ID,"server":"Rushu","observedAt":"2026-08-06T12:00:00","source":"capture_full_offers",
                    "confidenceScore":1.0,"minPrice":1000,"avgPrice":1000}"""
                )
            }

            val response = client.get("/api/harvest/opportunities")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val results = json.decodeFromString(ListSerializer(HarvestOpportunity.serializer()), response.bodyAsText())
            val apiTree = results.first { it.node.resourceId == API_TREE_RESOURCE_ID }
            // 0.5*3*1000 + 0.5*4*1000 = 3500 (the two 1%-chance bonus drops have no captured price, excluded).
            assertThat(apiTree.expectedValue).isEqualTo(3500L)
            assertThat(results.first().expectedValue).isEqualTo(apiTree.expectedValue)
            // The 3500 is a partial sum, not the node's full expected value -- missingDropCount says so.
            assertThat(apiTree.missingDropCount).isEqualTo(2)
        }

    @Test
    fun `minSkillLevel filters out lower-requirement nodes`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/harvest/opportunities?minSkillLevel=200")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val results = json.decodeFromString(ListSerializer(HarvestOpportunity.serializer()), response.bodyAsText())
            assertThat(results).allMatch { it.node.skillLevelRequired >= 200 }
        }
}
