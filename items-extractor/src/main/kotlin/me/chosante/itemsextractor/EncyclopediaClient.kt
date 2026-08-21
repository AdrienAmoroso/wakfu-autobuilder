package me.chosante.itemsextractor

import kotlinx.coroutines.delay
import java.io.File
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP access to the Ankama Wakfu encyclopedia -- the same defeat-the-403 approach as
 * spells-extractor's `EncyclopediaClient` (session-cookie priming + a real browser User-Agent), not
 * shared as a library since each extractor module is a standalone tool. See that module's client for
 * the full rationale.
 *
 * Resumable: every fetched page is cached under [cacheDir] keyed by URL, so a re-run only fetches
 * what is missing.
 */
class EncyclopediaClient(
    private val cacheDir: File,
    private val throttleMillis: Long = 350,
    private val maxAttempts: Int = 4,
) {
    private val cookieManager = CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }

    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build()

    init {
        cacheDir.mkdirs()
    }

    /** Establishes the session cookie by fetching the (English) encyclopedia root once. */
    suspend fun prime() {
        fetch("$BASE/en/mmorpg/encyclopedia", cacheKey = "_prime", useCache = false)
    }

    /** GETs [url], returning the page body (or `null` if every attempt failed). Cached by [cacheKey]. */
    suspend fun fetch(
        url: String,
        cacheKey: String,
        useCache: Boolean = true,
    ): String? {
        val cacheFile = File(cacheDir, "$cacheKey.html")
        if (useCache && cacheFile.isFile && cacheFile.length() > MIN_VALID_BYTES) {
            return cacheFile.readText()
        }

        var attempt = 0
        var backoff = 500L
        while (attempt < maxAttempts) {
            attempt++
            try {
                delay(throttleMillis)
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9")
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200 && response.body().length > MIN_VALID_BYTES) {
                    if (useCache) cacheFile.writeText(response.body())
                    return response.body()
                }
                System.err.println("  ! $url -> HTTP ${response.statusCode()} (attempt $attempt/$maxAttempts)")
            } catch (e: Exception) {
                System.err.println("  ! $url -> ${e.javaClass.simpleName}: ${e.message} (attempt $attempt/$maxAttempts)")
            }
            delay(backoff)
            backoff *= 2
        }
        return null
    }

    /** Downloads a binary asset (an item icon) straight to [destination]. No-op if it already exists. */
    suspend fun download(
        url: String,
        destination: File,
    ): Boolean {
        if (destination.isFile && destination.length() > 0) return true
        var attempt = 0
        var backoff = 500L
        while (attempt < maxAttempts) {
            attempt++
            try {
                delay(throttleMillis)
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
                if (response.statusCode() == 200 && response.body().isNotEmpty()) {
                    destination.parentFile.mkdirs()
                    destination.writeBytes(response.body())
                    return true
                }
            } catch (_: Exception) {
                // retried below
            }
            delay(backoff)
            backoff *= 2
        }
        return false
    }

    companion object {
        const val BASE = "https://www.wakfu.com"

        // A short page is the SSO/404 shell, not a real listing page; treat as a miss.
        private const val MIN_VALID_BYTES = 5_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
