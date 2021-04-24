package eu.kanade.tachiyomi.extension.en.readmanhwa

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.PreferenceScreen
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Nsfw
class ReadManhwa : ConfigurableSource, HttpSource() {

    override val name = "ReadManhwa"

    override val baseUrl = "https://www.readmanhwa.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = headersBuilder(true)

    private fun headersBuilder(enableNsfw: Boolean) = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
        .add("X-NSFW", enableNsfw.toString())

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    private fun parseMangaFromJson(response: Response): MangasPage {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        val mangas = jsonObject["data"].asJsonArray.map { json ->
            SManga.create().apply {
                title = json["title"].string
                thumbnail_url = json["image_url"].string
                url = json["slug"].string
            }
        }

        return MangasPage(mangas, jsonObject["current_page"].int < jsonObject["last_page"].int)
    }
    private fun getMangaUrl(url: String): String {
        return HttpUrl.parse(url)!!.newBuilder()
            .setQueryParameter("nsfw", isNSFWEnabledInPref().toString()).toString()
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET(getMangaUrl("$baseUrl/api/comics?per_page=36&page=$page&q=&sort=popularity&order=desc&duration=all"), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(getMangaUrl("$baseUrl/api/comics?per_page=36&page=$page&q=&sort=uploaded_at&order=desc&duration=day"), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val enableNsfw = (filters.find { it is NSFWFilter } as? Filter.CheckBox)?.state ?: true

        val url = HttpUrl.parse("$baseUrl/api/comics")!!.newBuilder()
            .addQueryParameter("per_page", "36")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("nsfw", enableNsfw.toString())

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {

                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            url.addQueryParameter("tags[]", genre)
                        }
                    }
                }
                is StatusFilter -> {
                    val statusInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            statusInclude.add(it.id)
                        }
                    }
                    if (statusInclude.isNotEmpty()) {
                        statusInclude.forEach { status ->
                            url.addQueryParameter("statuses[]", status)
                        }
                    }
                }
                is OrderBy -> {
                    val orderby = if (filter.state!!.ascending) "asc" else "desc"
                    val sort = arrayOf("uploaded_at", "title", "pages", "favorites", "popularity")[filter.state!!.index]
                    url.addQueryParameter("sort", sort)
                    url.addQueryParameter("order", orderby)
                }
                is DurationFilter -> url.addQueryParameter("duration", filter.toUriPart())
            }
        }
        return GET(url.toString(), headersBuilder(enableNsfw).build())
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Details

    // Workaround to allow "Open in browser" to use the real URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(apiMangaDetailsRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    // Return the real URL for "Open in browser"
    override fun mangaDetailsRequest(manga: SManga) = GET(getMangaUrl("$baseUrl/en/webtoon/${manga.url}"), headers)

    private fun apiMangaDetailsRequest(manga: SManga): Request {
        return GET(getMangaUrl("$baseUrl/api/comics/${manga.url}"), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        return SManga.create().apply {
            description = jsonObject["description"].nullString
            status = jsonObject["status"].nullString.toStatus()
            thumbnail_url = jsonObject["image_url"].nullString
            genre = try { jsonObject["tags"].asJsonArray.joinToString { it["name"].string } } catch (_: Exception) { null }
            artist = try { jsonObject["artists"].asJsonArray.joinToString { it["name"].string } } catch (_: Exception) { null }
            author = try { jsonObject["authors"].asJsonArray.joinToString { it["name"].string } } catch (_: Exception) { null }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("complete", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga.url)
            }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(getMangaUrl("$baseUrl/api/comics/${manga.url}/chapters"), headers)
    }

    private fun chapterListParse(response: Response, titleSlug: String): List<SChapter> {
        return gson.fromJson<JsonArray>(response.body()!!.string()).map { json ->
            SChapter.create().apply {
                name = json["name"].string
                url = "$titleSlug/${json["slug"].string}"
                date_upload = json["added_at"].string.let { dateString ->
                    if (dateString.contains("ago")) {
                        val trimmedDate = dateString.substringBefore(" ago").removeSuffix("s").split(" ")
                        val calendar = Calendar.getInstance()
                        when (trimmedDate[1]) {
                            "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }.timeInMillis
                            "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }.timeInMillis
                            "minute" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }.timeInMillis
                            "second" -> calendar.apply { add(Calendar.SECOND, -trimmedDate[0].toInt()) }.timeInMillis
                            else -> 0L
                        }
                    } else {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)?.time ?: 0
                    }
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(getMangaUrl("$baseUrl/api/comics/${chapter.url}/images"), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["images"].asJsonArray.mapIndexed { i, json ->
            Page(i, "", json["source_url"].string)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        NSFWFilter().apply { state = isNSFWEnabledInPref() },
        GenreFilter(getGenreList()),
        StatusFilter(getStatusList()),
        DurationFilter(getDurationList()),
        OrderBy()
    )

    private class NSFWFilter : Filter.CheckBox("Show NSFW", true)
    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("GENRES", genres)
    private class Status(name: String, val id: String = name) : Filter.TriState(name)
    private class StatusFilter(status: List<Status>) : Filter.Group<Status>("STATUS", status)
    private class DurationFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("DURATION", pairs)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun getGenreList() = listOf(
        Genre("Action", "14"),
        Genre("Adventure", "6"),
        Genre("All Ages", "73"),
        Genre("Angst", "50"),
        Genre("BL", "20"),
        Genre("Boxing", "58"),
        Genre("College", "82"),
        Genre("Comedy", "1"),
        Genre("Comic", "70"),
        Genre("Completed", "53"),
        Genre("Cooking", "67"),
        Genre("Crime", "18"),
        Genre("Cultivation", "37"),
        Genre("Demons", "65"),
        Genre("Drama", "2"),
        Genre("Ecchi", "46"),
        Genre("Fantasy", "8"),
        Genre("Gender Bender", "35"),
        Genre("GL", "42"),
        Genre("Goshiwon", "80"),
        Genre("Gossip", "12"),
        Genre("Harem", "7"),
        Genre("Historical", "33"),
        Genre("Horror", "19"),
        Genre("Incest", "10"),
        Genre("Isekai", "28"),
        Genre("Josei", "48"),
        Genre("Long Strip", "78"),
        Genre("M", "43"),
        Genre("Magic", "59"),
        Genre("Magical", "69"),
        Genre("Magical Girls", "77"),
        Genre("Manga", "56"),
        Genre("Manhua", "38"),
        Genre("Manhwa", "40"),
        Genre("Manhwa18", "81"),
        Genre("Martial arts", "26"),
        Genre("Mature", "30"),
        Genre("Mecha", "54"),
        Genre("Medical", "24"),
        Genre("Moder", "64"),
        Genre("Modern", "51"),
        Genre("Monster/Tentacle", "57"),
        Genre("Music", "75"),
        Genre("Mystery", "15"),
        Genre("NTR", "32"),
        Genre("Office", "84"),
        Genre("Office Life", "79"),
        Genre("One shot", "61"),
        Genre("Philosophical", "44"),
        Genre("Post Apocalyptic", "49"),
        Genre("Psychological", "16"),
        Genre("Revenge", "74"),
        Genre("Reverse harem", "72"),
        Genre("Romance", "3"),
        Genre("Rpg", "41"),
        Genre("School LIfe", "11"),
        Genre("Sci Fi", "9"),
        Genre("Seinen", "31"),
        Genre("Shoujo", "36"),
        Genre("Shoujo Ai", "62"),
        Genre("Shounen", "29"),
        Genre("Shounen Ai", "63"),
        Genre("Slice of Life", "4"),
        Genre("Smut", "13"),
        Genre("Sports", "5"),
        Genre("Super power", "71"),
        Genre("Superhero", "45"),
        Genre("Supernatural", "22"),
        Genre("Suspense", "47"),
        Genre("Thriller", "17"),
        Genre("Time Travel", "55"),
        Genre("TimeTravel", "52"),
        Genre("ToonPoint", "83"),
        Genre("Tragedy", "23"),
        Genre("Uncensored", "85"),
        Genre("Vampire", "68"),
        Genre("Vanilla", "34"),
        Genre("Web Comic", "76"),
        Genre("Webtoon", "39"),
        Genre("Webtoons", "60"),
        Genre("Yaoi", "21"),
        Genre("Youkai", "66"),
        Genre("Yuri", "25")
    )

    private fun getStatusList() = listOf(
        Status("Ongoing", "ongoing"),
        Status("Complete", "complete"),
        Status("On Hold", "onhold"),
        Status("Canceled", "canceled")
    )

    private fun getDurationList() = arrayOf(
        Pair("All time", "all"),
        Pair("Year", "year"),
        Pair("Month", "month"),
        Pair("Week", "week"),
        Pair("Day", "day")
    )

    private class OrderBy : Filter.Sort(
        "Order by",
        arrayOf("Date", "Title", "Pages", "Favorites", "Popularity"),
        Selection(0, false)
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val nsfw = CheckBoxPreference(screen.context).apply {
            key = NSFW
            title = NSFW_TITLE
            setDefaultValue(NSFW_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as Boolean
                preferences.edit().putBoolean(NSFW, selected).commit()
            }
        }
        screen.addPreference(nsfw)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val nsfw = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = NSFW
            title = NSFW_TITLE
            setDefaultValue(NSFW_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as Boolean
                preferences.edit().putBoolean(NSFW, selected).commit()
            }
        }
        screen.addPreference(nsfw)
    }

    private fun isNSFWEnabledInPref(): Boolean {
        return preferences.getBoolean(NSFW, NSFW_DEFAULT)
    }

    companion object {
        private const val NSFW = "NSFW"
        private const val NSFW_TITLE = "Show NSFW"
        private const val NSFW_DEFAULT = true
    }
}
