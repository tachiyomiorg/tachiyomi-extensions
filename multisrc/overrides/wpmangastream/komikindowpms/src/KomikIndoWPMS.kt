package eu.kanade.tachiyomi.extension.id.komikindowpms

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class KomikIndoWPMS : WPMangaStream("Komik Indo", "https://www.komikindo.web.id", "id") {
    // Formerly "Komik Indo (WP Manga Stream)"
    override val id = 1481562643469779882

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

}
