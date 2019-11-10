package eu.kanade.tachiyomi.extension.en.mangarock

import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.set
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONObject
import rx.Observable
import java.util.ArrayList
import kotlin.experimental.and
import kotlin.experimental.xor

/**
 * Manga Rock source
 */

class MangaRock : HttpSource() {

    override val name = "Manga Rock"

    override val baseUrl = "https://mangarock.com"

    private val apiUrl = "https://api.mangarockhd.com/query/web401"

    override val lang = "en"

    override val supportsLatest = true

    // Handles the page decoding
    override val client: OkHttpClient = super.client.newBuilder().addInterceptor(fun(chain): Response {
        val url = chain.request().url().toString()
        val response = chain.proceed(chain.request())
        if (!url.endsWith(".mri")) return response

        val decoded: ByteArray = decodeMri(response)
        val mediaType = MediaType.parse("image/webp")
        val rb = ResponseBody.create(mediaType, decoded)
        return response.newBuilder().body(rb).build()
    }).build()

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/mrs_latest")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val res = response.body()!!.string()
        val list = getMangaListFromJson(res)
        return getMangasPageFromJsonList(list)
    }

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/mrs_latest")

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.body()!!.string()
        val list = getMangaListFromJson(res)
        return getMangasPageFromJsonList(sortByRank(list))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val jsonType = MediaType.parse("application/jsonType; charset=utf-8")

        // Filter
        if (query.isBlank()) {
            var status = ""
            var rank = ""
            var orderBy = ""
            val genres = jsonObject()
            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        status = when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "completed"
                            Filter.TriState.STATE_EXCLUDE -> "ongoing"
                            else -> "all"
                        }
                    }
                    is RankFilter -> {
                        rank = filter.toUriPart()
                    }
                    is SortBy -> {
                        orderBy = filter.toUriPart()
                    }
                    is GenreList -> {
                        filter.state
                                .filter { it.state != Filter.TriState.STATE_IGNORE }
                                .forEach { genres[it.id] = it.state == Filter.TriState.STATE_INCLUDE }
                    }
                }
            }

            val body = RequestBody.create(jsonType, jsonObject(
                    "status" to status,
                    "genres" to genres,
                    "rank" to rank,
                    "order" to orderBy
            ).toString())
            return POST("$apiUrl/mrs_filter", headers, body)
        }

        // Regular search
        val body = RequestBody.create(jsonType, jsonObject(
                "type" to "series",
                "keywords" to query
        ).toString())
        return POST("$apiUrl/mrs_search", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val idArray = JSONObject(response.body()!!.string()).getJSONArray("data")

        val jsonType = MediaType.parse("application/jsonType; charset=utf-8")
        val body = RequestBody.create(jsonType, idArray.toString())
        val metaRes = client.newCall(POST("https://api.mangarockhd.com/meta", headers, body)).execute().body()!!.string()

        val res = JSONObject(metaRes).getJSONObject("data")
        val mangas = ArrayList<SManga>(res.length())
        for (i in 0 until idArray.length()) {
            val id = idArray.get(i).toString()
            mangas.add(parseMangaJson(res.getJSONObject(id)))
        }
        return MangasPage(mangas, false)
    }

    private fun getMangaListFromJson(json: String): List<JSONObject> {
        val arr = JSONObject(json).getJSONArray("data")
        val mangaJson = ArrayList<JSONObject>(arr.length())
        for (i in 0 until arr.length()) {
            mangaJson.add(arr.getJSONObject(i))
        }
        return mangaJson
    }

    private fun getMangasPageFromJsonList(arr: List<JSONObject>): MangasPage {
        val mangas = ArrayList<SManga>(arr.size)
        for (obj in arr) {
            mangas.add(parseMangaJson(obj))
        }
        return MangasPage(mangas, false)
    }

    private fun parseMangaJson(obj: JSONObject): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain("/manga/${obj.getString("oid")}")
            title = obj.getString("name")
            thumbnail_url = obj.getString("thumbnail")
            status = if (obj.getBoolean("completed")) SManga.COMPLETED else SManga.ONGOING
        }
    }
    private fun sortByRank(arr: List<JSONObject>): List<JSONObject> {
        return arr.sortedBy { it.getInt("rank") }
    }

    // Avoid directly overriding mangaDetailsRequest so that "Open in browser" action uses the
    // "real" URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(getMangaApiRequest(manga))
                .asObservableSuccess()
                .map { response ->
                    mangaDetailsParse(response).apply { initialized = true }
                }
    }

    // Always returns the "real" URL for the "Open in browser" action
    override fun mangaDetailsRequest(manga: SManga): Request {
        // Handle older entries with API URL ("/info?oid=mrs-series-...")
        if (manga.url.startsWith("/info")) {
            val oid = manga.url.substringAfterLast("=")
            return GET("$baseUrl/manga/$oid", headers)
        }

        return super.mangaDetailsRequest(manga)
    }

    override fun chapterListRequest(manga: SManga) = getMangaApiRequest(manga)

    private fun getMangaApiRequest(manga: SManga): Request {
        // Handle older entries with API URL ("/info?oid=mrs-series-...")
        if (manga.url.startsWith("/info")) {
            return GET("$apiUrl${manga.url}&Country=", headers)
        }

        val oid = manga.url.substringAfterLast("/")
        return GET("$apiUrl/info?oid=$oid&Country=", headers)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val obj = JSONObject(response.body()!!.string()).getJSONObject("data")

        title = obj.getString("name")
        description = obj.getString("description")

        if (obj.isNull("authors")) {
            artist = ""
            author = ""
        } else {
            val people = obj.getJSONArray("authors")
            val authors = ArrayList<String>()
            val artists = ArrayList<String>()
            for (i in 0 until people.length()) {
                val person = people.getJSONObject(i)
                when (person.getString("role")) {
                    "art" -> artists.add(person.getString("name"))
                    "story" -> authors.add(person.getString("name"))
                }
            }
            artist = artists.sorted().joinToString(", ")
            author = authors.sorted().joinToString(", ")
        }

        val categories = obj.getJSONArray("rich_categories")
        val genres = ArrayList<String>(categories.length())
        for (i in 0 until categories.length()) {
            genres.add(categories.getJSONObject(i).getString("name"))
        }
        genre = genres.sorted().joinToString(", ")

        status = if (obj.getBoolean("completed")) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = obj.getString("thumbnail")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val obj = JSONObject(response.body()!!.string()).getJSONObject("data")
        val chapters = ArrayList<SChapter>()
        val arr = obj.getJSONArray("chapters")
        // Iterate backwards to match website's sorting
        for (i in arr.length() - 1 downTo 0) {
            val chapter = arr.getJSONObject(i)
            chapters.add(SChapter.create().apply {
                name = chapter.getString("name")
                date_upload = chapter.getString("updatedAt").toLong() * 1000
                url = "/pagesv2?oid=${chapter.getString("oid")}"
            })
        }
        return chapters
    }

    override fun pageListRequest(chapter: SChapter) = GET(apiUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val obj = JSONObject(response.body()!!.string()).getJSONArray("data")
        val pages = ArrayList<Page>()
        for (i in 0 until obj.length()) {
            pages.add(Page(i, "", obj.getJSONObject(i).getString("url")))
        }
        return pages
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("This method should not be called!")

    // See drawWebpToCanvas function in the site's client.js file
    // Extracted code: https://jsfiddle.net/6h2sLcs4/30/
    private fun decodeMri(response: Response): ByteArray {
        val data = response.body()!!.bytes()

        // Decode file if it starts with "E" (space when XOR-ed later)
        if (data[0] != 69.toByte()) return data

        // Reconstruct WEBP header
        // Doc: https://developers.google.com/speed/webp/docs/riff_container#webp_file_header
        val buffer = ByteArray(data.size + 15)
        val size = data.size + 7
        buffer[0] = 82  // R
        buffer[1] = 73  // I
        buffer[2] = 70  // F
        buffer[3] = 70  // F
        buffer[4] = (255.toByte() and size.toByte())
        buffer[5] = (size ushr 8).toByte() and 255.toByte()
        buffer[6] = (size ushr 16).toByte() and 255.toByte()
        buffer[7] = (size ushr 24).toByte() and 255.toByte()
        buffer[8] = 87  // W
        buffer[9] = 69  // E
        buffer[10] = 66 // B
        buffer[11] = 80 // P
        buffer[12] = 86 // V
        buffer[13] = 80 // P
        buffer[14] = 56 // 8

        // Decrypt file content using XOR cipher with 101 as the key
        val cipherKey = 101.toByte()
        for (r in 0 until data.size) {
            buffer[r + 15] = cipherKey xor data[r]
        }

        return buffer
    }

    private class StatusFilter : Filter.TriState("Completed")

    private class RankFilter : UriPartFilter("Rank", arrayOf(
            Pair("All", "all"),
            Pair("1 - 999", "1-999"),
            Pair("1k - 2k", "1000-2000"),
            Pair("2k - 3k", "2000-3000"),
            Pair("3k - 4k", "3000-4000"),
            Pair("4k - 5k", "4000-5000"),
            Pair("5k - 6k", "5000-6000"),
            Pair("6k - 7k", "6000-7000"),
            Pair("7k - 8k", "7000-8000"),
            Pair("8k - 9k", "8000-9000"),
            Pair("9k - 19k", "9000-10000"),
            Pair("10k - 11k", "10000-11000")
    ))

    private class SortBy : UriPartFilter("Sort by", arrayOf(
            Pair("Name", "name"),
            Pair("Rank", "rank")
    ))

    private class Genre(name: String, val id: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
            // Search and filter don't work at the same time
            Filter.Header("NOTE: Ignored if using text search!"),
            Filter.Separator(),
            StatusFilter(),
            RankFilter(),
            SortBy(),
            GenreList(getGenreList())
    )

    // [...document.querySelectorAll('._2DMqI .mdl-checkbox')].map(n => `Genre("${n.querySelector('.mdl-checkbox__label').innerText}", "${n.querySelector('input').dataset.oid}")`).sort().join(',\n')
    // on https://mangarock.com/manga
    private fun getGenreList() = listOf(
            Genre("4-koma", "mrs-genre-100117675"),
            Genre("Action", "mrs-genre-304068"),
            Genre("Adult", "mrs-genre-358370"),
            Genre("Adventure", "mrs-genre-304087"),
            Genre("Comedy", "mrs-genre-304069"),
            Genre("Demons", "mrs-genre-304088"),
            Genre("Doujinshi", "mrs-genre-304197"),
            Genre("Drama", "mrs-genre-304177"),
            Genre("Ecchi", "mrs-genre-304074"),
            Genre("Fantasy", "mrs-genre-304089"),
            Genre("Gender Bender", "mrs-genre-304358"),
            Genre("Harem", "mrs-genre-304075"),
            Genre("Historical", "mrs-genre-304306"),
            Genre("Horror", "mrs-genre-304259"),
            Genre("Isekai", "mrs-genre-100291868"),
            Genre("Josei", "mrs-genre-304070"),
            Genre("Kids", "mrs-genre-304846"),
            Genre("Magic", "mrs-genre-304090"),
            Genre("Martial Arts", "mrs-genre-304072"),
            Genre("Mature", "mrs-genre-358371"),
            Genre("Mecha", "mrs-genre-304245"),
            Genre("Military", "mrs-genre-304091"),
            Genre("Music", "mrs-genre-304589"),
            Genre("Mystery", "mrs-genre-304178"),
            Genre("One Shot", "mrs-genre-100018505"),
            Genre("Parody", "mrs-genre-304786"),
            Genre("Police", "mrs-genre-304236"),
            Genre("Psychological", "mrs-genre-304176"),
            Genre("Romance", "mrs-genre-304073"),
            Genre("School Life", "mrs-genre-304076"),
            Genre("Sci-Fi", "mrs-genre-304180"),
            Genre("Seinen", "mrs-genre-304077"),
            Genre("Shoujo Ai", "mrs-genre-304695"),
            Genre("Shoujo", "mrs-genre-304175"),
            Genre("Shounen Ai", "mrs-genre-304307"),
            Genre("Shounen", "mrs-genre-304164"),
            Genre("Slice of Life", "mrs-genre-304195"),
            Genre("Smut", "mrs-genre-358372"),
            Genre("Space", "mrs-genre-305814"),
            Genre("Sports", "mrs-genre-304367"),
            Genre("Super Power", "mrs-genre-305270"),
            Genre("Supernatural", "mrs-genre-304067"),
            Genre("Tragedy", "mrs-genre-358379"),
            Genre("Vampire", "mrs-genre-304765"),
            Genre("Webtoons", "mrs-genre-358150"),
            Genre("Yaoi", "mrs-genre-304202"),
            Genre("Yuri", "mrs-genre-304690")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

}
