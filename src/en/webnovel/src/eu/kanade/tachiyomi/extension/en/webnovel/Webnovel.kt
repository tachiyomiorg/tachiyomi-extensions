package eu.kanade.tachiyomi.extension.en.webnovel

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Webnovel : ParsedHttpSource() {

    override val name = "Webnovel.com"

    override val baseUrl = "https://www.webnovel.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM dd,yyyy", Locale.US)

    // popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/0_comic_page$page", headers)

    override fun popularMangaSelector() = "a.g_thumb, div.j_bookList .g_book_item a:has(img)"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.attr("abs:href").substringAfter(baseUrl)
        manga.title = element.attr("title")
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "[rel=next]"

    // latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/0_comic_page$page?orderBy=5", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filters = if (filters.isEmpty()) getFilterList() else filters
        val genre = filters.findInstance<GenreList>()?.toUriPart()
        val order = filters.findInstance<OrderByFilter>()?.toUriPart()
        val status = filters.findInstance<StatusFilter>()?.toUriPart()

        return when {
            query!!.isNotEmpty() -> GET("$baseUrl/search?keywords=$query&type=2&pageIndex=$page", headers)
            else -> GET("$baseUrl/category/$genre" + "_comic_page1?&orderBy=$order&bookStatus=$status")
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.select("i.g_thumb img:first-child").attr("abs:src")
        title = document.select("h2").text()
        description = document.select(".j_synopsis p").text()
    }

    // chapters
    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url + "/catalog", headers)

    override fun chapterListSelector() = ".volume-item li a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.attr("title")
        date_upload = parseChapterDate(element.select(".oh small").text())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    fun parseChapterDate(date: String): Long {
        return if (date.contains("ago")) {
            val value = date.split(' ')[0].toInt()
            when {
                "min" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
                "hour" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
                "day" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
                "week" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                }.timeInMillis
                "month" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                }.timeInMillis
                "year" in date -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                }.timeInMillis
                else -> {
                    0L
                }
            }
        } else {
            try {
                dateFormat.parse(date)?.time ?: 0
            } catch (_: Exception) {
                0L
            }
        }
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#comicPageContainer img").mapIndexed { i, element ->
            Page(i, "", element.attr("data-original"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    // filter
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        StatusFilter(),
        OrderByFilter(),
        GenreList()
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("0", "All"),
            Pair("1", "Ongoing"),
            Pair("2", "Completed")
        )
    )

    private class OrderByFilter : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("1", "Default"),
            Pair("1", "Popular"),
            Pair("2", "Recommendation"),
            Pair("3", "Collection"),
            Pair("4", "Rates"),
            Pair("5", "Updated")
        )
    )
    private class GenreList : UriPartFilter(
        "Select Genre",
        arrayOf(
            Pair("0", "All"),
            Pair("60002", "Action"),
            Pair("60014", "Adventure"),
            Pair("60011", "Comedy"),
            Pair("60009", "Cooking"),
            Pair("60027", "Diabolical"),
            Pair("60024", "Drama"),
            Pair("60006", "Eastern"),
            Pair("60022", "Fantasy"),
            Pair("60017", "Harem"),
            Pair("60018", "History"),
            Pair("60015", "Horror"),
            Pair("60013", "Inspiring"),
            Pair("60029", "LGBT+"),
            Pair("60016", "Magic"),
            Pair("60008", "Mystery"),
            Pair("60003", "Romance"),
            Pair("60007", "School"),
            Pair("60004", "Sci-fi"),
            Pair("60019", "Slice of Life"),
            Pair("60023", "Sports"),
            Pair("60012", "Transmigration"),
            Pair("60005", "Urban"),
            Pair("60010", "Wuxia")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
