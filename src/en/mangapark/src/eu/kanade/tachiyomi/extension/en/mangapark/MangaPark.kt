package eu.kanade.tachiyomi.extension.en.mangapark

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

/**
 * MangaPark source
 */

class MangaPark : ParsedHttpSource() {
    override val lang: String = "en"

    override val supportsLatest = true
    override val name = "MangaPark"
    override val baseUrl = "http://mangapark.me" //TODO Can we do HTTPS?

    private val directorySelector = ".item"
    private val directoryUrl = "/genre"
    private val directoryNextPageSelector = ".paging.full > li:last-child > a"

    private val dateFormat = SimpleDateFormat("MMM d, yyyy, HH:mm a", Locale.ENGLISH)

    override fun popularMangaSelector() = directorySelector

    fun mangaFromElement(element: Element) = SManga.create().apply {
        val coverElement = element.getElementsByClass("cover").first()
        url = coverElement.attr("href")

        title = coverElement.attr("title")

        thumbnail_url = coverElement.getElementsByTag("img").attr("src")
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun popularMangaNextPageSelector() = directoryNextPageSelector

    override fun searchMangaSelector() = ".item"

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaNextPageSelector() = ".paging > li:last-child > a"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl$directoryUrl?views")

    override fun latestUpdatesSelector() = directorySelector

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/search").buildUpon()
        uri.appendQueryParameter("q", query)
        filters.forEach {
            if(it is UriFilter)
                it.addToUri(uri)
        }
        return GET(uri.toString())
    }

    override fun latestUpdatesNextPageSelector() = directoryNextPageSelector

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val coverElement = document.select(".cover > img").first()

        title = coverElement.attr("title")

        thumbnail_url = coverElement.attr("src")

        document.select(".attr > tbody > tr").forEach {
            val type = it.getElementsByTag("th").first().text().trim().toLowerCase()
            when(type) {
                "author(s)" -> {
                    author = it.getElementsByTag("a").joinToString(transform = Element::text)
                }
                "artist(s)" -> {
                    artist = it.getElementsByTag("a").joinToString(transform = Element::text)
                }
                "genre(s)" -> {
                    genre = it.getElementsByTag("a").joinToString(transform = Element::text)
                }
                "status" -> {
                    status = when(it.getElementsByTag("td").text().trim().toLowerCase()) {
                        "ongoing" -> SManga.ONGOING
                        "completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }
        }

        description = document.getElementsByClass("summary").text().trim()
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl$directoryUrl?latest")

    //TODO MangaPark has "versioning" or something
    //TODO Currently we just use the version that is expanded by default
    //TODO Maybe make it possible for users to view the other versions as well?
    override fun chapterListSelector() = ".stream:not(.collapsed) .volume .chapter li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        url = element.select("em > a").last().attr("href")

        name = element.getElementsByClass("ch").text()

        date_upload = dateFormat.parse(element.getElementsByTag("i").text().trim()).time
    }

    override fun pageListParse(document: Document)
        = document.getElementsByClass("img").map {
            Page(it.attr("i").toInt() - 1, "", it.attr("src"))
        }

    override fun imageUrlParse(document: Document): String {
        //Unused, we can get image urls directly from the chapter page
        throw UnsupportedOperationException("This method should not be called!")
    }

    override fun getFilterList() = FilterList(
            AuthorArtistText(),
            SearchTypeFilter("Title query", "name-match"),
            SearchTypeFilter("Author/Artist query", "autart-match"),
            SortFilter(),
            GenreGroup(),
            GenreInclusionFilter(),
            ChapterCountFilter(),
            StatusFilter(),
            RatingFilter(),
            TypeFilter(),
            YearFilter()
    )

    class SearchTypeFilter(name: String, val uriParam: String):
            Filter.Select<String>(name, stateMap), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter(uriParam, stateMap[state])
        }

        companion object {
            private val stateMap = arrayOf("contain", "begin", "end")
        }
    }

