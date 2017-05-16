package eu.kanade.tachiyomi.extension.en.mangago

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
 * Mangago source
 */

class Mangago : ParsedHttpSource() {
    override val lang = "en"
    override val supportsLatest = true
    override val name = "Mangago"
    override val baseUrl = "https://www.mangago.me"

    override val client = network.cloudflareClient!!

    //Hybrid selector that selects manga from either the genre listing or the search results
    private val genreListingSelector = ".updatesli"
    private val genreListingNextPageSelector = ".current+li > a"

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

    override fun popularMangaSelector() = genreListingSelector

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        val linkElement = element.select(".thm-effect")

        setUrlWithoutDomain(linkElement.attr("href"))

        title = linkElement.attr("title")

        thumbnail_url = linkElement.first().child(0).attr("src")
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun popularMangaNextPageSelector() = genreListingNextPageSelector

    //Hybrid selector that selects manga from either the genre listing or the search results
    override fun searchMangaSelector() = "$genreListingSelector, .pic_list .box"

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaNextPageSelector() = genreListingNextPageSelector

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genre/all/$page/?f=1&o=1&sortby=view&e=")

    override fun latestUpdatesSelector() = genreListingSelector

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        //If text search is active use text search, otherwise use genre search
        val url = if (query.isNotBlank()) {
            Uri.parse("$baseUrl/r/l_search/")
                    .buildUpon()
                    .appendQueryParameter("name", query)
                    .appendQueryParameter("page", page.toString())
                    .toString()
        } else {
            val uri = Uri.parse("$baseUrl/genre/").buildUpon()
            val genres = filters.flatMap {
                (it as? GenreGroup)?.stateList ?: emptyList()
            }
            //Append included genres
            val activeGenres = genres.filter { it.isIncluded() }
            uri.appendPath(if (activeGenres.isEmpty())
                "all"
            else
                activeGenres.joinToString(",", transform = { it.name }))
            //Append page number
            uri.appendPath(page.toString())
            //Append excluded genres
            uri.appendQueryParameter("e",
                    genres.filter { it.isExcluded() }
                            .joinToString(",", transform = GenreFilter::name))
            //Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri.toString()
        }
        return GET(url)
    }

    override fun latestUpdatesNextPageSelector() = genreListingNextPageSelector

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val coverElement = document.select(".left.cover > img")

        title = coverElement.attr("alt")

        thumbnail_url = coverElement.attr("src")

        document.select(".manga_right td").forEach {
            when (it.getElementsByTag("label").text().trim().toLowerCase()) {
                "status:" -> {
                    status = when (it.getElementsByTag("span").first().text().trim().toLowerCase()) {
                        "ongoing" -> SManga.ONGOING
                        "completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
                "author:" -> {
                    author = it.getElementsByTag("a").first().text()
                }
                "genre(s):" -> {
                    genre = it.getElementsByTag("a").joinToString(transform = { it.text() })
                }
            }
        }

        description = document.getElementsByClass("manga_summary").first().ownText().trim()
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genre/all/$page/?f=1&o=1&sortby=update_date&e=")

    override fun chapterListSelector() = "#chapter_table > tbody > tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val link = element.getElementsByTag("a")

        setUrlWithoutDomain(link.attr("href"))

        name = link.text().trim()

        date_upload = dateFormat.parse(element.getElementsByClass("no").text().trim()).time
    }

    override fun pageListParse(document: Document)
            = document.getElementById("pagenavigation").getElementsByTag("a").mapIndexed { index, element ->
        Page(index, element.attr("href"))
    }

    override fun imageUrlParse(document: Document) = document.getElementById("page1").attr("src")!!

    override fun getFilterList() = FilterList(
            //Mangago does not support genre filtering and text search at the same time
            Filter.Header("NOTE: Ignored if using text search!"),
            Filter.Separator(),
            Filter.Header("Status"),
            StatusFilter("Completed", "f"),
            StatusFilter("Ongoing", "o"),
            GenreGroup(),
            SortFilter()
    )

    private class GenreGroup : UriFilterGroup<GenreFilter>("Genres", listOf(
            GenreFilter("Yaoi"),
            GenreFilter("Doujinshi"),
            GenreFilter("Shounen Ai"),
            GenreFilter("Shoujo"),
            GenreFilter("Yuri"),
            GenreFilter("Romance"),
            GenreFilter("Fantasy"),
            GenreFilter("Smut"),
            GenreFilter("Adult"),
            GenreFilter("School Life"),
            GenreFilter("Mystery"),
            GenreFilter("Comedy"),
            GenreFilter("Ecchi"),
            GenreFilter("Shounen"),
            GenreFilter("Martial Arts"),
            GenreFilter("Shoujo Ai"),
            GenreFilter("Supernatural"),
            GenreFilter("Drama"),
            GenreFilter("Action"),
            GenreFilter("Adventure"),
            GenreFilter("Harem"),
            GenreFilter("Historical"),
            GenreFilter("Horror"),
            GenreFilter("Josei"),
            GenreFilter("Mature"),
            GenreFilter("Mecha"),
            GenreFilter("Psychological"),
            GenreFilter("Sci-fi"),
            GenreFilter("Seinen"),
            GenreFilter("Slice Of Life"),
            GenreFilter("Sports"),
            GenreFilter("Gender Bender"),
            GenreFilter("Tragedy"),
            GenreFilter("Bara"),
            GenreFilter("Shotacon")
    ))

    private class GenreFilter(name: String) : Filter.TriState(name)

    private class StatusFilter(name: String, val uriParam: String) : Filter.CheckBox(name, true), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter(uriParam, if (state) "1" else "0")
        }
    }

    private class SortFilter : UriSelectFilter("Sort", "sortby", arrayOf(
            Pair("random", "Random"),
            Pair("view", "Views"),
            Pair("comment_count", "Comment Count"),
            Pair("create_date", "Creation Date"),
            Pair("update_date", "Update Date")
    ))

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    private open class UriSelectFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                       val firstIsUnspecified: Boolean = true,
                                       defaultValue: Int = 0) :
            Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    /**
     * Uri filter group
     */
    private open class UriFilterGroup<V>(name: String, val stateList: List<V>) : Filter.Group<V>(name, stateList), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            stateList.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }
}
