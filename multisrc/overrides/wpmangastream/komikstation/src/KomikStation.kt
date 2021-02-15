package eu.kanade.tachiyomi.extension.id.komikstation

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor

class KomikStation : WPMangaStream("Komik Station", "https://komikstation.com", "id") {
    // Formerly "Komik Station (WP Manga Stream)"
    override val id = 6148605743576635261

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

}
