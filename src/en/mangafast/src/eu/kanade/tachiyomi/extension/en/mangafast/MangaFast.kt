package eu.kanade.tachiyomi.extension.en.mangafast

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaFast : ParsedHttpSource() {
    override val name = "MangaFast"

    override val baseUrl = "https://mangafast.net"

    override val lang = "en"

    override val supportsLatest = true

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/list-manga" + if (page > 1) "/page/$page" else "", headers)
    }

    override fun popularMangaSelector() = ".daftar .bge"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select(".bgei a").attr("href"))
        title = element.select(".kan h3").text().trim()
        thumbnail_url = element.select(".bgei img").attr("src")
    }

    override fun popularMangaNextPageSelector() = ".btn-w a:contains(Next »)"

    // latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl", headers)

    override fun latestUpdatesSelector() = ".ls8w div.ls8"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select(".ls8j a").attr("href"))
        title = element.select("h4").text().trim()
        thumbnail_url = element.select(".ls8v img").attr("src")
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page/?s=$query", headers)

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("#Judul h1").text()
        author = document.select("td[itemprop=creator]").text().trim()
        status = parseStatus(document.select(".inftable").text())
        genre = document.select("a[itemprop=genre]").joinToString { it.text() }
        description = document.select("#Judul .desc").first().text().substringAfter(title).substringAfter(". ")
        thumbnail_url = document.select("#Informasi .row img.shadow").first().attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapter list
    override fun chapterListSelector() = ".chapter-link"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select(".text-left").text()
        date_upload = parseDate(element.select(".text-right").text())
    }

    private fun parseDate(text: String): Long {
        return try {
            dateFormat.parse(text.trim())?.time ?: 0L
        } catch (pe: ParseException) { // this can happen for spoiler & release date entries
            0L
        }
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(".content-comic > img").mapIndexed { i, element ->
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
