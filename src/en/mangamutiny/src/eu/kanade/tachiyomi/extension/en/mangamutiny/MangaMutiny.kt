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
        val format = SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
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
                description = rootNode.get("summary").asString
                thumbnail_url = rootNode.get("thumbnail")?.asString
                title = rootNode.get("title").asString
                url = rootNode.get("slug").asString
                artist = rootNode.get("artists").asString
                author = rootNode.get("authors").asString

                genre = rootNode.get("genres").asJsonArray
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
                        this.thumbnail_url = mangaObject.get("thumbnail")?.asString
                        this.url = mangaObject.get("slug").asString
                    })
                }
            }

            responseBody.close()
        }

        return MangasPage(mangasPage, mangasPage.size == 20)
    }

    private fun mangaRequest(page: Int, filters: FilterList? = null, query: String? = null): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
        uri.appendEncodedPath(apiMangaUrlPath)
        if (query?.isNotBlank() == true) {
            uri.appendQueryParameter("text", query)
        }
        if (filters != null) {
            for (singleFilter in filters) {
                if (singleFilter is UriFilter) {
                    singleFilter.addToUri(uri)
                }
            }
        } else {
            uri.appendQueryParameter("sort", "-rating -ratingCount")
        }
        uri.appendQueryParameter("limit", "20")

        if (page != 1) {
            uri.appendQueryParameter("skip", (page * 20).toString())
        }
        return GET(uri.build().toString(), headers)
    }

    // Filter
    override fun getFilterList(): FilterList {
        return FilterList(
            StatusFilter(),
            CategoryFilter(),
            GenreGroup(),
            SortFilter()
        )
    }

    override fun imageUrlParse(response: Response): String {
        throw Exception("Not used")
    }

    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }
    private open class UriSelectFilter(
        displayName: String,
        val uriParam: String,
        val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    private class StatusFilter : UriSelectFilter("Status", "status", arrayOf(
        Pair("", "All"),
        Pair("completed", "Completed"),
        Pair("ongoing", "Ongoing")
    ))

    private class CategoryFilter : UriSelectFilter("Category", "category", arrayOf(
        Pair("", "All"),
        Pair("josei", "Josei"),
        Pair("seinen", "Seinen"),
        Pair("shoujo", "Shoujo"),
        Pair("shounen", "Shounen")
    ))

    private class GenreFilter(val uriParam: String, displayName: String) : Filter.CheckBox(displayName)

    private class GenreGroup : Filter.Group<GenreFilter>("Genres", listOf(
        GenreFilter("4-koma", "4-koma"),
        GenreFilter("action", "Action"),
        GenreFilter("adaptation", "Adaptation"),
        GenreFilter("adult", "Adult"),
        GenreFilter("adventure", "Adventure"),
        GenreFilter("aliens", "Aliens"),
        GenreFilter("animals", "Animals"),
        GenreFilter("anthology", "Anthology"),
        GenreFilter("award_winning", "Award winning"),
        GenreFilter("comedy", "Comedy"),
        GenreFilter("cooking", "Cooking"),
        GenreFilter("crossdressing", "Crossdressing"),
        GenreFilter("delinquents", "Delinquents"),
        GenreFilter("demons", "Demons"),
        GenreFilter("doujinshi", "Doujinshi"),
        GenreFilter("drama", "Drama"),
        GenreFilter("ecchi", "Ecchi"),
        GenreFilter("fan_colored", "Fan colored"),
        GenreFilter("fantasy", "Fantasy"),
        GenreFilter("full_color", "Full color"),
        GenreFilter("gender_bender", "Gender bender"),
        GenreFilter("genderswap", "Genderswap"),
        GenreFilter("ghosts", "Ghosts"),
        GenreFilter("gore", "Gore"),
        GenreFilter("gyaru", "Gyaru"),
        GenreFilter("harem", "Harem"),
        GenreFilter("historical", "Historical"),
        GenreFilter("horror", "Horror"),
        GenreFilter("incest", "Incest"),
        GenreFilter("isekai", "Isekai"),
        GenreFilter("josei", "Josei"),
        GenreFilter("loli", "Loli"),
        GenreFilter("long_strip", "Long strip"),
        GenreFilter("magic", "Magic"),
        GenreFilter("magical_girls", "Magical girls"),
        GenreFilter("manga", "Manga"),
        GenreFilter("mangamutiny", "Mangamutiny"),
        GenreFilter("manhua", "Manhua"),
        GenreFilter("manhwa", "Manhwa"),
        GenreFilter("martial_arts", "Martial arts"),
        GenreFilter("mature", "Mature"),
        GenreFilter("mecha", "Mecha"),
        GenreFilter("medical", "Medical"),
        GenreFilter("military", "Military"),
        GenreFilter("monster_girls", "Monster girls"),
        GenreFilter("monsters", "Monsters"),
        GenreFilter("mystery", "Mystery"),
        GenreFilter("ninja", "Ninja"),
        GenreFilter("office_workers", "Office workers"),
        GenreFilter("official_colored", "Official colored"),
        GenreFilter("oneshot", "Oneshot"),
        GenreFilter("philosophical", "Philosophical"),
        GenreFilter("psychological", "Psychological"),
        GenreFilter("reincarnation", "Reincarnation"),
        GenreFilter("reverse_harem", "Reverse harem"),
        GenreFilter("romance", "Romance"),
        GenreFilter("school_life", "School life"),
        GenreFilter("sci_fi", "Sci fi"),
        GenreFilter("sci-fi", "Sci-fi"),
        GenreFilter("seinen", "Seinen"),
        GenreFilter("sexual_violence", "Sexual violence"),
        GenreFilter("shota", "Shota"),
        GenreFilter("shoujo", "Shoujo"),
        GenreFilter("shounen", "Shounen"),
        GenreFilter("shounen_ai", "Shounen ai"),
        GenreFilter("slice_of_life", "Slice of life"),
        GenreFilter("smut", "Smut"),
        GenreFilter("sports", "Sports"),
        GenreFilter("superhero", "Superhero"),
        GenreFilter("supernatural", "Supernatural"),
        GenreFilter("survival", "Survival"),
        GenreFilter("time_travel", "Time travel"),
        GenreFilter("tragedy", "Tragedy"),
        GenreFilter("video_games", "Video games"),
        GenreFilter("virtual_reality", "Virtual reality"),
        GenreFilter("web_comic", "Web comic"),
        GenreFilter("webtoons", "Webtoons"),
        GenreFilter("wuxia", "Wuxia"),
        GenreFilter("zombies", "Zombies")
    )), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            val genresParameterValue = state.filter { it.state }.joinToString("+") { it.uriParam }
            if (genresParameterValue.isNotEmpty()) {
                uri.appendQueryParameter("genres", genresParameterValue)
            }
        }
    }

    private class SortFilter : UriSelectFilter("Sort", "sort", arrayOf(
        Pair("-rating -ratingCount", "Popular"),
        Pair("-lastReleasedAt", "Last update"),
        Pair("-createdAt", "Newest"),
        Pair("title", "Name")
    ), firstIsUnspecified = false, defaultValue = 0)
}
