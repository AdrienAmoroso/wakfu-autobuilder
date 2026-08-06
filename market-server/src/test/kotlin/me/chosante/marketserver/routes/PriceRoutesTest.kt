package me.chosante.marketserver.routes

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import me.chosante.marketserver.dto.ObservationResponse
import me.chosante.marketserver.module
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PriceRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun tempDbPath(): String {
        val dbFile = Files.createTempFile("market-test", ".db")
        Files.deleteIfExists(dbFile)
        return dbFile.toString()
    }

    private fun createRequestBody(
        itemId: Int,
        server: String = "Rushu",
        observedAt: String = "2026-08-06T12:00:00",
        source: String = "capture_full_offers",
        confidenceScore: Double = 1.0,
        minPrice: Long = 100,
        avgPrice: Long = 150,
        captureUid: String? = null,
    ) = """
        {"itemId":$itemId,"server":"$server","observedAt":"$observedAt","source":"$source",
        "confidenceScore":$confidenceScore,"minPrice":$minPrice,"avgPrice":$avgPrice
        ${captureUid?.let { ""","captureUid":"$it"""" } ?: ""}}
        """.trimIndent()

    @Test
    fun `listing observations on an empty database returns an empty array`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/prices/observations")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(json.decodeFromString<List<ObservationResponse>>(response.bodyAsText())).isEmpty()
        }

    @Test
    fun `creating an observation with only required fields succeeds with nulls for the rest`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response =
                client.post("/api/prices/observations") {
                    contentType(ContentType.Application.Json)
                    setBody(createRequestBody(itemId = 2021))
                }

            assertThat(response.status).isEqualTo(HttpStatusCode.Created)
            val created = json.decodeFromString<ObservationResponse>(response.bodyAsText())
            assertThat(created.itemId).isEqualTo(2021)
            assertThat(created.medianPrice).isNull()
            assertThat(created.comment).isNull()
            assertThat(created.captureUid).isNull()
        }

    @Test
    fun `itemId filter only returns observations for that item`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            client.post("/api/prices/observations") {
                contentType(ContentType.Application.Json)
                setBody(createRequestBody(itemId = 1))
            }
            client.post("/api/prices/observations") {
                contentType(ContentType.Application.Json)
                setBody(createRequestBody(itemId = 2))
            }

            val response = client.get("/api/prices/observations?itemId=1")

            val results = json.decodeFromString<List<ObservationResponse>>(response.bodyAsText())
            assertThat(results).hasSize(1)
            assertThat(results.single().itemId).isEqualTo(1)
        }

    @Test
    fun `limit above 200 is clamped`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            repeat(3) {
                client.post("/api/prices/observations") {
                    contentType(ContentType.Application.Json)
                    setBody(createRequestBody(itemId = 42))
                }
            }

            val response = client.get("/api/prices/observations?itemId=42&limit=500")

            val results = json.decodeFromString<List<ObservationResponse>>(response.bodyAsText())
            assertThat(results).hasSize(3)
        }

    @Test
    fun `deleting a missing observation returns 404`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.delete("/api/prices/observations/999999")

            assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        }

    @Test
    fun `deleting an existing observation removes it`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val created =
                json.decodeFromString<ObservationResponse>(
                    client
                        .post("/api/prices/observations") {
                            contentType(ContentType.Application.Json)
                            setBody(createRequestBody(itemId = 7))
                        }.bodyAsText()
                )

            val deleteResponse = client.delete("/api/prices/observations/${created.id}")
            val listResponse = client.get("/api/prices/observations?itemId=7")

            assertThat(deleteResponse.status).isEqualTo(HttpStatusCode.NoContent)
            assertThat(json.decodeFromString<List<ObservationResponse>>(listResponse.bodyAsText())).isEmpty()
        }

    @Test
    fun `updating prices appends the correction marker without losing the prior comment`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val created =
                json.decodeFromString<ObservationResponse>(
                    client
                        .post("/api/prices/observations") {
                            contentType(ContentType.Application.Json)
                            setBody(createRequestBody(itemId = 7).dropLast(1) + ""","comment":"seen at HDV"}""")
                        }.bodyAsText()
                )

            val response =
                client.patch("/api/prices/observations/${created.id}/prices") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"minPrice":90,"avgPrice":120}""")
                }

            val updated = json.decodeFromString<ObservationResponse>(response.bodyAsText())
            assertThat(updated.minPrice).isEqualTo(90)
            assertThat(updated.avgPrice).isEqualTo(120)
            assertThat(updated.comment).isEqualTo("seen at HDV[corrected_manually]")
        }

    @Test
    fun `flagging an observation overwrites its comment entirely`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val created =
                json.decodeFromString<ObservationResponse>(
                    client
                        .post("/api/prices/observations") {
                            contentType(ContentType.Application.Json)
                            setBody(createRequestBody(itemId = 7).dropLast(1) + ""","comment":"old note"}""")
                        }.bodyAsText()
                )

            val response =
                client.patch("/api/prices/observations/${created.id}/flag") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"motif":"outlier"}""")
                }

            val flagged = json.decodeFromString<ObservationResponse>(response.bodyAsText())
            assertThat(flagged.comment).isEqualTo("[outlier]")
            assertThat(flagged.comment).doesNotContain("old note")
        }

    @Test
    fun `latest observation for an item is the most recently created one`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            client.post("/api/prices/observations") {
                contentType(ContentType.Application.Json)
                setBody(createRequestBody(itemId = 55, minPrice = 100))
            }
            val second =
                json.decodeFromString<ObservationResponse>(
                    client
                        .post("/api/prices/observations") {
                            contentType(ContentType.Application.Json)
                            setBody(createRequestBody(itemId = 55, minPrice = 200))
                        }.bodyAsText()
                )

            val response = client.get("/api/prices/55/latest")

            val latest = json.decodeFromString<ObservationResponse>(response.bodyAsText())
            assertThat(latest.id).isEqualTo(second.id)
            assertThat(latest.minPrice).isEqualTo(200)
        }

    @Test
    fun `latest observation for an item with no history returns 404`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val response = client.get("/api/prices/123456/latest")

            assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        }

    @Test
    fun `two observations with null captureUid can both be created`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val first =
                client.post("/api/prices/observations") {
                    contentType(ContentType.Application.Json)
                    setBody(createRequestBody(itemId = 9, captureUid = null))
                }
            val second =
                client.post("/api/prices/observations") {
                    contentType(ContentType.Application.Json)
                    setBody(createRequestBody(itemId = 9, captureUid = null))
                }

            assertThat(first.status).isEqualTo(HttpStatusCode.Created)
            assertThat(second.status).isEqualTo(HttpStatusCode.Created)
        }

    @Test
    fun `a duplicate non-null captureUid is rejected by the partial unique index`() =
        testApplication {
            application { module(dbPath = tempDbPath()) }

            val first =
                client.post("/api/prices/observations") {
                    contentType(ContentType.Application.Json)
                    setBody(createRequestBody(itemId = 9, captureUid = "session_9"))
                }
            val duplicate =
                client.post("/api/prices/observations") {
                    contentType(ContentType.Application.Json)
                    setBody(createRequestBody(itemId = 9, captureUid = "session_9"))
                }

            assertThat(first.status).isEqualTo(HttpStatusCode.Created)
            assertThat(duplicate.status).isNotEqualTo(HttpStatusCode.Created)
        }
}
