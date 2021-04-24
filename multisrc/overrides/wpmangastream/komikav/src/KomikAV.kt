package eu.kanade.tachiyomi.extension.id.komikav

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class KomikAV : WPMangaStream(
    "Komik AV (WP Manga Stream)",
    "https://komikav.com",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))
) {
    // Formerly "Komik AV (WP Manga Stream)"
    override val id = 7875815514004535629

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }
}
