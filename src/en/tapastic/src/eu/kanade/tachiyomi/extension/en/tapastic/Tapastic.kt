package eu.kanade.tachiyomi.extension.en.tapastic

import android.net.Uri
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Tapastic : ParsedHttpSource() {
    override val lang = "en"
    override val supportsLatest = true
    override val name = "Tapastic"
    override val baseUrl = "https://tapas.io"

    private val browseMangaSelector = ".content-item"
    private val nextPageSelector = "a.paging-btn.next"

    private val jsonParser by lazy { JsonParser() }

    override fun popularMangaSelector() = browseMangaSelector

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        val thumb = element.getElementsByClass("thumb-wrap")

        url = thumb.attr("href")

        title = element.getElementsByClass("title").text().trim()

        thumbnail_url = thumb.select("img").attr("src")
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun popularMangaNextPageSelector() = nextPageSelector

    override fun searchMangaSelector() = "$browseMangaSelector, .search-item-wrap"

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaNextPageSelector() = nextPageSelector

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comics?pageNumber=$page&browse=POPULAR")

    override fun latestUpdatesSelector() = browseMangaSelector

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        //If there is any search text, use text search, otherwise use filter search
        val uri = if(query.isNotBlank()) {
            Uri.parse("$baseUrl/search")
                    .buildUpon()
                    .appendQueryParameter("t", "COMICS")
                    .appendQueryParameter("q", query)
        } else {
            val uri = Uri.parse("$baseUrl/comics").buildUpon()
            //Append uri filters
            filters.forEach {
                if(it is UriFilter)
                    it.addToUri(uri)
            }
            uri
        }
        //Append page number
        uri.appendQueryParameter("pageNumber", page.toString())
        return GET(uri.toString())
    }

    override fun latestUpdatesNextPageSelector() = nextPageSelector

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.getElementsByClass("series-header-title").text().trim()

        author = document.getElementsByClass("name").text().trim()
        artist = author

        description = document.getElementById("series-desc-body").text().trim()

        genre = document.getElementsByClass("genre").text()

        status = SManga.UNKNOWN
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comics?pageNumber=$page&browse=FRESH")

    override fun chapterListParse(response: Response)
            //Chapters are stored in JavaScript as JSON!
            = response.asJsoup().getElementsByTag("script").filter {
        it.data().trim().startsWith("var _data")
    }.flatMap {
        val text = it.data()
        val episodeVar = text.indexOf("episodeList")
        if(episodeVar == -1)
            return@flatMap emptyList<SChapter>()

        val episodeLeftBracket = text.indexOf('[', startIndex = episodeVar)
        if(episodeLeftBracket == -1)
            return@flatMap emptyList<SChapter>()

        val endOfLine = text.indexOf('\n', startIndex = episodeLeftBracket)
        if(endOfLine == -1)
            return@flatMap emptyList<SChapter>()

        val episodeRightBracket = text.lastIndexOf(']', startIndex = endOfLine)
        if(episodeRightBracket == -1)
            return@flatMap emptyList<SChapter>()

        val episodeListText = text.substring(episodeLeftBracket .. episodeRightBracket)

        jsonParser.parse(episodeListText).array.map {
            val json = it.asJsonObject
            //Ensure that the chapter is published (tapastic allows scheduling chapters)
            if(json["orgScene"].int != 0)
                SChapter.create().apply {
                    url = "/episode/${json["id"].string}"

                    name = json["title"].string

                    date_upload = json["publishDate"].long

                    chapter_number = json["scene"].float
                }
            else null
        }.filterNotNull().sortedByDescending(SChapter::chapter_number)
    }

    override fun chapterListSelector(): String {
        throw UnsupportedOperationException("This method should not be called!")
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException("This method should not be called!")
    }

    override fun pageListParse(document: Document)
            = document.getElementsByClass("art-image").mapIndexed { index, element ->
        Page(index, "", element.attr("src"))
    }

    override fun imageUrlParse(document: Document): String {
        //Unused, we can get image urls directly from the chapter page
        throw UnsupportedOperationException("This method should not be called!")
    }

    override fun getFilterList() = FilterList(
            //Tapastic does not support genre filtering and text search at the same time
            Filter.Header("NOTE: Ignored if using text search!"),
            Filter.Separator(),
            FilterFilter(),
            GenreFilter(),
            Filter.Separator(),
            Filter.Header("Sort is ignored when filter is active!"),
            SortFilter()
    )

    private class FilterFilter: UriSelectFilter("Filter", "browse", arrayOf(
            Pair("ALL", "None"),
            Pair("POPULAR", "Popular"),
            Pair("TRENDING", "Trending"),
            Pair("FRESH", "Fresh"),
            Pair("TAPASTIC", "Staff Picks")
    ), firstIsUnspecified = false, defaultValue = 1)

    private class GenreFilter: UriSelectFilter("Genre", "genreIds", arrayOf(
            Pair("", "Any"),
            Pair("7", "Action"),
            Pair("2", "Comedy"),
            Pair("8", "Drama"),
            Pair("3", "Fantasy"),
            Pair("9", "Gaming"),
            Pair("6", "Horror"),
            Pair("10", "Mystery"),
            Pair("5", "Romance"),
            Pair("4", "Science Fiction"),
            Pair("1", "Slice of Life")
    ))

    private class SortFilter: UriSelectFilter("Sort", "sortType", arrayOf(
            Pair("SUBSCRIBE", "Subscribers"),
            Pair("LIKE", "Likes"),
            Pair("VIEW", "Views"),
            Pair("COMMENT", "Comments"),
            Pair("CREATED", "Date"),
            Pair("TITLE", "Name")
    ))

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    private open class UriSelectFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                       val firstIsUnspecified: Boolean = true,
                                       val defaultValue: Int = 0):
            Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if(state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }
}
