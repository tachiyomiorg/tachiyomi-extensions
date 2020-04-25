package eu.kanade.tachiyomi.extension.id.mangakita

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaKita : ParsedHttpSource() {
    override val name = "MangaKita"

    override val baseUrl = "https://mangakita.net"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "a.series:not([href*=novel])"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list", headers)

    // The page I'm getting these from has no thumbnails
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
    }

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesSelector() = "div.latestSeries"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").first().attr("href"))
        title = element.select("p.clamp2").first().ownText()
        thumbnail_url = element.select("img").attr("src")
    }

    override fun latestUpdatesNextPageSelector() = "[rel=next]"

    override fun searchMangaSelector() = "div.result-search"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page/?s=$query", headers)

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("span.titlex > a").attr("href"))
        title = element.select("span.titlex > a").text()
        thumbnail_url = element.select("img").attr("src")
    }

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select("div.row > div").get(5).ownText().trim()
        genre = document.select("[rel=tag]").joinToString { it.text() }
        status = document.select("div.row > div").get(10).ownText().let {
            parseStatus(it)
        }
        thumbnail_url = document.select("div#wrap img").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "span.chapterLabel > a:not([href*=pdf])"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text()
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select(".mangaimg").mapIndexed { i, element ->
            val image = element.attr("src")
            if (image != "") {
                pages.add(Page(i, "", image))
            }
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
