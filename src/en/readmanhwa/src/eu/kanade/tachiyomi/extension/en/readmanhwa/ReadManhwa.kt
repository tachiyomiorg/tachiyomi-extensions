package eu.kanade.tachiyomi.extension.en.readmanhwa

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class ReadManhwa : HttpSource() {

    override val name = "ReadManhwa"

    override val baseUrl = "https://www.readmanhwa.com"

    override val lang = "en"

    override val supportsLatest = true

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

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/comics?page=$page&q=&sort=popularity&order=desc&duration=week", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/comics?page=$page&q=&sort=uploaded_at&order=desc&duration=day", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/api/comics")!!.newBuilder()
            .addQueryParameter("per_page", "18")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", "desc")
            .addQueryParameter("q", query)

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                    is GenreFilter -> url.addQueryParameter("tags", filter.toUriPart())
                    is DurationFilter -> url.addQueryParameter("duration", filter.toUriPart())
                }
            }
        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Details

    // Workaround to allow "Open in browser" to use the real URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(apiMangaDetailsRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    // Return the real URL for "Open in browser"
    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/en/webtoon/${manga.url}", headers)

    private fun apiMangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/api/comics/${manga.url}", headers)
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
        return GET("$baseUrl/api/comics/${manga.url}/chapters", headers)
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
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString).time
                    }
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/api/comics/${chapter.url}/images", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["images"].asJsonArray.mapIndexed { i, json ->
            Page(i, "", json["source_url"].string)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList()),
        DurationFilter(getDurationList()),
        SortFilter(getSortList())
    )

    private class GenreFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("Genre", pairs)

    private class DurationFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("Duration", pairs)

    private class SortFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("Sorted by", pairs)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun getGenreList() = arrayOf(
        Pair("All", "0"),
        Pair("Action", "14"),
        Pair("Adult", "27"),
        Pair("Adventure", "6"),
        Pair("Angst", "50"),
        Pair("BL", "20"),
        Pair("Comedy", "1"),
        Pair("Completed", "53"),
        Pair("Crime", "18"),
        Pair("Cultivation", "37"),
        Pair("Drama", "2"),
        Pair("Ecchi", "46"),
        Pair("Fantasy", "8"),
        Pair("GL", "42"),
        Pair("Gender Bender", "35"),
        Pair("Gossip", "12"),
        Pair("Harem", "7"),
        Pair("Historical", "33"),
        Pair("Horror", "19"),
        Pair("Incest", "10"),
        Pair("Isekai", "28"),
        Pair("Josei", "48"),
        Pair("M", "43"),
        Pair("Manhua", "38"),
        Pair("Manhwa", "40"),
        Pair("Martial arts", "26"),
        Pair("Mature", "30"),
        Pair("Medical", "24"),
        Pair("Modern", "51"),
        Pair("Mystery", "15"),
        Pair("NTR", "32"),
        Pair("Philosophical", "44"),
        Pair("Post Apocalyptic", "49"),
        Pair("Psychological", "16"),
        Pair("Romance", "3"),
        Pair("Rpg", "41"),
        Pair("School LIfe", "11"),
        Pair("Sci Fi", "9"),
        Pair("Seinen", "31"),
        Pair("Shoujo", "36"),
        Pair("Shounen", "29"),
        Pair("Slice of Life", "4"),
        Pair("Smut", "13"),
        Pair("Sports", "5"),
        Pair("Superhero", "45"),
        Pair("Supernatural", "22"),
        Pair("Suspense", "47"),
        Pair("Thriller", "17"),
        Pair("TimeTravel", "52"),
        Pair("Tragedy", "23"),
        Pair("Vanilla", "34"),
        Pair("Webtoon", "39"),
        Pair("Yaoi", "21"),
        Pair("Yuri", "25")
    )

    private fun getDurationList() = arrayOf(
        Pair("All time", "all"),
        Pair("Year", "year"),
        Pair("Month", "month"),
        Pair("Week", "week"),
        Pair("Day", "day")
    )

    private fun getSortList() = arrayOf(
        Pair("Popularity", "popularity"),
        Pair("Date", "uploaded_at")
    )
}
