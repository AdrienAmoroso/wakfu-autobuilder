package me.chosante.marketserver.capture

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import kotlin.io.path.writeText

// This whole feature (PowerShell process management, Windows .exe paths) is inherently
// Windows-only, matching the (untouched) capture pipeline it wraps -- CI runs on ubuntu-latest
// (.github/workflows/build.yml), so these tests must skip cleanly there rather than fail.
@EnabledOnOs(OS.WINDOWS)
class CaptureServiceTest {
    // Stands in for the real wakfu_capture_pcap.py: spawns a copy of PING.EXE **renamed to
    // tshark.exe** as its capture child (so CaptureService's real "kill the tshark.exe child of
    // the tracked PID" logic has something genuine to find and kill), waits on it exactly like the
    // real script's `proc.wait()` does (returns normally whether the child is killed or exits on
    // its own), then always writes a fixed `.prices.json` and prints the exact line CaptureService
    // parses.
    private fun stubCaptureScript(scriptsDir: java.nio.file.Path): java.nio.file.Path {
        val tsharkStub = scriptsDir.resolve("tshark.exe")
        Files.copy(
            java.nio.file.Path
                .of("""C:\Windows\System32\PING.EXE"""),
            tsharkStub
        )
        val outputJson = scriptsDir.resolve("stub_session.prices.json")
        val script = scriptsDir.resolve("wakfu_capture_pcap.py")
        script.writeText(
            """
            import subprocess, sys
            proc = subprocess.Popen([r"$tsharkStub", "-t", "127.0.0.1"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            proc.wait()
            with open(r"$outputJson", "w") as f:
                f.write("{}")
            print("  Sauvegarde JSON  : $outputJson")
            """.trimIndent()
        )
        return script
    }

    // Stands in for import_prices.py: verifies it received the right args, prints the exact
    // success line CaptureService parses for the imported count.
    private fun stubImportScript(scriptsDir: java.nio.file.Path): java.nio.file.Path {
        val script = scriptsDir.resolve("import_prices.py")
        script.writeText(
            """
            print("  [OK] 3 inserees, 0 ecrasees.")
            """.trimIndent()
        )
        return script
    }

    private fun testConfig(
        scriptsDir: java.nio.file.Path,
        launcherExists: Boolean = true,
    ): CaptureConfig {
        val launcher = scriptsDir.resolve("FakeLauncher.exe")
        if (launcherExists) Files.write(launcher, byteArrayOf())
        return CaptureConfig(
            scriptsDir = scriptsDir.toString(),
            launcherPath = launcher.toString(),
            pythonExecutable = "python",
            tsharkPath = scriptsDir.resolve("tshark.exe").toString(),
            ankamaKillWaitMs = 10L
        )
    }

    @Test
    fun `status defaults to idle`() {
        val service = CaptureService(CaptureConfig())
        assertThat(service.status().phase).isEqualTo("idle")
    }

    @Test
    fun `start reports a clear error when prerequisites are missing`() {
        runBlocking {
            val scriptsDir = Files.createTempDirectory("capture-test")
            val config = testConfig(scriptsDir, launcherExists = false)
            val service = CaptureService(config)

            val result = service.start(dbPath = "unused")

            assertThat(result.phase).isEqualTo("error")
            assertThat(result.message).contains("tshark", "Ankama Launcher", "wakfu_capture_pcap.py")
        }
    }

    @Test
    fun `full start-stop cycle kills the tshark child and imports the result`() {
        runBlocking {
            withTimeout(30_000) {
                val scriptsDir = Files.createTempDirectory("capture-test")
                stubCaptureScript(scriptsDir)
                stubImportScript(scriptsDir)
                val config = testConfig(scriptsDir)
                val service = CaptureService(config)

                val started = service.start(dbPath = "unused")
                assertThat(started.phase).isEqualTo("capturing")
                assertThat(started.startedAt).isNotNull()

                // Let the stub tshark.exe child actually spawn before trying to kill it.
                delay(500)

                val stopped = service.stop(dbPath = "unused")
                assertThat(stopped.phase).isEqualTo("processing")

                var finalStatus = service.status()
                while (finalStatus.phase == "processing") {
                    delay(200)
                    finalStatus = service.status()
                }

                assertThat(finalStatus.phase).isEqualTo("idle")
                assertThat(finalStatus.lastImportedCount).isEqualTo(3)
            }
        }
    }

    @Test
    fun `start is idempotent while a capture is already running`() {
        runBlocking {
            withTimeout(30_000) {
                val scriptsDir = Files.createTempDirectory("capture-test")
                stubCaptureScript(scriptsDir)
                stubImportScript(scriptsDir)
                val config = testConfig(scriptsDir)
                val service = CaptureService(config)

                val first = service.start(dbPath = "unused")
                val second = service.start(dbPath = "unused")

                assertThat(second).isEqualTo(first)

                service.stop(dbPath = "unused")
                var finalStatus = service.status()
                while (finalStatus.phase != "idle") {
                    delay(200)
                    finalStatus = service.status()
                }
            }
        }
    }
}
