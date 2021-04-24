package eu.kanade.tachiyomi.extension.id.mangayu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MangaYu : ParsedHttpSource() {

    override val name = "MangaYu"
    override val baseUrl = "https://mangayu.com"
    override val lang = "id"
    override val supportsLatest = true
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    protected fun Element.imgAttr(): String = if (this.hasAttr("data-src")) this.attr("abs:data-src") else this.attr("abs:src")

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga?order_by=views&page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga?order_by=latest&page=$page", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/manga?")!!.newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
        val newUrl = null
        filters.forEach { filter ->
            when (filter) {
                is SortByFilter -> {
                    url.addQueryParameter("order_by", filter.toUriPart())
                }
                // TODO GENRE with $baseUrl/genre
            }
        }
        return GET(url.toString(), headers)
    }

    override fun popularMangaSelector() = ".row .col-md-8 .row .col-md-6"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select(".detail a.link").attr("href"))
        manga.title = element.select(".detail a.link").text()
        manga.thumbnail_url = element.select(".cover a img").attr("src")

        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.page-link[rel=next]"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select("tr:contains(Author) td:nth-child(2)").text()
        artist = document.select("tr:contains(Artist) td:nth-child(2)").text()
        status = parseStatus(document.select("tr:contains(Status) td").firstOrNull()?.ownText())
        description = document.select("div.card-body h5 ~ p").text()
        genre = document.select("tr:contains(Genre) a").joinToString { it.text() }
    }

    protected fun parseStatus(element: String?): Int = when {
        element == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { it.contains(element, ignoreCase = true) } -> SManga.ONGOING
        listOf("completed").any { it.contains(element, ignoreCase = true) } -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.list-group-item a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.select("div.d-flex").text()
        date_upload = parseChapterDate(element.select("span.text-white-50").text()) ?: 0
    }

    fun parseChapterDate(date: String): Long {
        return if (date.contains("yang lalu")) {
            val value = date.split(' ')[0].toInt()
            when {
                "menit" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
                "jam" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
                "hari" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
                "minggu" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                }.timeInMillis
                "bulan" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                }.timeInMillis
                "tahun" in date -> Calendar.getInstance().apply {
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

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select(".chapter-image img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    private class SortByFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Default", ""),
            Pair("A-Z", "name"),
            Pair("Latest Update", "latest"),
            Pair("Latest Added", "new"),
            Pair("Popular", "views")
        )
    )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    override fun getFilterList() = FilterList(
        SortByFilter()
    )

// need to add thing to search filter for genre
    private fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Mystery", "mystery"),
        Genre("One-shot", "one-shot"),
        Genre("Psychological", "psychological"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Tragedy", "tragedy"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
        Genre("Loli", "loli"),
        Genre("Game", "game"),
        Genre("Medical", "medical"),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
