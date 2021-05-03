package eu.kanade.tachiyomi.extension.en.mangamutiny

import android.net.Uri
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun JsonObject.getNullable(key: String): JsonElement? {
    val value: JsonElement = this.get(key) ?: return null

    if (value.isJsonNull) {
        return null
    }

    return value
}

fun Float.toStringWithoutDotZero(): String = when (this % 1) {
    0F -> this.toInt().toString()
    else -> this.toString()
}

class MangaMutiny : HttpSource() {

    override val name = "Manga Mutiny"
    override val baseUrl = "https://mangamutiny.org"

    override val supportsLatest = true

    override val lang = "en"

    private val parser = JsonParser()

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
        val chapterList = mutableListOf<SChapter>()
        val responseBody = response.body

        if (responseBody != null) {
            val jsonChapters = JsonParser().parse(responseBody.charStream()).asJsonObject
                .get("chapters").asJsonArray
            for (singleChapterJsonElement in jsonChapters) {
                val singleChapterJsonObject = singleChapterJsonElement.asJsonObject

                chapterList.add(
                    SChapter.create().apply {
                        name = chapterTitleBuilder(singleChapterJsonObject)
                        url = singleChapterJsonObject.get("slug").asString
                        date_upload = parseDate(singleChapterJsonObject.get("releasedAt").asString)

                        chapterNumberBuilder(singleChapterJsonObject)?.let { chapterNumber ->
                            chapter_number = chapterNumber
                        }
                    }
                )
            }

            responseBody.close()
        }

        return chapterList
    }

    private fun chapterNumberBuilder(rootNode: JsonObject): Float? =
        rootNode.getNullable("chapter")?.asFloat

    private fun chapterTitleBuilder(rootNode: JsonObject): String {
        val volume = rootNode.getNullable("volume")?.asInt

        val chapter = rootNode.getNullable("chapter")?.asFloat?.toStringWithoutDotZero()

        val textTitle = rootNode.getNullable("title")?.asString

        val chapterTitle = StringBuilder()
        if (volume != null) chapterTitle.append("Vol. $volume")
        if (chapter != null) {
            if (volume != null) chapterTitle.append(" ")
            chapterTitle.append("Chapter $chapter")
        }
        if (textTitle != null && textTitle != "") {
            if (volume != null || chapter != null) chapterTitle.append(": ")
            chapterTitle.append(textTitle)
        }

        return chapterTitle.toString()
    }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun parseDate(dateAsString: String): Long =
        dateFormatter.parse(dateAsString)?.time ?: 0

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
        val manga = SManga.create()
        val responseBody = response.body

        if (responseBody != null) {
            val rootNode = parser.parse(responseBody.charStream()).asJsonObject
            manga.apply {
                status = when (rootNode.get("status").asString) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                description = rootNode.getNullable("summary")?.asString
                thumbnail_url = rootNode.getNullable("thumbnail")?.asString
                title = rootNode.get("title").asString
                url = rootNode.get("slug").asString
                artist = rootNode.getNullable("artists")?.asString
                author = rootNode.get("authors").asString

                genre = rootNode.get("tags").asJsonArray
                    .joinToString { singleGenre -> singleGenre.asString }
            }

            responseBody.close()
        }

        return manga
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val uri = Uri.parse(baseUrlAPI).buildUpon()
            .appendEncodedPath(apiChapterUrlPath)
            .appendEncodedPath(chapter.url)

        return GET(uri.build().toString(), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageList = ArrayList<Page>()

        val responseBody = response.body

        if (responseBody != null) {
            val rootNode = parser.parse(responseBody.charStream()).asJsonObject

            // Build chapter url for every image of this chapter
            val storageLocation = rootNode.get("storage").asString
            val manga = rootNode.get("manga").asString
            val chapterId = rootNode.get("id").asString

            val chapterUrl = "$storageLocation/$manga/$chapterId/"

            // Process every image of this chapter
            val images = rootNode.get("images").asJsonArray

            for (i in 0 until images.size()) {
                pageList.add(Page(i, "", chapterUrl + images[i].asString))
            }

            responseBody.close()
        }

        return pageList
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        mangaRequest(page, query, filters)

    override fun searchMangaParse(response: Response): MangasPage = mangaParse(response)

    // commonly used functions
    private fun mangaParse(response: Response): MangasPage {
        val mangasPage = ArrayList<SManga>()
        val responseBody = response.body

        var totalObjects = 0

        if (responseBody != null) {
            val rootNode = parser.parse(responseBody.charStream())

            if (rootNode.isJsonObject) {
                val rootObject = rootNode.asJsonObject
                val itemsArray = rootObject.get("items").asJsonArray

                for (singleItem in itemsArray) {
                    val mangaObject = singleItem.asJsonObject
                    mangasPage.add(
                        SManga.create().apply {
                            this.title = mangaObject.get("title").asString
                            this.thumbnail_url = mangaObject.getNullable("thumbnail")?.asString
                            this.url = mangaObject.get("slug").asString
                        }
                    )
                }

                // total number of manga the server found in its database
                // and is returning paginated page by page:
                totalObjects = rootObject.getNullable("total")?.asInt ?: 0
            }

            responseBody.close()
        }

        val skipped = response.request.url.queryParameter("skip")?.toInt() ?: 0

        val moreElementsToSkip = skipped + fetchAmount < totalObjects

        val pageSizeEqualsFetchAmount = mangasPage.size == fetchAmount

        val hasMorePages = pageSizeEqualsFetchAmount && moreElementsToSkip

        return MangasPage(mangasPage, hasMorePages)
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
