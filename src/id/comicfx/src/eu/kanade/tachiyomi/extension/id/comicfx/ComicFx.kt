package eu.kanade.tachiyomi.extension.id.comicfx

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class ComicFx : ParsedHttpSource() {

    override val name = "Comic Fx"
    override val baseUrl = "https://comicfx.net"
    override val lang = "id"
    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/filterList?page=$page&sortBy=name&asc=true", headers)
    }

    override fun popularMangaSelector() = "div.media"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".media-left a img").attr("src")
        manga.title = element.select(".media-body .media-heading a strong").text()
        val item = element.select(".media-left a")
        manga.setUrlWithoutDomain(item.attr("href"))

        return manga
    }

    override fun popularMangaNextPageSelector() = ".pagination li a[rel=next]"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-release?page=$page", headers)
    }

    override fun latestUpdatesSelector() = "div.daftar-komik .komika"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".komik-img a .batas img").attr("src")
        manga.title = element.select(".komik-des a h3").text()
        val item = element.select("div.komik-img a")
        manga.setUrlWithoutDomain(item.attr("href"))

        return manga
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filters = if (filters.isEmpty()) getFilterList() else filters
        val genre = filters.findInstance<GenreList>()?.toUriPart()
        val order = filters.findInstance<OrderByFilter>()?.toUriPart()

        // todo search

        return GET("$baseUrl/filterList?page=$page&cstatus=&ctype=&cat=$genre&alpha=&$order&author=&artist=&tag=") // filter
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select("#author a").text()
        artist = document.select(".infolengkap span:contains(Artist) a").text()
        status = parseStatus(document.select(".infolengkap span:contains(status) i").text())
        description = document.select("div.sinopsis p").text()
        genre = document.select(".infolengkap span:contains(Genre) a").joinToString { it.text() }
    }

    protected fun parseStatus(element: String?): Int = when {
        element == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { it.contains(element, ignoreCase = true) } -> SManga.ONGOING
        listOf("completed").any { it.contains(element, ignoreCase = true) } -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListSelector() = "div.chaplist li .pull-left a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }

        // Add timestamp to latest chapter, taken from "Updated On". so source which not provide chapter timestamp will have atleast one
        val updateOn = document.select(".infokomik .infolengkap span:contains(update) b").text()
        val date = document.select(".infokomik .infolengkap span:contains(update)").text().substringAfter(updateOn)
        val checkChapter = document.select(chapterListSelector()).firstOrNull()
        if (date != "" && checkChapter != null) chapters[0].date_upload = parseDate(date)

        return chapters
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).parse(date)?.time ?: 0L
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text()
    }

    // Pages
    override fun imageUrlParse(document: Document) = ""

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("#all img").mapIndexed { i, element ->
            val image = element.attr("data-src")
            if (image != "") {
                pages.add(Page(i, "", image))
            }
        }

        return pages
    }

    // filters
    override fun getFilterList() = FilterList(
        OrderByFilter(),
        GenreList()
    )

    private class OrderByFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("sortBy=name&asc=true", "Default"),
            Pair("sortBy=name&asc=true", "A-Z"),
            Pair("sortBy=name&asc=false", "Z-A"),
            Pair("sortBy=views&asc=false", "Popular to Less"),
            Pair("sortBy=views&asc=true", "Less to Popular")
        )
    )

    private class GenreList : UriPartFilter(
        "Select Genre",
        arrayOf(
            Pair("", "<select>"),
            Pair("1", "Action"),
            Pair("2", "Adventure"),
            Pair("3", "Comedy"),
            Pair("4", "Doujinshi"),
            Pair("5", "Drama"),
            Pair("6", "Ecchi"),
            Pair("7", "Fantasy"),
            Pair("8", "Gender Bender"),
            Pair("9", "Harem"),
            Pair("10", "Historical"),
            Pair("11", "Horror"),
            Pair("12", "Josei"),
            Pair("13", "Martial Arts"),
            Pair("14", "Mature"),
            Pair("15", "Mecha"),
            Pair("16", "Mystery"),
            Pair("17", "One Shot"),
            Pair("18", "Psychological"),
            Pair("19", "Romance"),
            Pair("20", "School Life"),
            Pair("21", "Sci-Fi"),
            Pair("22", "Seinen"),
            Pair("23", "Shoujo"),
            Pair("24", "Shoujo Ai"),
            Pair("25", "Shounen"),
            Pair("26", "Shounen Ai"),
            Pair("27", "Slice of Life"),
            Pair("28", "Sports"),
            Pair("29", "Supernatural"),
            Pair("30", "Tragedy"),
            Pair("34", "Smut")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
