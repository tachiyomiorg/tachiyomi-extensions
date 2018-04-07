package eu.kanade.tachiyomi.extension.en.mangarock

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.*
import org.json.JSONObject
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.xor

/**
 * Manga Rock source
 */

class MangaRock : HttpSource() {

    override val name = "Manga Rock"

    override val baseUrl = "https://api.mangarockhd.com/query/web400"

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

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/mrs_latest")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val res = response.body()!!.string()
        val list = getMangaListFromJson(res)
        return getMangasPageFromJsonList(list)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mrs_latest")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.body()!!.string()
        val list = getMangaListFromJson(res)
        return getMangasPageFromJsonList(sortByRank(list))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val jsonType = MediaType.parse("application/jsonType; charset=utf-8")
        val body = RequestBody.create(jsonType, "{\"type\":\"series\", \"keywords\":\"$query\"}")
        return POST("$baseUrl/mrs_search", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val obj = JSONObject(response.body()!!.string()).getJSONArray("data")

        val jsonType = MediaType.parse("application/jsonType; charset=utf-8")
        val body = RequestBody.create(jsonType, obj.toString())
        val metaRes = client.newCall(POST("https://api.mangarockhd.com/meta", headers, body)).execute().body()!!.string()

        val res = JSONObject(metaRes).getJSONObject("data")
        val mangas = ArrayList<SManga>(res.length())
        for (key in res.keys()) {
            mangas.add(parseMangaJson(res.getJSONObject(key)))
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
            title = obj.getString("name")
            thumbnail_url = obj.getString("thumbnail")
            status = if (obj.getBoolean("completed")) SManga.COMPLETED else SManga.ONGOING
            url = "/info?oid=${obj.getString("oid")}"
        }
    }

    private fun sortByRank(arr: List<JSONObject>): List<JSONObject> {
        return arr.sortedBy({ it.getInt("rank") })
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val obj = JSONObject(response.body()!!.string()).getJSONObject("data")

        url = "https://mangarock.com/manga/${obj.getString("oid")}"
        title = obj.getString("name")
        description = obj.getString("description")

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
        for (i in 0 until arr.length()) {
            val chapter = arr.getJSONObject(i)
            chapters.add(SChapter.create().apply {
                name = chapter.getString("name")
                date_upload = chapter.getString("updatedAt").toLong() * 1000
                url = "/pages?oid=${chapter.getString("oid")}"
            })
        }
        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val obj = JSONObject(response.body()!!.string()).getJSONArray("data")
        val pages = ArrayList<Page>()
        for (i in 0 until obj.length()) {
            pages.add(Page(i, "", obj.getString(i)))
        }
        return pages
    }

    override fun imageUrlParse(response: Response)
            = throw UnsupportedOperationException("This method should not be called!")

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

    // "{\"status\":\"all\",\"genres\":{},\"rank\":\"all\",\"order\":\"rank\"}"
    private class Status : Filter.Select<String>("Completed", arrayOf("all", "completed", "ongoing"))
    private class Rank : Filter.Select<String>("Rank", arrayOf("All", "1-999", "1000-2000"))
    private class OrderBy : Filter.Select<String>("Order", arrayOf("name", "rank"))

    private class Genre(name: String, id: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    // TODO: can only filter by status/genres/rank/ordering if you get the full list of manga
    override fun getFilterList() = FilterList(
//            Status(),
//            Rank(),
//            OrderBy(),
//            GenreList(getGenreList())
    )

    // [... new Set($$('a[href^="/genre"]').filter(a => a.innerText !== '').map(a => `Genre("${a.innerText}", "${a.href.replace('https://mangarock.com/genre/', '')}")`))].sort().join(',\n')
    // on https://mangarock.com/
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

}
