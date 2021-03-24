package eu.kanade.tachiyomi.extension.en.mangasee

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.mangaseelife.MangaSeeLife
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MangaSee : MangaSeeLife("MangaSee", "https://mangasee123.com", "en") {

    override val id: Long = 9

    private val rateLimitInterceptor = RateLimitInterceptor(1, 2)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor)
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()
}
