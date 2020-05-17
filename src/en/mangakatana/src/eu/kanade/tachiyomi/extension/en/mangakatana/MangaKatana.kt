package eu.kanade.tachiyomi.extension.en.mangakatana

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

class MangaKatana : ParsedHttpSource() {
    override val name = "MangaKatana"

    override val baseUrl = "https://mangakatana.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun latestUpdatesSelector() = "div#book_list > div.item"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.text > h3 > a").attr("href"))
        title = element.select("div.text > h3 > a").text()
        thumbnail_url = element.select("img").attr("abs:data-src")
    }

    override fun latestUpdatesNextPageSelector() = ".next.page-numbers"

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page", headers)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page?search=$query&search_by=book_name", headers)

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select(".author").text()
        description = document.select(".summary > p").text()
        status = parseStatus(document.select(".value.status").text())
        genre = document.select(".genres > a").joinToString { it.text() }
        thumbnail_url = document.select(".cover > img").attr("abs:data-src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "tr:has(.chapter)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").text()
        date_upload = dateFormat.parse(element.select(".update_time").text()).time ?: 0
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM-dd-yyyy", Locale.US)
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val html = document.toString()

        // Thanks to https://github.com/manga-py/manga-py
        val regex = Regex("var\\s+\\w+\\s?=\\s?(\\[['\"].+?['\"]).?\\]\\s?;")
        val match = regex.find(html)?.destructured?.toList()?.get(0)?.removePrefix("[")

        return match!!.split(",").mapIndexed { i, string ->
            Page(i, "", string.reversed().replace("\"", "").replace("'", ""))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
