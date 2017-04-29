package eu.kanade.tachiyomi.extension.jp.senmanga

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

/**
 * Sen Manga source
 */

class SenManga: ParsedHttpSource() {
    override val lang: String = "jp"

    //Latest updates currently returns duplicate manga as it separates manga into chapters
    override val supportsLatest = false
    override val name = "Sen Manga"
    override val baseUrl = "http://raw.senmanga.com"

    override val client: OkHttpClient
        get() = super.client.newBuilder().addInterceptor {
            //Intercept any image requests and add a referer to them
            //Enables bandwidth stealing feature
            val request = if(it.request().url().pathSegments().firstOrNull()?.trim()?.toLowerCase() == "viewer") {
                it.request().newBuilder()
                        .addHeader("Referer", it.request().url().newBuilder()
                                .removePathSegment(0)
                                .toString())
                        .build()
            } else {
                it.request()
            }
            it.proceed(request)
        }.build()

    //Sen Manga doesn't follow the specs and decides to use multiple elements with the same ID on the page...
    override fun popularMangaSelector() = "#manga-list"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val linkElement = element.select("h1 a")

        url = linkElement.attr("href")

        title = linkElement.text()

        thumbnail_url = baseUrl + element.getElementsByClass("series-cover").attr("src")
    }

    override fun popularMangaNextPageSelector() = "#Navigation > span > ul > li:nth-last-child(2)"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = document.select(popularMangaNextPageSelector()).let {
            it.isNotEmpty() && it.text().trim().toLowerCase() == "Next"
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() = ".search-results"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val coverImage = element.getElementsByTag("img")

        url = coverImage.parents().attr("href")

        title = coverImage.attr("alt")

        thumbnail_url = baseUrl + coverImage.attr("src")
    }

    //Sen Manga search returns one page max!
    override fun searchMangaNextPageSelector() = null

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/Manga/?order=popular&page=$page")

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException("This method should not be called!")
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException("This method should not be called!")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if(response.request().url().pathSegments().firstOrNull()?.toLowerCase() != "search.php") {
            //Use popular manga parser if we are not actually doing text search
            return popularMangaParse(response)
        } else {
            val document = response.asJsoup()

            val mangas = document.select(searchMangaSelector()).map { element ->
                searchMangaFromElement(element)
            }

            return MangasPage(mangas, false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if(query.isNullOrBlank()) {
            val genreFilter = filters.find { it is GenreFilter } as GenreFilter
            val sortFilter = filters.find { it is SortFilter } as SortFilter
            if(!sortFilter.isDefault() || genreFilter.genrePath() == ALL_GENRES_PATH) {
                val uri = Uri.parse("$baseUrl/Manga/")
                        .buildUpon()
                sortFilter.addToUri(uri)
                return GET(uri.toString())
            } else {
                return GET("$baseUrl/directory/category/${genreFilter.genrePath()}/")
            }
        } else {
            val uri = Uri.parse("$baseUrl/Search.php")
                    .buildUpon()
                    .appendQueryParameter("q", query)
            return GET(uri.toString())
        }
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException("This method should not be called!")
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1[itemprop=name]").text()

        thumbnail_url = baseUrl + document.select(".cover > img").attr("src")

        val seriesDesc = document.getElementsByClass("series_desc")

        //Get the next paragraph after paragraph with "Categorize in:"
        genre = seriesDesc.first()
                .children()
                .find {
                    it.tagName().toLowerCase() == "p"
                            && it.text().trim().toLowerCase() == "categorize in:"
                }?.nextElementSibling()
                ?.text()
                ?.trim()


        author = seriesDesc.select("div > span")?.text()?.trim()

        seriesDesc?.first()?.children()?.forEach {
            val keyText = it.select("p > strong").text().trim().toLowerCase()
            val valueText = it.select("p > .desc").text().trim()

            when (keyText) {
                "artist:" -> artist = valueText
                "status:" -> status = when (valueText.toLowerCase()) {
                    "ongoing" -> SManga.ONGOING
                    "complete" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        }

        description = seriesDesc.select("div[itemprop=description]").text()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException("This method should not be called!")
    }

    //This may be unreliable as Sen Manga breaks the specs by having multiple elements with the same ID
    override fun chapterListSelector() = "#post > table > tbody > tr:not(.headline)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val linkElement = element.getElementsByTag("a")

        url = linkElement.attr("href")

        name = linkElement.text()

        chapter_number = element.child(0).text().trim().toFloatOrNull() ?: -1f

        date_upload = parseRelativeDate(element.children().last().text().trim().toLowerCase())
    }

    /**
     * Parses dates in this form:
     * `11 days ago`
     */
    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.split(" ")

        if (trimmedDate[2] != "ago") return 0

        val number = trimmedDate[0].toIntOrNull() ?: return 0
        val unit = trimmedDate[1].removeSuffix("s") //Remove 's' suffix

        val now = Calendar.getInstance()

        //Map English unit to Java unit
        val javaUnit = when (unit) {
            "year" -> Calendar.YEAR
            "month" -> Calendar.MONTH
            "week" -> Calendar.WEEK_OF_MONTH
            "day" -> Calendar.DAY_OF_MONTH
            "hour" -> Calendar.HOUR
            "minute" -> Calendar.MINUTE
            "second" -> Calendar.SECOND
            else -> return 0
        }

        now.add(javaUnit, -number)

        return now.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        //Base URI (document URI but without page index)
        val baseUri = Uri.parse(baseUrl).buildUpon().apply {
            Uri.parse(document.baseUri()).pathSegments.let {
                it.take(it.size - 1)
            }.forEach {
                appendPath(it)
            }
        }.build()

        //Base Image URI (document URI but without page index and with "viewer" inserted as first path segment
        val baseImageUri = Uri.parse(baseUrl).buildUpon().appendPath("viewer").apply {
            baseUri.pathSegments.forEach {
                appendPath(it)
            }
        }.build()

        return document.select("select[name=page] > option").map {
            val index = it.attr("value")

            val uri = baseUri.buildUpon().appendPath(index).build()

            val imageUriBuilder = baseImageUri.buildUpon().appendPath(index).build()

            Page(index.toInt() - 1, uri.toString(), imageUriBuilder.toString())
        }
    }

    override fun imageUrlParse(document: Document): String {
        //We are able to get the image URL directly from the page list
        throw UnsupportedOperationException("This method should not be called!")
    }

    override fun getFilterList() = FilterList(
            Filter.Header("NOTE: Ignored if using text search!"),
            GenreFilter(),
            Filter.Header("NOTE: Sort ignores genres search!"),
            SortFilter()
    )

    private class GenreFilter: Filter.Select<String>("Genre", GENRES.map { it.second }.toTypedArray()) {
        fun genrePath() = GENRES[state].first
    }

    private class SortFilter: UriSelectFilter("Sort", "order", arrayOf(
                    Pair("popular", "Popularity"),
                    Pair("title", "Title"),
                    Pair("rating", "Rating")
            ), false) {
        fun isDefault() = state == 0
    }

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    private open class UriSelectFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
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
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    companion object {
        private val ALL_GENRES_PATH = "all"
        //<path, display name>
        private val GENRES = listOf(
                Pair(ALL_GENRES_PATH, "All"),
                Pair("Action", "Action"),
                Pair("Adult", "Adult"),
                Pair("Adventure", "Adventure"),
                Pair("Comedy", "Comedy"),
                Pair("Cooking", "Cooking"),
                Pair("Drama", "Drama"),
                Pair("Ecchi", "Ecchi"),
                Pair("Fantasy", "Fantasy"),
                Pair("Gender-Bender", "Gender Bender"),
                Pair("Harem", "Harem"),
                Pair("Historical", "Historical"),
                Pair("Horror", "Horror"),
                Pair("Josei", "Josei"),
                Pair("Light_Novel", "Light Novel"),
                Pair("Martial_Arts", "Martial Arts"),
                Pair("Mature", "Mature"),
                Pair("Music", "Music"),
                Pair("Mystery", "Mystery"),
                Pair("Psychological", "Psychological"),
                Pair("Romance", "Romance"),
                Pair("School_Life", "School Life"),
                Pair("Sci-Fi", "Sci-Fi"),
                Pair("Seinen", "Seinen"),
                Pair("Shoujo", "Shoujo"),
                Pair("Shoujo-Ai", "Shoujo Ai"),
                Pair("Shounen", "Shounen"),
                Pair("Shounen-Ai", "Shounen Ai"),
                Pair("Slice_of_Life", "Slice of Life"),
                Pair("Smut", "Smut"),
                Pair("Sports", "Sports"),
                Pair("Supernatural", "Supernatural"),
                Pair("Tragedy", "Tragedy"),
                Pair("Webtoons", "Webtoons"),
                Pair("Yaoi", "Yaoi"),
                Pair("Yuri", "Yuri")
        )
    }
}
