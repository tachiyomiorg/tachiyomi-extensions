package eu.kanade.tachiyomi.extension.en.mangaclash

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MangaClash : Madara(
    "Manga Clash",
    "https://mangaclash.com",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yy", Locale.US)
) {
    private val rateLimitInterceptor = RateLimitInterceptor(1)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()
}
