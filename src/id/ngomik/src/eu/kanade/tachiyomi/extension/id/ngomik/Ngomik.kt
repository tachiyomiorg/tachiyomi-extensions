package eu.kanade.tachiyomi.extension.id.ngomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Ngomik : ParsedHttpSource() {

    override val name = "Ngomik"

    override val baseUrl = "https://ngomik.in"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.imgu > a.series"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/daftar-komik/page/$page/?order=popular", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
        thumbnail_url = element.select("img").attr("src")
    }

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/daftar-komik/page/$page/?order=update", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page/?s=$query", headers)

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1[itemprop=headline]").text()
        author = document.select("div.listinfo li").get(2).text().removePrefix("Author: ")
        description = document.select(".desc").text()
        genre = document.select("div.gnr > a").joinToString { it.text() }
        status = document.select("div.listinfo li").get(3).text().let {
            parseStatus(it)
        }
        thumbnail_url = document.select("div[itemprop=image] > img").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.lch > a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea img").mapIndexed { i, element ->
            Page(i, "", element.attr("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