    class AuthorArtistText : Filter.Text("Author/Artist"), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("autart", state)
        }
    }

    class GenreFilter(val uriParam: String, displayName: String): Filter.TriState(displayName)

    class GenreGroup : Filter.Group<GenreFilter>("Genres", listOf(
            GenreFilter("4-koma", "4 koma"),
            GenreFilter("action", "Action"),
            GenreFilter("adult", "Adult"),
            GenreFilter("adventure", "Adventure"),
            GenreFilter("award-winning", "Award winning"),
            GenreFilter("comedy", "Comedy"),
            GenreFilter("cooking", "Cooking"),
            GenreFilter("demons", "Demons"),
            GenreFilter("doujinshi", "Doujinshi"),
            GenreFilter("drama", "Drama"),
            GenreFilter("ecchi", "Ecchi"),
            GenreFilter("fantasy", "Fantasy"),
            GenreFilter("gender-bender", "Gender bender"),
            GenreFilter("harem", "Harem"),
            GenreFilter("historical", "Historical"),
            GenreFilter("horror", "Horror"),
            GenreFilter("josei", "Josei"),
            GenreFilter("magic", "Magic"),
            GenreFilter("martial-arts", "Martial arts"),
            GenreFilter("mature", "Mature"),
            GenreFilter("mecha", "Mecha"),
            GenreFilter("medical", "Medical"),
            GenreFilter("music", "Music"),
            GenreFilter("mystery", "Mystery"),
            GenreFilter("one-shot", "One shot"),
            GenreFilter("psychological", "Psychological"),
            GenreFilter("romance", "Romance"),
            GenreFilter("school-life", "School life"),
            GenreFilter("sci-fi", "Sci fi"),
            GenreFilter("seinen", "Seinen"),
            GenreFilter("shoujo", "Shoujo"),
            GenreFilter("shoujo-ai", "Shoujo ai"),
            GenreFilter("shounen", "Shounen"),
            GenreFilter("shounen-ai", "Shounen ai"),
            GenreFilter("slice-of-life", "Slice of life"),
            GenreFilter("smut", "Smut"),
            GenreFilter("sports", "Sports"),
            GenreFilter("supernatural", "Supernatural"),
            GenreFilter("tragedy", "Tragedy"),
            GenreFilter("webtoon", "Webtoon"),
            GenreFilter("yaoi", "Yaoi"),
            GenreFilter("yuri", "Yuri")
    )), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("genres", state.filter { it.isIncluded() }.map { it.uriParam }.joinToString(","))
            uri.appendQueryParameter("genres-exclude", state.filter { it.isExcluded() }.map { it.uriParam }.joinToString(","))
        }
    }

    class GenreInclusionFilter : UriSelectFilter("Genre inclusion", "genres-mode", arrayOf(
            Pair("and", "And mode"),
            Pair("or", "Or mode")
    ))

    class ChapterCountFilter : UriSelectFilter("Chapter count", "chapters", arrayOf(
            Pair("any", "Any"),
            Pair("1", "1 +"),
            Pair("5", "5 +"),
            Pair("10", "10 +"),
            Pair("20", "20 +"),
            Pair("30", "30 +"),
            Pair("40", "40 +"),
            Pair("50", "50 +"),
            Pair("100", "100 +"),
            Pair("150", "150 +"),
            Pair("200", "200 +")
    ))

    class StatusFilter : UriSelectFilter("Status", "status", arrayOf(
            Pair("any", "Any"),
            Pair("completed", "Completed"),
            Pair("ongoing", "Ongoing")
    ))

    class RatingFilter : UriSelectFilter("Rating", "rating", arrayOf(
            Pair("any", "Any"),
            Pair("5", "5 stars"),
            Pair("4", "4 stars"),
            Pair("3", "3 stars"),
            Pair("2", "2 stars"),
            Pair("1", "1 star"),
            Pair("0", "0 stars")
    ))

    class TypeFilter : UriSelectFilter("Type", "types", arrayOf(
            Pair("any", "Any"),
            Pair("manga", "Japanese Manga"),
            Pair("manhwa", "Korean Manhwa"),
            Pair("manhua", "Chinese Manhua"),
            Pair("unknown", "Unknown")
    ))

    class YearFilter : UriSelectFilter("Release year", "years",
            arrayOf(Pair("any", "Any"),
                    //Get all years between today and 1946
                    *(Calendar.getInstance().get(Calendar.YEAR) downTo 1946).map {
                        Pair(it.toString(), it.toString())
                    }.toTypedArray()
            )
    )

    class SortFilter : UriSelectFilter("Sort", "orderby", arrayOf(
            Pair("a-z", "A-Z"),
            Pair("views", "Views"),
            Pair("rating", "Rating"),
            Pair("latest", "Latest"),
            Pair("add", "New manga")
    ), firstIsUnspecified = false, defaultValue = 1)

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    open class UriSelectFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                               val firstIsUnspecified: Boolean = true,
                               defaultValue: Int = 0):
            Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if(state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }
}
