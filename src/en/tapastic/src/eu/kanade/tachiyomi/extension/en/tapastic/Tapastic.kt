package eu.kanade.tachiyomi.extension.en.tapastic

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Tapastic : ConfigurableSource, ParsedHttpSource() {

    // Preferences Code

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val chapterListPref = androidx.preference.ListPreference(screen.context).apply {
            key = SHOW_LOCKED_CHAPTERS_Title
            title = SHOW_LOCKED_CHAPTERS_Title
            entries = prefsEntries
            entryValues = prefsEntryValues
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(SHOW_LOCKED_CHAPTERS, entry).commit()
            }
        }
        screen.addPreference(chapterListPref)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val chapterListPref = ListPreference(screen.context).apply {
            key = SHOW_LOCKED_CHAPTERS_Title
            title = SHOW_LOCKED_CHAPTERS_Title
            entries = prefsEntries
            entryValues = prefsEntryValues
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(SHOW_LOCKED_CHAPTERS, entry).commit()
            }
        }
        screen.addPreference(chapterListPref)
    }

    private fun chapterListPref() = preferences.getString(SHOW_LOCKED_CHAPTERS, "free")

    companion object {
        private const val SHOW_LOCKED_CHAPTERS_Title = "Tapas requires login/payment for some chapters"
        private const val SHOW_LOCKED_CHAPTERS = "tapas_locked_chapters"
        private val prefsEntries = arrayOf("Show all chapters (including pay-to-read)", "Only show free chapters")
        private val prefsEntryValues = arrayOf("all", "free")
    }

    // Info
    override val lang = "en"
    override val supportsLatest = true
    override val name = "Tapas" // originally Tapastic
    override val baseUrl = "https://tapas.io"
    override val id = 3825434541981130345

    // Popular

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/comics?b=POPULAR&g=&f=NONE&pageNumber=$page&pageSize=20&")

    override fun popularMangaNextPageSelector() = "div[data-has-next=true]"
    override fun popularMangaSelector() = "li.js-list-item"
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        url = element.select(".item__thumb a").attr("href")
        title = element.select(".item__thumb img").attr("alt")
        thumbnail_url = element.select(".item__thumb img").attr("src")
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/comics?b=FRESH&g=&f=NONE&pageNumber=$page&pageSize=20&")

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // If there is any search text, use text search, otherwise use filter search
        val uri = if (query.isNotBlank()) {
            Uri.parse("$baseUrl/search")
                .buildUpon()
                .appendQueryParameter("t", "COMICS")
                .appendQueryParameter("q", query)
        } else {
            val uri = Uri.parse("$baseUrl/comics").buildUpon()
            // Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri
        }
        // Append page number
        uri.appendQueryParameter("pageNumber", page.toString())
        return GET(uri.toString())
    }

    override fun searchMangaNextPageSelector() =
        "${popularMangaNextPageSelector()}, a.paging__button--next"

    override fun searchMangaSelector() = "${popularMangaSelector()}, .search-item-wrap"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        url = element.select(".item__thumb a, .title-section .title a").attr("href")
        title = element.select(".item__thumb img").firstOrNull()?.attr("alt") ?: element.select(".title-section .title a").text()
        thumbnail_url = element.select(".item__thumb img, .thumb-wrap img").attr("src")
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + "${manga.url}/info")
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        genre = document.select("div.info-detail__row a.genre-btn").joinToString { it.text() }
        title = document.select("div.title-wrapper a.title").text()
        thumbnail_url = document.select("div.thumb-wrapper img").attr("abs:src")
        author = document.select("ul.creator-section a.name").joinToString { it.text() }
        artist = author
        description = document.select("div.row-body span.description__body").text()
    }

    // Chapters

    /**
     * Checklist: Paginated chapter lists, locked chapters, future chapters, early-access chapters (app only?), chapter order
     */

    private val gson by lazy { Gson() }

    private fun Element.isLockedChapter(): Boolean {
        return this.hasClass("js-have-to-sign")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.select("div.info-body__bottom a").attr("data-id")
        val chapters = mutableListOf<SChapter>()

        // recursively build the chapter list
        fun parseChapters(page: Int) {
            val url = "$baseUrl/series/$mangaId/episodes?page=$page&sort=NEWEST&init_load=0&large=true&last_access=0&"
            val json = gson.fromJson<JsonObject>(client.newCall(GET(url, headers)).execute().body()!!.string())["data"]

            Jsoup.parse(json["body"].string).select(chapterListSelector())
                .let { list ->
                    // show/don't show locked chapters based on user's preferences
                    if (chapterListPref() == "free") list.filterNot { it.isLockedChapter() } else list
                }
                .map { chapters.add(chapterFromElement(it)) }

            if (json["pagination"]["has_next"].bool) parseChapters(json["pagination"]["page"].int)
        }

        parseChapters(1)
        return chapters
    }

    override fun chapterListSelector() = "li a:not(.js-early-access):not(.js-coming-soon)"

    private val datePattern = Regex("""\w\w\w \d\d, \d\d\d\d""")

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val episode = element.select("p.scene").text()
        val chName = element.select("span.title__body").text()
        name = (if (element.isLockedChapter()) "\uD83D\uDD12 " else "") + "$episode | $chName"
        setUrlWithoutDomain(element.attr("href"))
        date_upload = datePattern.find(element.select("p.additional").text())?.value.toDate()
    }

    private fun String?.toDate(): Long {
        this ?: return 0L
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(this).time
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.content__img").mapIndexed { i, img ->
            Page(i, "", img.let { if (it.hasAttr("data-src")) it.attr("abs:data-src") else it.attr("abs:src") })
        }
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("This method should not be called!")

    // Filters

    override fun getFilterList() = FilterList(
        // Tapastic does not support genre filtering and text search at the same time
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        FilterFilter(),
        GenreFilter(),
        StatusFilter(),
        Filter.Separator(),
        Filter.Header("Sort is ignored when filter is active!"),
        SortFilter()
    )

    private class FilterFilter : UriSelectFilter(
        "Filter", "b", arrayOf(
            Pair("ALL", "None"),
            Pair("POPULAR", "Popular"),
            Pair("TRENDING", "Trending"),
            Pair("FRESH", "Fresh"),
            Pair("BINGE", "Binge"),
            Pair("ORIGINAL", "Tapas Originals")
        ), firstIsUnspecified = false, defaultValue = 1
    )

    private class GenreFilter : UriSelectFilter(
        "Genre", "g", arrayOf(
            Pair("", "Any"),
            Pair("7", "Action"),
            Pair("22", "Boys Love"),
            Pair("2", "Comedy"),
            Pair("8", "Drama"),
            Pair("3", "Fantasy"),
            Pair("24", "Girls Love"),
            Pair("9", "Gaming"),
            Pair("6", "Horror"),
            Pair("25", "LGBTQ+"),
            Pair("10", "Mystery"),
            Pair("5", "Romance"),
            Pair("4", "Science Fiction"),
            Pair("1", "Slice of Life")
        )
    )

    private class StatusFilter : UriSelectFilter(
        "Status", "f", arrayOf(
            Pair("NONE", "All"),
            Pair("F2R", "Free to read"),
            Pair("PRM", "Premium")
        )
    )

    private class SortFilter : UriSelectFilter(
        "Sort", "s", arrayOf(
            Pair("DATE", "Date"),
            Pair("LIKE", "Likes"),
            Pair("SUBSCRIBE", "Subscribers")
        )
    )

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    // vals: <name, display>
    private open class UriSelectFilter(
        displayName: String,
        val uriParam: String,
        val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue),
        UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
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
