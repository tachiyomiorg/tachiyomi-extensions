package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class Kiryuu : WPMangaReader("Kiryuu", "https://kiryuu.id", "id") {
    // Formerly "Kiryuu (WP Manga Stream)"
    override val id = 3639673976007021338

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override val hasProjectPage = true
}
