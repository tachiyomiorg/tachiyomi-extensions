package eu.kanade.tachiyomi.extension.en.mangamutiny

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class MangaMutiny : HttpSource() {

    override val name = "Manga Mutiny"
    override val baseUrl = "https://mangamutiny.org"

    override val supportsLatest = true

    override val lang = "en"

    private val json: Json by injectLazy()

    private val baseUrlAPI = "https://api.mangamutiny.org"

    private val webViewSingleMangaPath = "title/"
    private val webViewMultipleMangaPath = "titles/"

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().apply {
            add("Accept", "application/json")
            add("Origin", "https://mangamutiny.org")
        }
    }

    private val apiMangaUrlPath = "v1/public/manga"
    private val apiChapterUrlPath = "v1/public/chapter"

    private val fetchAmount = 21

    companion object {
        const val PREFIX_ID_SEARCH = "slug:"
    }

    // Popular manga
    override fun popularMangaRequest(page: Int): Request = mangaRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = mangaParse(response)

    // Chapters
    override fun chapterListRequest(manga: SManga): Request =
        mangaDetailsRequestCommon(manga, false)

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseBody = response.body

        return responseBody?.use {
            json.decodeFromString(ListChapterDS, it.string()).also {
                responseBody.close()
            }
        } ?: listOf()
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request =
        mangaRequest(page, filters = FilterList(SortFilter().apply { this.state = 1 }))

    override fun latestUpdatesParse(response: Response): MangasPage = mangaParse(response)

    // browse + latest + search
    override fun mangaDetailsRequest(manga: SManga): Request = mangaDetailsRequestCommon(manga)

    private fun mangaDetailsRequestCommon(manga: SManga, lite: Boolean = true): Request {
        val uri = if (isForWebView()) {
            Uri.parse(baseUrl).buildUpon()
                .appendEncodedPath(webViewSingleMangaPath)
                .appendPath(manga.url)
        } else {
            Uri.parse(baseUrlAPI).buildUpon()
                .appendEncodedPath(apiMangaUrlPath)
                .appendPath(manga.url).let {
                    if (lite) it.appendQueryParameter("lite", "1") else it
                }
        }

        return GET(uri.build().toString(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val responseBody = response.body

        if (responseBody != null) {
            return responseBody.use {
                json.decodeFromString(SMangaDS, it.string())
            }
        } else {
            throw IllegalStateException("Response code ${response.code}")
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val uri = Uri.parse(baseUrlAPI).buildUpon()
            .appendEncodedPath(apiChapterUrlPath)
            .appendEncodedPath(chapter.url)

        return GET(uri.build().toString(), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val responseBody = response.body

        return responseBody?.use {
            json.decodeFromString(ListPageDS, it.string())
        } ?: listOf()
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        mangaRequest(page, query, filters)

    override fun searchMangaParse(response: Response): MangasPage = mangaParse(response)

    // commonly used functions
    private fun mangaParse(response: Response): MangasPage {
        val responseBody = response.body

        return if (responseBody != null) {
            val deserializationResult = json.decodeFromString(PageInfoDS, responseBody.string())
            val totalObjects = deserializationResult.second
            val skipped = response.request.url.queryParameter("skip")?.toInt() ?: 0
            val moreElementsToSkip = skipped + fetchAmount < totalObjects
            val pageSizeEqualsFetchAmount = deserializationResult.first.size == fetchAmount
            val hasMorePages = pageSizeEqualsFetchAmount && moreElementsToSkip

            MangasPage(deserializationResult.first, hasMorePages)
        } else {
            MangasPage(listOf(), false)
        }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {

        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)

            val tempManga = SManga.create().apply {
                url = realQuery
            }

            client.newCall(mangaDetailsRequestCommon(tempManga, true))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    MangasPage(listOf(details), false)
                }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    private fun mangaRequest(page: Int, query: String? = null, filters: FilterList? = null): Request {
        val forWebView = isForWebView()

        val uri = if (forWebView) {
            Uri.parse(baseUrl).buildUpon().apply {
                appendEncodedPath(webViewMultipleMangaPath)
            }
        } else {
            Uri.parse(baseUrlAPI).buildUpon().apply {
                appendEncodedPath(apiMangaUrlPath)
            }
        }

        if (query?.isNotBlank() == true) {
            uri.appendQueryParameter("text", query)
        }

        val applicableFilters = if (filters != null && filters.isNotEmpty()) {
            filters
        } else {
            FilterList(SortFilter())
        }

        val uriParameterMap = mutableMapOf<String, String>()

        for (singleFilter in applicableFilters) {
            if (singleFilter is UriFilter) {
                singleFilter.addParameter(uriParameterMap)
            }
        }

        for (uriParameter in uriParameterMap) {
            uri.appendQueryParameter(uriParameter.key, uriParameter.value)
        }

        if (!forWebView) {
            uri.appendQueryParameter("limit", fetchAmount.toString())
            if (page != 1) {
                uri.appendQueryParameter("skip", ((page - 1) * fetchAmount).toString())
            }
        }

        return GET(uri.build().toString(), headers)
    }

    // Filter
    override fun getFilterList(): FilterList {
        return FilterList(
            StatusFilter(),
            CategoryFilter(),
            GenresFilter(),
            FormatsFilter(),
            SortFilter(),
            AuthorFilter()
            // ScanlatorFilter()
        )
    }

    override fun imageUrlParse(response: Response): String {
        throw Exception("Not used")
    }

    private interface UriFilter {
        val uriParam: () -> String
        val shouldAdd: () -> Boolean
        val getParameter: () -> String

        fun addParameter(parameterMap: MutableMap<String, String>) {
            if (shouldAdd()) {
                val newParameterValueBuilder = StringBuilder()
                if (parameterMap[uriParam()] != null) {
                    newParameterValueBuilder.append(parameterMap[uriParam()] + " ")
                }
                newParameterValueBuilder.append(getParameter())

                parameterMap[uriParam()] = newParameterValueBuilder.toString()
            }
        }
    }

    private abstract class UriSelectFilter(
        displayName: String,
        override val uriParam: () -> String,
        val vals: Array<Pair<String, String>>,
        val defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {

        override val shouldAdd = fun() =
            this.state != defaultValue

        override val getParameter = fun() = vals[state].first
    }

    private class StatusFilter : UriSelectFilter(
        "Status",
        fun() = "status",
        arrayOf(
            Pair("", "All"),
            Pair("completed", "Completed"),
            Pair("ongoing", "Ongoing")
        )
    )

    private class CategoryFilter : UriSelectFilter(
        "Category",
        fun() = "tags",
        arrayOf(
            Pair("", "All"),
            Pair("josei", "Josei"),
            Pair("seinen", "Seinen"),
            Pair("shoujo", "Shoujo"),
            Pair("shounen", "Shounen")
        )
    )

    // A single filter: either a genre or a format filter
    private class GenreOrFormatFilter(val uriParam: String, displayName: String) :
        Filter.CheckBox(displayName)

    // A collection of genre or format filters
    private abstract class GenreOrFormatFilterList(name: String, specificUriParam: String, elementList: List<GenreOrFormatFilter>) : Filter.Group<GenreOrFormatFilter>(name, elementList), UriFilter {

        override val shouldAdd = fun() = state.any { it.state }

        override val getParameter = fun() =
            state.filter { it.state }.joinToString(" ") { it.uriParam }

        override val uriParam = fun() = if (isForWebView()) specificUriParam else "tags"
    }

    // Generes filter list
    private class GenresFilter : GenreOrFormatFilterList(
        "Genres",
        "genres",
        listOf(
            GenreOrFormatFilter("action", "action"),
            GenreOrFormatFilter("adult", "adult"),
            GenreOrFormatFilter("adventure", "adventure"),
            GenreOrFormatFilter("aliens", "aliens"),
            GenreOrFormatFilter("animals", "animals"),
            GenreOrFormatFilter("comedy", "comedy"),
            GenreOrFormatFilter("cooking", "cooking"),
            GenreOrFormatFilter("crossdressing", "crossdressing"),
            GenreOrFormatFilter("delinquents", "delinquents"),
            GenreOrFormatFilter("demons", "demons"),
            GenreOrFormatFilter("drama", "drama"),
            GenreOrFormatFilter("ecchi", "ecchi"),
            GenreOrFormatFilter("fantasy", "fantasy"),
            GenreOrFormatFilter("gender_bender", "gender bender"),
            GenreOrFormatFilter("genderswap", "genderswap"),
            GenreOrFormatFilter("ghosts", "ghosts"),
            GenreOrFormatFilter("gore", "gore"),
            GenreOrFormatFilter("gyaru", "gyaru"),
            GenreOrFormatFilter("harem", "harem"),
            GenreOrFormatFilter("historical", "historical"),
            GenreOrFormatFilter("horror", "horror"),
            GenreOrFormatFilter("incest", "incest"),
            GenreOrFormatFilter("isekai", "isekai"),
            GenreOrFormatFilter("loli", "loli"),
            GenreOrFormatFilter("magic", "magic"),
            GenreOrFormatFilter("magical_girls", "magical girls"),
            GenreOrFormatFilter("mangamutiny", "mangamutiny"),
            GenreOrFormatFilter("martial_arts", "martial arts"),
            GenreOrFormatFilter("mature", "mature"),
            GenreOrFormatFilter("mecha", "mecha"),
            GenreOrFormatFilter("medical", "medical"),
            GenreOrFormatFilter("military", "military"),
            GenreOrFormatFilter("monster_girls", "monster girls"),
            GenreOrFormatFilter("monsters", "monsters"),
            GenreOrFormatFilter("mystery", "mystery"),
            GenreOrFormatFilter("ninja", "ninja"),
            GenreOrFormatFilter("office_workers", "office workers"),
            GenreOrFormatFilter("philosophical", "philosophical"),
            GenreOrFormatFilter("psychological", "psychological"),
            GenreOrFormatFilter("reincarnation", "reincarnation"),
            GenreOrFormatFilter("reverse_harem", "reverse harem"),
            GenreOrFormatFilter("romance", "romance"),
            GenreOrFormatFilter("school_life", "school life"),
            GenreOrFormatFilter("sci_fi", "sci fi"),
            GenreOrFormatFilter("sci-fi", "sci-fi"),
            GenreOrFormatFilter("sexual_violence", "sexual violence"),
            GenreOrFormatFilter("shota", "shota"),
            GenreOrFormatFilter("shoujo_ai", "shoujo ai"),
            GenreOrFormatFilter("shounen_ai", "shounen ai"),
            GenreOrFormatFilter("slice_of_life", "slice of life"),
            GenreOrFormatFilter("smut", "smut"),
            GenreOrFormatFilter("sports", "sports"),
            GenreOrFormatFilter("superhero", "superhero"),
            GenreOrFormatFilter("supernatural", "supernatural"),
            GenreOrFormatFilter("survival", "survival"),
            GenreOrFormatFilter("time_travel", "time travel"),
            GenreOrFormatFilter("tragedy", "tragedy"),
            GenreOrFormatFilter("video_games", "video games"),
            GenreOrFormatFilter("virtual_reality", "virtual reality"),
            GenreOrFormatFilter("webtoons", "webtoons"),
            GenreOrFormatFilter("wuxia", "wuxia"),
            GenreOrFormatFilter("zombies", "zombies")
        )
    )

    // Actual format filter List
    private class FormatsFilter : GenreOrFormatFilterList(
        "Formats",
        "formats",
        listOf(
            GenreOrFormatFilter("4-koma", "4-koma"),
            GenreOrFormatFilter("adaptation", "adaptation"),
            GenreOrFormatFilter("anthology", "anthology"),
            GenreOrFormatFilter("award_winning", "award winning"),
            GenreOrFormatFilter("doujinshi", "doujinshi"),
            GenreOrFormatFilter("fan_colored", "fan colored"),
            GenreOrFormatFilter("full_color", "full color"),
            GenreOrFormatFilter("long_strip", "long strip"),
            GenreOrFormatFilter("official_colored", "official colored"),
            GenreOrFormatFilter("oneshot", "oneshot"),
            GenreOrFormatFilter("web_comic", "web comic")
        ),

    )

    private class SortFilter : UriSelectFilter(
        "Sort",
        fun() = "sort",
        arrayOf(
            Pair("title", "Name"),
            Pair("-lastReleasedAt", "Last update"),
            Pair("-createdAt", "Newest"),
            Pair("-rating -ratingCount", "Popular")
        ),
        defaultValue = 3
    ) {
        override val shouldAdd = fun() = if (isForWebView()) state != defaultValue else true

        override val getParameter = fun(): String {
            return if (isForWebView()) {
                this.state.toString()
            } else {
                this.vals[this.state].first
            }
        }
    }

    private class AuthorFilter : Filter.Text("Manga Author & Artist"), UriFilter {
        override val uriParam = fun() = "creator"

        override val shouldAdd = fun() = state.isNotEmpty()

        override val getParameter = fun(): String = state
    }

    /**The scanlator filter exists on the mangamutiny website, however it doesn't work.
     This should stay disabled in the extension until it's properly implemented on the website,
     otherwise users may be confused by searches that return no results.**/
    /*
    private class ScanlatorFilter : Filter.Text("Scanlator Name"), UriFilter {
        override val uriParam = fun() = "scanlator"

        override val shouldAdd = fun() = state.isNotEmpty()

        override val getParameter = fun(): String = state
    }
     */
}

private fun isForWebView(): Boolean =
    Thread.currentThread().stackTrace.map { it.methodName }
        .firstOrNull {
            it.contains("WebView", true) && !it.contains("isForWebView")
        } != null
