package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AsuraScans : WPMangaStream("AsuraScans", "https://asurascans.com", "en") {
    private val rateLimitInterceptor = RateLimitInterceptor(2)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()
	
    override val pageSelector = "div.rdminimal img[loading*=lazy]"
}