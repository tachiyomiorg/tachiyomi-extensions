package eu.kanade.tachiyomi.extension.en.mangamutiny

import android.net.Uri
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.collections.ArrayList
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response

fun JsonObject.getNullable(key: String): JsonElement? {
    val value: JsonElement = this.get(key) ?: return null

    if (value.isJsonNull) {
        return null
    }

    return value
}

class MangaMutiny : HttpSource() {

    override val name = "Manga Mutiny"
    override val baseUrl = "https://api.mangamutiny.org"
    override val supportsLatest = true

    override val lang = "en"

    private val parser = JsonParser()

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().apply {
            add("Accept", "application/json")
            add("Origin", "https://mangamutiny.org")
        }
    }

    private val apiMangaUrlPath = "v1/public/manga"
    private val apiChapterUrlPath = "v1/public/chapter"

    private val fetchAmount = 21

    // Popular manga
    override fun popularMangaRequest(page: Int): Request = mangaRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = mangaParse(response)

    // Chapters
    override fun chapterListRequest(manga: SManga): Request =
        mangaDetailsRequestCommon(manga, false)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = ArrayList<SChapter>()
        val responseBody = response.body()

        if (responseBody != null) {
            val jsonChapters = JsonParser().parse(responseBody.charStream()).asJsonObject
                .get("chapters").asJsonArray
            for (singleChapterJsonElement in jsonChapters) {
                val singleChapterJsonObject = singleChapterJsonElement.asJsonObject

                chapterList.add(SChapter.create().apply {
                    name = chapterTitleBuilder(singleChapterJsonObject)
                    url = singleChapterJsonObject.get("slug").asString
                    date_upload = parseDate(singleChapterJsonObject.get("releasedAt").asString)
                })
            }
        }

        return chapterList
    }

    private fun chapterTitleBuilder(rootNode: JsonObject): String {
        val volume = rootNode.getNullable("volume")?.asInt

        val chapter = rootNode.getNullable("chapter")?.asInt

        val textTitle = rootNode.getNullable("title")?.asString

        val chapterTitle = StringBuilder()
        if (volume != null) chapterTitle.append("Vol. $volume")
        if (chapter != null) {
            if (volume != null) chapterTitle.append(" ")
            chapterTitle.append("Chapter $chapter")
        }
        if (textTitle != null && textTitle != "") {
            if (volume != null || chapter != null) chapterTitle.append(" ")
            chapterTitle.append(textTitle)
        }

        return chapterTitle.toString()
    }

    private fun parseDate(dateAsString: String): Long {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        format.timeZone = TimeZone.getTimeZone("UTC")

        return format.parse(dateAsString)?.time ?: 0
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request =
        mangaRequest(page, FilterList(SortFilter().apply { this.state = 1 }))

    override fun latestUpdatesParse(response: Response): MangasPage = mangaParse(response)

    // browse + latest + search
    override fun mangaDetailsRequest(manga: SManga): Request = mangaDetailsRequestCommon(manga)

    private fun mangaDetailsRequestCommon(manga: SManga, lite: Boolean = true): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
            .appendEncodedPath(apiMangaUrlPath)
            .appendPath(manga.url)

        if (lite) uri.appendQueryParameter("lite", "1")

        return GET(uri.build().toString(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = SManga.create()
        val responseBody = response.body()

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
                artist = rootNode.get("artists").asString
                author = rootNode.get("authors").asString

                genre = rootNode.get("tags").asJsonArray
                    .joinToString { singleGenre -> singleGenre.asString }
            }
        }

        return manga
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
            .appendEncodedPath(apiChapterUrlPath)
            .appendEncodedPath(chapter.url)

        return GET(uri.build().toString(), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageList = ArrayList<Page>()

        val responseBody = response.body()

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
        }

        return pageList
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        mangaRequest(page, filters, query)

    override fun searchMangaParse(response: Response): MangasPage = mangaParse(response)

    // commonly functions
    private fun mangaParse(response: Response): MangasPage {
        val mangasPage = ArrayList<SManga>()
        val responseBody = response.body()

        if (responseBody != null) {
            val rootNode = parser.parse(responseBody.charStream())

            if (rootNode.isJsonObject) {
                val rootObject = rootNode.asJsonObject
                val itemsArray = rootObject.get("items").asJsonArray

                for (singleItem in itemsArray) {
                    val mangaObject = singleItem.asJsonObject
                    mangasPage.add(SManga.create().apply {
                        this.title = mangaObject.get("title").asString
                        this.thumbnail_url = mangaObject.getNullable("thumbnail")?.asString
                        this.url = mangaObject.get("slug").asString
                    })
                }
            }

            responseBody.close()
        }

        return MangasPage(mangasPage, mangasPage.size == fetchAmount)
    }

    private fun mangaRequest(page: Int, filters: FilterList? = null, query: String? = null): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
        uri.appendEncodedPath(apiMangaUrlPath)
        if (query?.isNotBlank() == true) {
            uri.appendQueryParameter("text", query)
        }

        if (filters != null) {
            val uriParameterMap = mutableMapOf<String, String>()

            for (singleFilter in filters) {
                if (singleFilter is UriFilter) {
                    singleFilter.potentiallyAddToUriParameterMap(uriParameterMap)
                }
            }

            for (uriParameter in uriParameterMap) {
                uri.appendQueryParameter(uriParameter.key, uriParameter.value)
            }
        } else {
            uri.appendQueryParameter("sort", "-rating -ratingCount")
        }
        uri.appendQueryParameter("limit", fetchAmount.toString())

        if (page != 1) {
            uri.appendQueryParameter("skip", (page * fetchAmount).toString())
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
        fun potentiallyAddToUriParameterMap(parameterMap: MutableMap<String, String>)
        fun appendValueToKeyInUriParameterMap(parameterMap: MutableMap<String, String>, parameterName: String, additionalValue: String) {
            if (additionalValue.isNotEmpty()) {
                val newParameterValueBuilder = StringBuilder()
                if (parameterMap[parameterName] != null) {
                    newParameterValueBuilder.append(parameterMap[parameterName] + " ")
                }
                newParameterValueBuilder.append(additionalValue)

                parameterMap[parameterName] = newParameterValueBuilder.toString()
            }
        }
    }
    private open class UriSelectFilter(
        displayName: String,
        val uriParam: String,
        val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {

        // If not otherwise specified, any new parameter will overwrite any existing parameter in the parameter map
        override fun potentiallyAddToUriParameterMap(parameterMap: MutableMap<String, String>) {
            if (state != 0 || !firstIsUnspecified) {
                parameterMap[uriParam] = vals[state].first
            }
        }
    }

    private class StatusFilter : UriSelectFilter("Status", "status", arrayOf(
        Pair("", "All"),
        Pair("completed", "Completed"),
        Pair("ongoing", "Ongoing")
    ))

    private class CategoryFilter : UriSelectFilter("Category", "tags", arrayOf(
        Pair("", "All"),
        Pair("josei", "Josei"),
        Pair("seinen", "Seinen"),
        Pair("shoujo", "Shoujo"),
        Pair("shounen", "Shounen")
    )) {
        override fun potentiallyAddToUriParameterMap(parameterMap: MutableMap<String, String>) =
            appendValueToKeyInUriParameterMap(parameterMap, uriParam, vals[state].first)
    }

    // A single filter: either a genre or a format filter
    private class GenreFilter(val uriParam: String, displayName: String) : Filter.CheckBox(displayName)

    // A collection of genre or format filters
    private abstract class GenreFilterList(name: String, elementList: List<GenreFilter>) : Filter.Group<GenreFilter>(name, elementList), UriFilter {
        override fun potentiallyAddToUriParameterMap(parameterMap: MutableMap<String, String>) {
            val genresParameterValue = state.filter { it.state }.joinToString(" ") { it.uriParam }
            if (genresParameterValue.isNotEmpty()) {
                appendValueToKeyInUriParameterMap(parameterMap, "tags", genresParameterValue)
            }
        }
    }
    // Actual genere filter list
    private class GenresFilter : GenreFilterList("Genres", listOf(
        GenreFilter("action", "action"),
        GenreFilter("adult", "adult"),
        GenreFilter("adventure", "adventure"),
        GenreFilter("aliens", "aliens"),
        GenreFilter("animals", "animals"),
        GenreFilter("comedy", "comedy"),
        GenreFilter("cooking", "cooking"),
        GenreFilter("crossdressing", "crossdressing"),
        GenreFilter("delinquents", "delinquents"),
        GenreFilter("demons", "demons"),
        GenreFilter("drama", "drama"),
        GenreFilter("ecchi", "ecchi"),
        GenreFilter("fantasy", "fantasy"),
        GenreFilter("gender_bender", "gender bender"),
        GenreFilter("genderswap", "genderswap"),
        GenreFilter("ghosts", "ghosts"),
        GenreFilter("gore", "gore"),
        GenreFilter("gyaru", "gyaru"),
        GenreFilter("harem", "harem"),
        GenreFilter("historical", "historical"),
        GenreFilter("horror", "horror"),
        GenreFilter("incest", "incest"),
        GenreFilter("isekai", "isekai"),
        GenreFilter("loli", "loli"),
        GenreFilter("magic", "magic"),
        GenreFilter("magical_girls", "magical girls"),
        GenreFilter("mangamutiny", "mangamutiny"),
        GenreFilter("martial_arts", "martial arts"),
        GenreFilter("mature", "mature"),
        GenreFilter("mecha", "mecha"),
        GenreFilter("medical", "medical"),
        GenreFilter("military", "military"),
        GenreFilter("monster_girls", "monster girls"),
        GenreFilter("monsters", "monsters"),
        GenreFilter("mystery", "mystery"),
        GenreFilter("ninja", "ninja"),
        GenreFilter("office_workers", "office workers"),
        GenreFilter("philosophical", "philosophical"),
        GenreFilter("psychological", "psychological"),
        GenreFilter("reincarnation", "reincarnation"),
        GenreFilter("reverse_harem", "reverse harem"),
        GenreFilter("romance", "romance"),
        GenreFilter("school_life", "school life"),
        GenreFilter("sci_fi", "sci fi"),
        GenreFilter("sci-fi", "sci-fi"),
        GenreFilter("sexual_violence", "sexual violence"),
        GenreFilter("shota", "shota"),
        GenreFilter("shoujo_ai", "shoujo ai"),
        GenreFilter("shounen_ai", "shounen ai"),
        GenreFilter("slice_of_life", "slice of life"),
        GenreFilter("smut", "smut"),
        GenreFilter("sports", "sports"),
        GenreFilter("superhero", "superhero"),
        GenreFilter("supernatural", "supernatural"),
        GenreFilter("survival", "survival"),
        GenreFilter("time_travel", "time travel"),
        GenreFilter("tragedy", "tragedy"),
        GenreFilter("video_games", "video games"),
        GenreFilter("virtual_reality", "virtual reality"),
        GenreFilter("webtoons", "webtoons"),
        GenreFilter("wuxia", "wuxia"),
        GenreFilter("zombies", "zombies")
    ))

    // Actual format filter List
    private class FormatsFilter : GenreFilterList("Formats", listOf(
        GenreFilter("4-koma", "4-koma"),
        GenreFilter("adaptation", "adaptation"),
        GenreFilter("anthology", "anthology"),
        GenreFilter("award_winning", "award winning"),
        GenreFilter("doujinshi", "doujinshi"),
        GenreFilter("fan_colored", "fan colored"),
        GenreFilter("full_color", "full color"),
        GenreFilter("long_strip", "long strip"),
        GenreFilter("official_colored", "official colored"),
        GenreFilter("oneshot", "oneshot"),
        GenreFilter("web_comic", "web comic")
    ))

    private class SortFilter : UriSelectFilter("Sort", "sort", arrayOf(
        Pair("-rating -ratingCount", "Popular"),
        Pair("-lastReleasedAt", "Last update"),
        Pair("-createdAt", "Newest"),
        Pair("title", "Name")
    ), firstIsUnspecified = false, defaultValue = 0)

    private class AuthorFilter : Filter.Text("Manga Author & Artist"), UriFilter {
        override fun potentiallyAddToUriParameterMap(parameterMap: MutableMap<String, String>) {
            if (state.isNotEmpty()) {
                parameterMap["creator"] = state
            }
        }
    }

    /**The scanlator filter exists on the mangamutiny website website, however it doesn't work.
    This should stay disabled in the extension until it's properly implemented on the website,
    otherwise users may be confused by searches that return no results.**/
    /*
    private class ScanlatorFilter : Filter.Text("Scanlator Name"), UriFilter {
        override fun potentiallyAddToUriParameterMap(parameterMap: MutableMap<String, String>) {
            if (state.isNotEmpty()) {
                parameterMap["scanlator"] = state
            }
        }
    }
     */
}
