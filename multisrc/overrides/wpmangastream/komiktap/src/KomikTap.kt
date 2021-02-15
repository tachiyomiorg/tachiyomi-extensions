package eu.kanade.tachiyomi.extension.id.komiktap

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class KomikTap : WPMangaStream("KomikTap", "https://komiktap.in/", "id") {
    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()
}
