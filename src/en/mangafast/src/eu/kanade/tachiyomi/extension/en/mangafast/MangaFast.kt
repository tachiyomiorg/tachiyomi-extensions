package eu.kanade.tachiyomi.extension.en.mangafast

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaFast : ParsedHttpSource() {
    override val name = "MangaFast"

    override val baseUrl = "https://mangafast.net"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list-manga/", headers)

    override fun popularMangaSelector() = "li.ranking1"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("h4").text().trim()
        thumbnail_url = element.select("img").attr("abs:data-src").substringBeforeLast("resize")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/read/page/$page", headers)

    override fun latestUpdatesSelector() = "div.ls5"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("h3").text().trim()
        thumbnail_url = element.select("img").attr("abs:data-src")
    }

    override fun latestUpdatesNextPageSelector() = "a.next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page/?s=$query", headers)

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select("td[itemprop=creator]").text().trim()
        status = parseStatus(document.select(".inftable").text())
        genre = document.select("a[itemprop=genre]").joinToString { it.text() }
        description = document.select("[itemprop=description]").first().text().trim()
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "tr:has(td.tgs)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").attr("title")
        date_upload = dateFormat.parse(element.select("td.tgs").text().trim())?.time ?: 0
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.chp2 > img").mapIndexed { i, element ->
            var url = element.attr("abs:data-src")

            if (url.isEmpty()) {
                url = element.attr("abs:src")
            }

            Page(i, "", url)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
