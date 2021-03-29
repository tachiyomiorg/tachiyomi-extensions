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

    override fun popularMangaSelector() = ".list-content .ls4 .ls4v"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val a = element.select("a")
        setUrlWithoutDomain(a.attr("href"))
        title = a.attr("title")
        thumbnail_url = a.select("img").attr("src")
    }

    override fun popularMangaNextPageSelector() = ".btn-w a:contains(Next »)"

    // latest
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = ".ls8w div.ls8 .ls8v"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page/?s=$query", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        val articleTitle = document.select("article header[id=article-title]")
        val articleInfo = document.select("article section[id=article-info]")

        val manga = SManga.create().apply {
            title = articleTitle.select("h1[itemprop=name]").text().trim()
            description = articleTitle.select("p.desc").text().trim()
            thumbnail_url = articleInfo.select("img.shadow").attr("src")
        }
        articleInfo.select("table.inftable tbody tr").forEach {
            val row = it.select("td")
            when (row[0].text()) {
                "Genre" -> manga.genre = row[1].text().trim().removeSuffix(",")
                "Author" -> manga.author = row[1].text().trim()
                "Status" -> manga.status = parseStatus(row[1].text())
            }
        }

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapter list
    override fun chapterListSelector() = ".chapter-link"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.select(".left").text()
        date_upload = parseDate(element.select(".right").text())
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
