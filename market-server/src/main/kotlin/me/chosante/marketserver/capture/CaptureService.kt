package me.chosante.marketserver.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.chosante.marketserver.dto.CaptureStatusResponse
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

private const val PHASE_IDLE = "idle"
private const val PHASE_CAPTURING = "capturing"
private const val PHASE_PROCESSING = "processing"
private const val PHASE_ERROR = "error"

// If parsing+importing hasn't finished this long after tshark was killed, something is genuinely
// stuck (e.g. a huge pcap, or a PowerShell/process-tree issue like the one that motivated this
// timeout in the first place) -- surface a clear error instead of hanging the UI forever.
private val PROCESSING_TIMEOUT = 5.minutes

// Orchestrates the EXISTING, untouched capture pipeline
// (C:\Users\adrie\Claude\Projects\AgentWakfu\scraper\ by default -- see CaptureConfig) as
// subprocesses; never modifies those scripts. "Stop" works by killing the tshark.exe child process
// rather than fighting Windows' Ctrl+C/SIGINT delivery: wakfu_capture_pcap.py's `proc.wait()` on
// tshark returns normally either way, and its own (unmodified) parsing/JSON-writing logic runs
// regardless of how tshark exited. A class (not an object) so tests can inject a CaptureConfig
// pointing at a stub scripts directory instead of the real pipeline.
class CaptureService(
    private val config: CaptureConfig = CaptureConfig(),
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    @Volatile private var current = CaptureStatusResponse(phase = PHASE_IDLE)

    @Volatile private var trackedProcess: Process? = null

    @Volatile private var outputBuffer = StringBuilder()

    // Live feedback: while capturing/processing, the status response's `message` is computed
    // fresh from the tracked process's own captured stdout on every poll -- this is what makes the
    // GUI's status bar show real, changing progress instead of a static "please wait" the whole
    // time, without needing a separate ticker job (the GUI already polls status every few seconds).
    fun status(): CaptureStatusResponse =
        if (current.phase == PHASE_CAPTURING || current.phase == PHASE_PROCESSING) {
            current.copy(message = lastMeaningfulLine())
        } else {
            current
        }

    suspend fun start(dbPath: String): CaptureStatusResponse =
        stateMutex.withLock {
            if (current.phase == PHASE_CAPTURING || current.phase == PHASE_PROCESSING) {
                return@withLock current
            }

            val missing =
                listOfNotNull(
                    "tshark" to config.tsharkPath,
                    "the Ankama Launcher" to config.launcherPath,
                    "wakfu_capture_pcap.py" to config.captureScript
                ).filterNot { (_, path) -> File(path).isFile }
            if (missing.isNotEmpty()) {
                current =
                    CaptureStatusResponse(
                        phase = PHASE_ERROR,
                        message = missing.joinToString("; ") { (label, path) -> "$label not found at $path" }
                    )
                return@withLock current
            }

            runPowerShellScript(killAnkamaProcessesScript())
            delay(config.ankamaKillWaitMs)
            File(config.chromeKeylogPath).delete()

            outputBuffer = StringBuilder()
            val processBuilder =
                ProcessBuilder(
                    config.pythonExecutable,
                    config.captureScript,
                    "--chrome-keylog",
                    config.chromeKeylogPath,
                    "--java-keylog",
                    config.javaKeylogPath,
                    "--start-launcher",
                    config.launcherPath
                ).directory(File(config.scriptsDir))
                    .redirectErrorStream(true)
            processBuilder.environment()["SSLKEYLOGFILE"] = config.chromeKeylogPath

            val process =
                try {
                    processBuilder.start()
                } catch (e: IOException) {
                    current = CaptureStatusResponse(phase = PHASE_ERROR, message = "Failed to launch capture: ${e.message}")
                    return@withLock current
                }
            trackedProcess = process
            serviceScope.launch {
                process.inputStream.bufferedReader().forEachLine { outputBuffer.appendLine(it) }
            }

            current = CaptureStatusResponse(phase = PHASE_CAPTURING, startedAt = System.currentTimeMillis())
            current
        }

    suspend fun stop(dbPath: String): CaptureStatusResponse =
        stateMutex.withLock {
            val process = trackedProcess
            if (current.phase != PHASE_CAPTURING || process == null) {
                return@withLock current
            }

            val killOutput = runPowerShellScript(killTsharkChildScript(process.pid()))
            outputBuffer.appendLine("[market-server] kill tshark: $killOutput")

            current = current.copy(phase = PHASE_PROCESSING)
            val statusAtStop = current
            serviceScope.launch { finishProcessing(process, dbPath) }
            statusAtStop
        }

    private suspend fun finishProcessing(
        process: Process,
        dbPath: String,
    ) {
        val exited =
            withTimeoutOrNull(PROCESSING_TIMEOUT) {
                withContext(Dispatchers.IO) { process.waitFor() }
                true
            }
        trackedProcess = null

        val result =
            if (exited == null) {
                // Safety net: the tracked process never exited within the timeout (e.g. the
                // tshark-kill silently failed for some new reason) -- force the whole tree down and
                // surface a clear, diagnosable error instead of hanging the UI forever.
                process.descendants().forEach { it.destroy() }
                process.destroyForcibly()
                CaptureStatusResponse(
                    phase = PHASE_ERROR,
                    message =
                        "Capture process didn't exit within ${PROCESSING_TIMEOUT.inWholeMinutes} min -- " +
                            "it was force-killed. Last output: " + outputBuffer.toString().takeLast(500)
                )
            } else {
                val output = outputBuffer.toString()
                val jsonPathLine = output.lineSequence().firstOrNull { "Sauvegarde JSON" in it }
                if (jsonPathLine == null) {
                    CaptureStatusResponse(phase = PHASE_ERROR, message = "No prices captured -- " + output.takeLast(500))
                } else {
                    val jsonPath = jsonPathLine.substringAfter(":").trim()
                    runImport(jsonPath, dbPath)
                }
            }
        stateMutex.withLock { current = result }
    }

    private suspend fun runImport(
        jsonPath: String,
        dbPath: String,
    ): CaptureStatusResponse =
        withContext(Dispatchers.IO) {
            val processBuilder =
                ProcessBuilder(
                    config.pythonExecutable,
                    config.importScript,
                    jsonPath,
                    "--db",
                    dbPath
                ).directory(File(config.scriptsDir))
                    .redirectErrorStream(true)
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                return@withContext CaptureStatusResponse(phase = PHASE_ERROR, message = "Import failed -- " + output.takeLast(500))
            }
            val importedCount =
                Regex("""\[OK]\s+(\d+)\s+inserees""")
                    .find(output)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            CaptureStatusResponse(phase = PHASE_IDLE, lastImportedCount = importedCount)
        }

    private fun lastMeaningfulLine(): String? =
        outputBuffer
            .toString()
            .lineSequence()
            .lastOrNull { it.isNotBlank() }
            ?.trim()

    private fun killAnkamaProcessesScript() =
        """
        ${'$'}procs = @()
        foreach (${'$'}name in @('Ankama Launcher','zaap','wakfu')) { ${'$'}procs += Get-Process -Name ${'$'}name -ErrorAction SilentlyContinue }
        Get-Process | ForEach-Object {
            try {
                ${'$'}path = ${'$'}_.MainModule.FileName
                if (${'$'}path -and (${'$'}path -like '*Ankama*' -or ${'$'}path -like '*zaap*')) { ${'$'}procs += ${'$'}_ }
            } catch {}
        }
        ${'$'}procs | Select-Object -Unique Id | ForEach-Object {
            try { Stop-Process -Id ${'$'}_.Id -Force -ErrorAction SilentlyContinue } catch {}
        }
        """.trimIndent()

    private fun killTsharkChildScript(parentPid: Long) =
        """
        ${'$'}victims = Get-CimInstance Win32_Process -Filter "Name='tshark.exe' AND ParentProcessId=$parentPid"
        foreach (${'$'}v in ${'$'}victims) {
            Stop-Process -Id ${'$'}v.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Output "killed tshark pid ${'$'}(${'$'}v.ProcessId)"
        }
        if (${'$'}victims.Count -eq 0) { Write-Output "no tshark.exe child found for parent $parentPid" }
        """.trimIndent()

    // Runs a PowerShell script by writing it to a temp .ps1 file and invoking `-File`, NOT
    // `-Command <inline string>`. This is the actual root-cause fix for a real bug hit in
    // production: when ProcessBuilder passes args as an array (bypassing cmd.exe's own
    // re-parsing), powershell.exe's OWN argv handling for `-Command` mangles embedded double
    // quotes -- e.g. `-Filter "Name='x' AND Y=1"` gets corrupted into separate tokens, so the
    // WMI filter silently fails to parse and the whole kill becomes a no-op (reproduced and
    // confirmed via a minimal ProcessBuilder repro before this fix). `-File` reads the script
    // straight off disk, with no re-parsing/escaping layers to fight.
    private suspend fun runPowerShellScript(script: String): String =
        withContext(Dispatchers.IO) {
            val scriptFile = File.createTempFile("market-server-capture-", ".ps1")
            try {
                scriptFile.writeText(script)
                val process =
                    ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", scriptFile.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output.trim()
            } finally {
                scriptFile.delete()
            }
        }
}
