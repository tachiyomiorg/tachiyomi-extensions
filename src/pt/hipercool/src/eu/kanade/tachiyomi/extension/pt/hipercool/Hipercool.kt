package eu.kanade.tachiyomi.extension.pt.hipercool

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.*
import rx.Observable
import java.lang.Exception
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class Hipercool : HttpSource() {
    override val name = "HipercooL"

    override val baseUrl = "https://hiper.cool"

    override val lang = "pt"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Referer", baseUrl)
            .add("X-Requested-With", "XMLHttpRequest")

    private fun generalListMangaParse(obj: JsonObject): SManga {
        val book = obj["_book"].obj
        val bookSlug = book["slug"].string
        val bookRevision = book["revision"]?.int ?: 1

        return SManga.create().apply {
            title = book["title"].string
            thumbnail_url = getThumbnailUrl(bookSlug, bookRevision)
            setUrlWithoutDomain("$baseUrl/books/$bookSlug")
        }
    }

    // The source does not have popular mangas, so use latest instead.
    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/books/chapters?start=${(page - 1) * 40}&count=40", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.asJsonArray()

        if (result.size() == 0)
            return MangasPage(emptyList(), false)

        val latestMangas = result
                .map { latestMangaItemParse(it.obj) }
                .distinctBy { it.title }

        return MangasPage(latestMangas, result.size() == 40)
    }

    private fun latestMangaItemParse(obj: JsonObject): SManga = generalListMangaParse(obj)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val mediaType = MediaType.parse("application/json; charset=utf-8")

        // Create json body.
        val json = jsonObject(
            "start" to (page - 1) * 40,
            "count" to 40,
            "text" to query,
            "type" to "text"
        )

        val body = RequestBody.create(mediaType, json.toString())

        return POST("$baseUrl/api/books/chapters/search", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asJsonArray()

        if (result.size() == 0)
            return MangasPage(emptyList(), false)

        val searchMangas = result
                .map { searchMangaItemParse(it.obj) }
                .distinctBy { it.title }

        return MangasPage(searchMangas, result.size() == 40)
    }

    private fun searchMangaItemParse(obj: JsonObject): SManga = generalListMangaParse(obj)

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsApiRequest(manga))
                .asObservableSuccess()
                .map { response ->
                    mangaDetailsParse(response).apply { initialized = true }
                }
    }

    private fun mangaDetailsApiRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")

        return GET("$baseUrl/api/books/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asJsonObject()

        val artists = result["tags"].array
                .filter { it["label"].string == "Artista" }
                .flatMap { it["values"].array }
                .joinToString("; ") { it["label"].string }

        val authors = result["tags"].array
                .filter { it["label"].string == "Autor" }
                .flatMap { it["values"].array }
                .joinToString("; ") { it["label"].string }

        val tags = result["tags"].array
                .filter { it["label"].string == "Tags" }
                .flatMap { it["values"].array }
                .joinToString(", ") { it["label"].string }

        return SManga.create().apply {
            title = result["title"].string
            thumbnail_url = getThumbnailUrl(result["slug"].string, result["revision"].int)
            description = result["synopsis"]?.string ?: ""
            artist = artists
            author = authors
            genre = tags
        }
    }

    // Chapters are available in the same url of the manga details.
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsApiRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.asJsonObject()

        if (!result["chapters"]!!.isJsonArray)
            return emptyList()

        return result["chapters"].array
                .map { chapterListItemParse(result, it.obj) }
                .reversed()
    }

    private fun chapterListItemParse(book: JsonObject, obj: JsonObject): SChapter = SChapter.create().apply {
        name = obj["title"].string
        chapter_number = obj["title"].string.toFloatOrNull() ?: 0f
        // The property is written wrong.
        date_upload = parseChapterDate(obj["publishied_at"].string)

        val bookSlug = book["slug"].string
        val chapterSlug = obj["slug"].string
        val images = obj["images"].int
        val revision = book["revision"].int
        setUrlWithoutDomain("$baseUrl/books/$bookSlug/$chapterSlug?images=$images&revision=$revision")
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val regex = CHAPTER_REGEX.toRegex()
        val results = regex.find(chapter.url)!!.groupValues

        val bookSlug = results[1].toString()
        val chapterSlug = results[2].toString()
        val images = results[3].toInt()
        val revision = results[4].toInt()
        val pages = arrayListOf<Page>()

        // Create the pages.
        for (i in 1..images) {
            val url = getPageUrl(bookSlug, chapterSlug, i, revision)
            pages += Page(i - 1, chapter.url, url)
        }

        return Observable.just(pages)
    }

    override fun pageListParse(response: Response): List<Page> = throw Exception("This method should not be called!")

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.imageUrl!!)
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = Headers.Builder()
                .apply {
                    add("Referer", page.url)
                    add("User-Agent", USER_AGENT)
                }
                .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun parseChapterDate(date: String) : Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    .parse(date.substringBefore("T"))
                    .time
        } catch (e: ParseException) {
            0L
        }
    }

    private fun getThumbnailUrl(bookSlug: String, revision: Int): String
        = "$STATIC_URL/books/$bookSlug/$bookSlug-cover.jpg?revision=$revision"
        
    private fun getPageUrl(bookSlug: String, chapterSlug: String, page: Int, revision: Int): String
        = "$STATIC_URL/books/$bookSlug/$chapterSlug/$bookSlug-chapter-$chapterSlug-page-$page.jpg?revision=$revision"

    private fun Response.asJsonObject(): JsonObject = JSON_PARSER.parse(body()!!.string()).obj

    private fun Response.asJsonArray(): JsonArray = JSON_PARSER.parse(body()!!.string()).array

    companion object {
        private const val STATIC_URL = "https://static.hiper.cool"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"

        private const val CHAPTER_REGEX = "\\/books\\/(.*)\\/(.*)\\?images=(\\d+)&revision=(\\d+)\$"

        private val JSON_PARSER by lazy { JsonParser() }
    }
}
