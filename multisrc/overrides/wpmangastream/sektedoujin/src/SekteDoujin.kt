package eu.kanade.tachiyomi.extension.id.sektedoujin

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class SekteDoujin : WPMangaStream("Sekte Doujin", "https://sektedoujin.xyz", "id", SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))) {
    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()
}

