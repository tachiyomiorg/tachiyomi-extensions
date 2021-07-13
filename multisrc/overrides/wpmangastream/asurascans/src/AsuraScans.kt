package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import okhttp3.OkHttpClient
import okhttp3.Headers
import okhttp3.Request
import java.util.concurrent.TimeUnit
import org.jsoup.nodes.Document
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.network.GET


class AsuraScans : WPMangaStream("AsuraScans", "https://www.asurascans.com", "en") {
    private val rateLimitInterceptor = RateLimitInterceptor(1)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers.newBuilder().set("User-Agent", USER_AGENT).build())
    }

    override val pageSelector = "div.rdminimal img[loading*=lazy]"

    // Skip scriptPages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageSelector)
            .filterNot { it.attr("abs:src").isNullOrEmpty() }
            .mapIndexed { i, img -> Page(i, "", img.attr("abs:src")) }
    }

    companion object {
        private const val USER_AGENT = "Tachiyomi Mozilla/5.0 (Linux; U; Android 4.4.2; en-us; " +
            "LGMS323 Build/KOT49I.MS32310c) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.100 Mobile Safari/537.36"
    }
}
