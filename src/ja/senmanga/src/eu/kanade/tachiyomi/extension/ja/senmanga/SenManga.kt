package eu.kanade.tachiyomi.extension.ja.senmanga

import android.annotation.SuppressLint
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
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Sen Manga source
 */

class SenManga : ParsedHttpSource() {
    override val lang: String = "ja"

    override val supportsLatest = true
    override val name = "Sen Manga"
    override val baseUrl = "https://raw.senmanga.com"

    @SuppressLint("DefaultLocale")
    override val client = super.client.newBuilder().addInterceptor {
        // Intercept any image requests and add a referer to them
        // Enables bandwidth stealing feature
        val request = if (it.request().url().pathSegments().firstOrNull()?.trim()?.toLowerCase() == "viewer") {
            it.request().newBuilder()
                .addHeader(
                    "Referer",
                    it.request().url().newBuilder()
                        .removePathSegment(0)
                        .toString()
                )
                .build()
        } else it.request()
        it.proceed(request)
    }.build()!!

    override fun popularMangaSelector() = "div.item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.select("div.series-title").text()
        }
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination a[rel=next]"

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/directory/popular?page=$page")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val genreInclude = filter.state.filter { it.isIncluded() }.joinToString("%2C") { it.id }
                    val genreExclude = filter.state.filter { it.isExcluded() }.joinToString("%2C") { it.id }
                    url.addQueryParameter("genre", genreInclude)
                    url.addQueryParameter("nogenre", genreExclude)
                }
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
            }
        }
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1.series").text()

        thumbnail_url = document.select("div.cover img").first().attr("src")

        description = document.select("div.summary").first().text()

        val seriesElement = document.select("div.series-desc .info ")

        genre = seriesElement.select(".item:eq(0)").text().substringAfter(": ")
        status = seriesElement.select(".item:eq(1)").first()?.text().orEmpty().let { parseStatus(it.substringAfter("Status:")) }
        author = seriesElement.select(".item:eq(3)").text().substringAfter(": ")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Complete") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/directory/last_update?page=$page", headers)
    }

    override fun chapterListSelector() = "ul.chapter-list li"

    @SuppressLint("DefaultLocale")
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val linkElement = element.getElementsByTag("a")

        setUrlWithoutDomain(linkElement.attr("href"))

        name = linkElement.first().text()

        chapter_number = element.child(0).text().trim().toFloatOrNull() ?: -1f

        date_upload = parseDate(element.select("time").attr("datetime"))
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(date)?.time ?: 0
    }

    override fun pageListParse(document: Document): List<Page> {
        return listOf(1..document.select("select[name=page] option:last-of-type").first().text().toInt()).flatten().map { i ->
            Page(i - 1, "", "${document.location().replace(baseUrl, "$baseUrl/viewer")}/$i")
        }
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList()),
        SortFilter()
    )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    private class SortFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("total_views", "Total Views"),
            Pair("title", "Title"),
            Pair("rank", "Rank"),
            Pair("last_update", "Last Update")
        )
    )

    private fun getGenreList(): List<Genre> = listOf(
        Genre("Action", "Action"),
        Genre("Adult", "Adult"),
        Genre("Adventure", "Adventure"),
        Genre("Comedy", "Comedy"),
        Genre("Cooking", "Cooking"),
        Genre("Drama", "Drama"),
        Genre("Ecchi", "Ecchi"),
        Genre("Fantasy", "Fantasy"),
        Genre("Gender Bender", "Gender+Bender"),
        Genre("Harem", "Harem"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Josei", "Josei"),
        Genre("Light Novel", "Light+Novel"),
        Genre("Martial Arts", "Martial+Arts"),
        Genre("Mature", "Mature"),
        Genre("Music", "Music"),
        Genre("Mystery", "Mystery"),
        Genre("Psychological", "Psychological"),
        Genre("Romance", "Romance"),
        Genre("School Life", "School+Life"),
        Genre("Sci-Fi", "Sci+Fi"),
        Genre("Seinen", "Seinen"),
        Genre("Shoujo", "Shoujo"),
        Genre("Shoujo Ai", "Shoujo+Ai"),
        Genre("Shounen", "Shounen"),
        Genre("Shounen Ai", "Shounen+Ai"),
        Genre("Slice of Life", "Slice+of+Life"),
        Genre("Smut", "Smut"),
        Genre("Sports", "Sports"),
        Genre("Supernatural", "Supernatural"),
        Genre("Tragedy", "Tragedy"),
        Genre("Webtoons", "Webtoons"),
        Genre("Yaoi", "Yaoi"),
        Genre("Yuri", "Yuri")
    )
}
