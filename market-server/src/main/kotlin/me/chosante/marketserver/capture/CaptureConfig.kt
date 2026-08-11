package me.chosante.marketserver.capture

// Defaults mirror the values hardcoded in the (untouched, external) capture_hdv.ps1 today. This
// feature is inherently maintainer-machine-specific (like bdata-extractor/generateAssets), not a
// general end-user dependency -- machines without this setup get a clear "prerequisite missing"
// error from CaptureService.start(), not a confusing exception. A data class (not an env-reading
// object) so tests can inject a temp scriptsDir + stub scripts instead of the real pipeline.
data class CaptureConfig(
    val scriptsDir: String =
        System.getenv("MARKET_SERVER_CAPTURE_SCRIPTS_DIR")
            ?: """C:\Users\adrie\Claude\Projects\AgentWakfu\scraper""",
    val launcherPath: String =
        System.getenv("MARKET_SERVER_ANKAMA_LAUNCHER_PATH")
            ?: """C:\Users\adrie\AppData\Local\Programs\Ankama Launcher\Ankama Launcher.exe""",
    val pythonExecutable: String =
        System.getenv("MARKET_SERVER_PYTHON_EXECUTABLE") ?: "python",
    val tsharkPath: String =
        System.getenv("MARKET_SERVER_TSHARK_PATH") ?: """C:\Program Files\Wireshark\tshark.exe""",
    // How long to wait after killing existing Ankama processes for their TCP connections to close
    // (matches capture_hdv.ps1's own `Start-Sleep -Seconds 8`). Overridable so tests don't pay it.
    val ankamaKillWaitMs: Long = 8_000L,
) {
    val captureScript: String get() = "$scriptsDir\\wakfu_capture_pcap.py"
    val importScript: String get() = "$scriptsDir\\import_prices.py"

    val javaKeylogPath: String get() = "${System.getenv("TEMP")}\\wakfu_tls_keys.log"
    val chromeKeylogPath: String get() = "${System.getenv("TEMP")}\\zaap_chrome_tls_keys.log"
}
