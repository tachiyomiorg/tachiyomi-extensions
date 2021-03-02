package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class Kiryuu : WPMangaStream("Kiryuu", "https://kiryuu.co", "id") {
    // Formerly "Kiryuu (WP Manga Stream)"
    override val id = 3639673976007021338

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun chapterListSelector() = "div.bxcl ul li"

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea img").map { it.attr("abs:src") }
            .filterNot { it.substringAfterLast("/").contains(Regex("""(filerun|photothumb\.db)""")) }
            .mapIndexed { i, image -> Page(i, "", image) }
    }
}
