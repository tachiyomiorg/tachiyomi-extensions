package eu.kanade.tachiyomi.extension.id.mangakane

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaKane : ParsedHttpSource() {

    override val name = "MangaKane"
    override val baseUrl = "https://mangakane.com"
    override val lang = "id"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series/page/$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/page/$page/".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("s", query)
        filters.forEach { filter ->
            when (filter) {
                is ProjectFilter -> {
                    if (filter.toUriPart() == "project-filter-on") {
                        url = "$baseUrl/project-list/page/$page".toHttpUrlOrNull()!!.newBuilder()
                    }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun popularMangaSelector() = ".container .flexbox2 .flexbox2-item"
    override fun latestUpdatesSelector() = "h2:not(:has(a)) + .flexbox3 .flexbox3-item"
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("a").attr("title")
        manga.thumbnail_url = element.select("a img").attr("abs:src")

        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = ".pagination .next"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select(".series-infolist li:contains(Author) span").text()
        artist = document.select(".series-infolist li:contains(Artist) span").text()
        status = parseStatus(document.select(".series-infoz .status").firstOrNull()?.ownText())
        description = document.select(".series-synops p").text()
        genre = document.select(".series-genres a").joinToString { it.text() }
    }

    protected fun parseStatus(element: String?): Int = when {
        element == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { it.contains(element, ignoreCase = true) } -> SManga.ONGOING
        listOf("completed").any { it.contains(element, ignoreCase = true) } -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".series-chapterlist li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select(".flexch-infoz a").attr("href"))
        name = element.select(".flexch-infoz span:not(.date)").first().ownText()
        date_upload = parseChapterDate(element.select(".flexch-infoz .date").text()) ?: 0
    }

    private fun parseChapterDate(date: String): Long {
        var parsedDate = 0L
        try {
            parsedDate = SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(date)?.time ?: 0L
        } catch (_: Exception) { /*nothing to do, parsedDate is initialized with 0L*/ }
        return parsedDate
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select(".reader-area img").forEach { element ->
            val url = element.attr("abs:src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: cant be used with search or other filter!"),
        Filter.Header("$name Project List page"),
        ProjectFilter(),
    )

    private class ProjectFilter : UriPartFilter(
        "Filter Project",
        arrayOf(
            Pair("Show all manga", ""),
            Pair("Show only project manga", "project-filter-on")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
