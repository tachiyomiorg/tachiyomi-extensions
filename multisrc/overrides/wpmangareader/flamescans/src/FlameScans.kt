package eu.kanade.tachiyomi.extension.en.flamescans

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FlameScans : WPMangaReader("Flame Scans", "https://flamescans.org", "en", "/series/") {
    private val rateLimitInterceptor = RateLimitInterceptor(1)
    private val userAgent = "Tachiyomi Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()
}

