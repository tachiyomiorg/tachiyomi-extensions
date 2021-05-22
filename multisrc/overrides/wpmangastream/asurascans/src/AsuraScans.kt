package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import okhttp3.OkHttpClient
import okhttp3.Headers
import java.util.concurrent.TimeUnit

class AsuraScans : WPMangaStream("AsuraScans", "https://www.asurascans.com", "en") {
    private val rateLimitInterceptor = RateLimitInterceptor(2)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()
	
    override val pageSelector = "div.rdminimal img[loading*=lazy]"
	
	override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9," +
                "image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        )
        .add("Accept-Language", "en-US, en;q=0.5")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Edg/90.0.818.62")
        .add("Referer", "https://www.google.com")
}