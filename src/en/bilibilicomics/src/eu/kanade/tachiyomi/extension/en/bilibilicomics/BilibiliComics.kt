package eu.kanade.tachiyomi.extension.en.bilibilicomics

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.float
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class BilibiliComics : HttpSource() {

    override val name = "BILIBILI COMICS"

    override val baseUrl = "https://www.bilibilicomics.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    private val comicList: MutableList<SManga> = mutableListOf()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT_JSON)
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val requestPayload = jsonObject(
            "id" to FEATURED_ID,
            "isAll" to 0,
            "page_num" to 1,
            "page_size" to 6
        )
        val requestBody = requestPayload.toString().toRequestBody(JSON_CONTENT_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .build()

        return POST(
            "$baseUrl/$BASE_API_ENDPOINT/GetClassPageSixComics?device=pc&platform=web",
            headers = newHeaders,
            body = requestBody
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonResponse = response.asJson().obj

        if (jsonResponse["code"].int != 0) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val comicList = jsonResponse["data"]["roll_six_comics"].array
            .map(::popularMangaFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    private fun popularMangaFromObject(obj: JsonElement): SManga = SManga.create().apply {
        title = obj["title"].string
        thumbnail_url = obj["vertical_cover"].string
        url = "/detail/mc" + obj["comic_id"].int
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (comicList.isEmpty()) {
            super.fetchSearchManga(page, query, filters)
                .map { result ->
                    val filteredComics = result.mangas.filter { it.title.contains(query, true) }
                    MangasPage(filteredComics, result.hasNextPage)
                }
        } else {
            val filteredComics = comicList.filter { it.title.contains(query, true) }
            Observable.just(MangasPage(filteredComics, hasNextPage = false))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val jsonPayload = jsonObject(
            "area_id" to -1,
            "is_finish" to -1,
            "is_free" to 1,
            "order" to 0,
            "page_num" to page,
            "page_size" to 18,
            "style_id" to -1
        )
        val requestBody = jsonPayload.toString().toRequestBody(JSON_CONTENT_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .add("X-Page", page.toString())
            .set("Referer", "$baseUrl/genre")
            .build()

        return POST(
            "$baseUrl/$BASE_API_ENDPOINT/ClassPage?device=pc&platform=web",
            headers = newHeaders,
            body = requestBody
        )
    }

    // Site does not have search in the API, so we need to fetch all the pages
    // and then filter to find the query provided by the user.
    override fun searchMangaParse(response: Response): MangasPage {
        var request = response.request
        var currentPage = request.headers["X-Page"]!!.toInt()
        var jsonResponse = response.asJson().obj

        if (jsonResponse["code"].int != 0) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        while (jsonResponse["data"].array.size() > 0) {
            comicList += jsonResponse["data"].array
                .map(::searchMangaFromObject)

            request = searchMangaRequest(++currentPage, "", FilterList())
            jsonResponse = client.newCall(request).execute().asJson().obj
        }

        return MangasPage(comicList, hasNextPage = false)
    }

    private fun searchMangaFromObject(obj: JsonElement): SManga = SManga.create().apply {
        title = obj["title"].string
        thumbnail_url = obj["vertical_cover"].string
        url = "/detail/mc" + obj["season_id"].int
    }

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsApiRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun mangaDetailsApiRequest(manga: SManga): Request {
        val comicId = manga.url.substringAfterLast("/mc").toInt()

        val jsonPayload = jsonObject("comic_id" to comicId)
        val requestBody = jsonPayload.toString().toRequestBody(JSON_CONTENT_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .set("Referer", baseUrl + manga.url)
            .build()

        return POST(
            "$baseUrl/$BASE_API_ENDPOINT/ComicDetail?device=pc&platform=web",
            headers = newHeaders,
            body = requestBody
        )
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val jsonResponse = response.asJson().obj

        title = jsonResponse["data"]["title"].string
        author = jsonResponse["data"]["author_name"].array.joinToString { it.string }
        status = if (jsonResponse["data"]["is_finish"].int == 1) SManga.COMPLETED else SManga.ONGOING
        genre = jsonResponse["data"]["styles"].array.joinToString { it.string }
        description = jsonResponse["data"]["classic_lines"].string
        thumbnail_url = jsonResponse["data"]["vertical_cover"].string
    }

    // Chapters are available in the same url of the manga details.
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsApiRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonResponse = response.asJson().obj

        if (jsonResponse["code"].int != 0)
            return emptyList()

        return jsonResponse["data"]["ep_list"].array
            .filter { ep -> ep["is_locked"].bool.not() }
            .map { ep -> chapterFromObject(ep, jsonResponse["data"]["id"].int) }
    }

    private fun chapterFromObject(obj: JsonElement, comicId: Int): SChapter = SChapter.create().apply {
        name = "Ep. " + obj["ord"].float.toString().removeSuffix(".0") +
            " - " + obj["title"].string
        chapter_number = obj["ord"].float
        scanlator = this@BilibiliComics.name
        date_upload = obj["pub_time"].string.substringBefore("T").toDate()
        url = "/mc" + comicId + "/" + obj["id"].int
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val comicId = chapter.url.substringAfterLast("/").toInt()

        val jsonPayload = jsonObject("ep_id" to comicId)
        val requestBody = jsonPayload.toString().toRequestBody(JSON_CONTENT_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .set("Referer", baseUrl + chapter.url)
            .build()

        return POST(
            "$baseUrl/$BASE_API_ENDPOINT/GetImageIndex?device=pc&platform=web",
            headers = newHeaders,
            body = requestBody
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsonResponse = response.asJson().obj

        if (jsonResponse["code"].int != 0) {
            return emptyList()
        }

        return jsonResponse["data"]["images"].array
            .mapIndexed { i, page -> Page(i, page["path"].string, "") }
    }

    override fun imageUrlRequest(page: Page): Request {
        val jsonPayload = jsonObject("urls" to jsonArray(page.url).toString())
        val requestBody = jsonPayload.toString().toRequestBody(JSON_CONTENT_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .build()

        return POST(
            "$baseUrl/$BASE_API_ENDPOINT/ImageToken?device=pc&platform=web",
            headers = newHeaders,
            body = requestBody
        )
    }

    override fun imageUrlParse(response: Response): String {
        val jsonResponse = response.asJson().obj

        return jsonResponse["data"][0]["url"].string
            .plus("?token=" + jsonResponse["data"][0]["token"].string)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    private fun Response.asJson(): JsonElement = JsonParser.parseString(body!!.string())

    companion object {
        private const val BASE_API_ENDPOINT = "twirp/comic.v1.Comic"

        private const val ACCEPT_JSON = "application/json, text/plain, */*"

        private val JSON_CONTENT_TYPE = "application/json;charset=UTF-8".toMediaType()

        private const val FEATURED_ID = 3

        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    }
}
