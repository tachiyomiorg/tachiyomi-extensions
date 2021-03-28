package eu.kanade.tachiyomi.extension.en.xcalibrscans

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class xCaliBRScans : WPMangaStream("xCaliBR Scans", "https://xcalibrscans.com", "en") {
    private val rateLimitInterceptor = RateLimitInterceptor(2)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageSelector)
            .mapIndexed { i, img -> Page(i, "", img.attr("data-src")) }
    }

}
