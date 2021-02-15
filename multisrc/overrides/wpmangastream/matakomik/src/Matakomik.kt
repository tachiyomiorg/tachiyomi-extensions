package eu.kanade.tachiyomi.extension.id.matakomik

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class Matakomik : WPMangaStream("Matakomik", "https://matakomik.com", "id") {
    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea a").mapIndexed { i, a ->
            Page(i, "", a.attr("abs:href"))
        }
    }
}
