package eu.kanade.tachiyomi.extension.tr.liebeschneehiver

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class LiebeSchneeHiver : WPMangaStream(
    "Liebe Schnee Hiver",
    "https://www.liebeschneehiver.com",
    "tr",
    SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("tr"))
) {
    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

}
