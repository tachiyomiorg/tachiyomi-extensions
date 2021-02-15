package eu.kanade.tachiyomi.extension.id.mangaindonesia

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class MangaIndonesia : WPMangaStream("MangaIndonesia", "https://mangaindonesia.net", "id") {
    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun popularMangaRequest(page: Int): Request {
//        return GET("$baseUrl/popular" + if (page > 1) "/${(page - 1) * 30}" else "", headers)
//        return GET("$baseUrl/$popularPath" + if (page > 1) "?page=$page" else "", headers)
        return GET("$baseUrl/update/" + if (page > 1) "?page=$page" else "", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }
    override fun latestUpdatesSelector() = ".listupd:not(.project) .uta .imgu"
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("a img").imgAttr()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/page/$page/$query", headers)
    }
    override fun chapterListSelector() = "div.bxcl ul li:has(span)"
    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("a").attr("href"))
        chapter.name = element.select("a").text()
        chapter.date_upload = element.select("span.dt").firstOrNull()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }
}
