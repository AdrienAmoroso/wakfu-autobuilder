package me.chosante.marketserver.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
            assertThat(response.bodyAsText()).contains("\"equipmentId\":2021")
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
