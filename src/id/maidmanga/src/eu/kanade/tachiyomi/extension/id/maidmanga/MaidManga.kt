package eu.kanade.tachiyomi.extension.id.maidmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MaidManga : ParsedHttpSource() {

    override val name = "Maid - Manga"

    override val baseUrl = "https://www.maid.my.id"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private fun pagePathSegment(page: Int): String = if (page > 1) "page/$page/" else ""

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/advanced-search/${pagePathSegment(page)}?order=popular")
    }

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/advanced-search/${pagePathSegment(page)}?order=update")
    }

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/advanced-search/${pagePathSegment(page)}")!!.newBuilder()
        url.addQueryParameter("title", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state
                            .filter { it.state }
                            .forEach { url.addQueryParameter("genre[]", it.id) }
                }
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "div.flexbox2-item"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("div.flexbox2-content a").attr("href"))
            title = element.select("div.flexbox2-title > span").first().text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun searchMangaNextPageSelector() = "div.pagination span.current + a"

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            genre = document.select("div.series-genres a").joinToString { it.text() }
            description = document.select("div.series-synops").text()
            thumbnail_url = document.select("div.series-thumb img").attr("abs:src")
            status = parseStatus(document.select("div.block span.status").text())
            author = document.select("ul.series-infolist li b:contains(Author) + span").text()
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MMM d, yyyy", Locale("id")).parse(date).time
    }

    // careful not to include download links
    override fun chapterListSelector() = "ul.series-chapterlist div.flexch-infoz > a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.select("span").first().ownText()
            date_upload = parseDate(element.select("span.date").text())
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-area img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
            Filter.Header("You can combine filter."),
            Filter.Separator(),
            AuthorFilter(),
            YearFilter(),
            StatusFilter(),
            TypeFilter(),
            OrderByFilter(),
            GenreList(getGenreList())
    )

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class StatusFilter : Filter.TriState("Completed")

    private class TypeFilter : UriPartFilter("Type", arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
            Pair("One-Shot", "One-Shot"),
            Pair("Doujin", "Doujin")
    ))
    private class OrderByFilter : UriPartFilter("Order By", arrayOf(
            Pair("<select>", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
            Pair("Rating", "rating")
    ))

    private fun getGenreList() = listOf(
            Tag("4-koma", "4-Koma"),
            Tag("4-koma-comedy", "4-Koma Comedy"),
            Tag("action", "Action"),
            Tag("adult", "Adult"),
            Tag("adventure", "Adventure"),
            Tag("comedy", "Comedy"),
            Tag("demons", "Demons"),
            Tag("drama", "Drama"),
            Tag("ecchi", "Ecchi"),
            Tag("fantasy", "Fantasy"),
            Tag("game", "Game"),
            Tag("gender-bender", "Gender bender"),
            Tag("gore", "Gore"),
            Tag("harem", "Harem"),
            Tag("historical", "Historical"),
            Tag("horror", "Horror"),
            Tag("isekai", "Isekai"),
            Tag("josei", "Josei"),
            Tag("loli", "Loli"),
            Tag("magic", "Magic"),
            Tag("manga", "Manga"),
            Tag("manhua", "Manhua"),
            Tag("manhwa", "Manhwa"),
            Tag("martial-arts", "Martial Arts"),
            Tag("mature", "Mature"),
            Tag("mecha", "Mecha"),
            Tag("military", "Military"),
            Tag("monster-girls", "Monster Girls"),
            Tag("music", "Music"),
            Tag("mystery", "Mystery"),
            Tag("one-shot", "One Shot"),
            Tag("parody", "Parody"),
            Tag("police", "Police"),
            Tag("psychological", "Psychological"),
            Tag("romance", "Romance"),
            Tag("school", "School"),
            Tag("school-life", "School Life"),
            Tag("sci-fi", "Sci-Fi"),
            Tag("socks", "Socks"),
            Tag("seinen", "Seinen"),
            Tag("shoujo", "Shoujo"),
            Tag("shoujo-ai", "Shoujo Ai"),
            Tag("shounen", "Shounen"),
            Tag("shounen-ai", "Shounen Ai"),
            Tag("slice-of-life", "Slice of Life"),
            Tag("smut", "Smut"),
            Tag("sports", "Sports"),
            Tag("super-power", "Super Power"),
            Tag("supernatural", "Supernatural"),
            Tag("survival", "Survival"),
            Tag("thriller", "Thriller"),
            Tag("tragedy", "Tragedy"),
            Tag("vampire", "Vampire"),
            Tag("webtoons", "Webtoons"),
            Tag("yuri", "Yuri")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(val id: String, name: String) : Filter.CheckBox(name)

    private class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
}
