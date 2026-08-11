package me.chosante.marketserver.routes

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import me.chosante.marketserver.capture.CaptureConfig
import me.chosante.marketserver.capture.CaptureService
import me.chosante.marketserver.dto.CaptureStatusResponse
import me.chosante.marketserver.module
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files

// Same Windows-only rationale as CaptureServiceTest -- this whole feature only makes sense on the
// maintainer's Windows machine, and CI runs on ubuntu-latest.
@EnabledOnOs(OS.WINDOWS)
class CaptureRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun tempDbPath(): String {
        val dbFile = Files.createTempFile("market-test", ".db")
        Files.deleteIfExists(dbFile)
        return dbFile.toString()
    }

    @Test
    fun `GET status returns idle before any capture is started`() =
        testApplication {
            application { module(dbPath = tempDbPath(), captureService = CaptureService(CaptureConfig())) }

            val response = client.get("/api/capture/status")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val status = json.decodeFromString<CaptureStatusResponse>(response.bodyAsText())
            assertThat(status.phase).isEqualTo("idle")
        }

    @Test
    fun `POST start reports a clear error when the capture pipeline is not set up`() =
        testApplication {
            val scriptsDir = Files.createTempDirectory("capture-routes-test")
            val config =
                CaptureConfig(
                    scriptsDir = scriptsDir.toString(),
                    launcherPath = scriptsDir.resolve("NoLauncher.exe").toString(),
                    tsharkPath = scriptsDir.resolve("no-tshark.exe").toString()
                )
            application { module(dbPath = tempDbPath(), captureService = CaptureService(config)) }

            val response = client.post("/api/capture/start")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val status = json.decodeFromString<CaptureStatusResponse>(response.bodyAsText())
            assertThat(status.phase).isEqualTo("error")
            assertThat(status.message).isNotNull()
        }

    @Test
    fun `POST stop while idle is a no-op`() =
        testApplication {
            application { module(dbPath = tempDbPath(), captureService = CaptureService(CaptureConfig())) }

            val response = client.post("/api/capture/stop")

            val status = json.decodeFromString<CaptureStatusResponse>(response.bodyAsText())
            assertThat(status.phase).isEqualTo("idle")
        }
}
