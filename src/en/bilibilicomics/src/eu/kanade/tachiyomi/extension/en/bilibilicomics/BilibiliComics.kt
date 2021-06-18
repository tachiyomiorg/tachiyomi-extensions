package eu.kanade.tachiyomi.extension.en.bilibilicomics

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.injectLazy
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

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT_JSON)
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        val requestPayload = buildJsonObject {
            put("id", FEATURED_ID)
            put("isAll", 0)
            put("page_num", 1)
            put("page_size", 6)
        }
        val requestBody = requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

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
        val result = json.decodeFromString<BilibiliResultDto<BilibiliFeaturedDto>>(response.body!!.string())

        if (result.code != 0) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val comicList = result.data!!.rollSixComics
            .map(::popularMangaFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    private fun popularMangaFromObject(comic: BilibiliComicDto): SManga = SManga.create().apply {
        title = comic.title
        thumbnail_url = comic.verticalCover
        url = "/detail/mc${comic.comicId}"
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val jsonPayload = buildJsonObject {
            put("area_id", -1)
            put("is_finish", -1)
            put("is_free", 1)
            put("key_word", query)
            put("order", 0)
            put("page_num", page)
            put("page_size", 9)
            put("style_id", -1)
        }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val refererUrl = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .toString()
        val newHeaders = headersBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .add("X-Page", page.toString())
            .set("Referer", refererUrl)
            .build()

        return POST(
            "$baseUrl/$BASE_API_ENDPOINT/Search?device=pc&platform=web",
            headers = newHeaders,
            body = requestBody
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<BilibiliResultDto<BilibiliSearchDto>>(response.body!!.string())

        if (result.code != 0) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val comicList = result.data!!.list
            .map(::searchMangaFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    private fun searchMangaFromObject(comic: BilibiliComicDto): SManga = SManga.create().apply {
        title = Jsoup.parse(comic.title).text()
        thumbnail_url = comic.verticalCover
        url = "/detail/mc${comic.id}"
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

        val jsonPayload = buildJsonObject { put("comic_id", comicId) }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

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
        val result = json.decodeFromString<BilibiliResultDto<BilibiliComicDto>>(response.body!!.string())
        val comic = result.data!!

        title = comic.title
        author = comic.authorName.joinToString()
        status = if (comic.isFinish == 1) SManga.COMPLETED else SManga.ONGOING
        genre = comic.styles.joinToString()
        description = comic.classicLines
        thumbnail_url = comic.verticalCover
    }

    // Chapters are available in the same url of the manga details.
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsApiRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<BilibiliResultDto<BilibiliComicDto>>(response.body!!.string())

        if (result.code != 0)
            return emptyList()

        return result.data!!.episodeList
            .filter { episode -> episode.isLocked.not() }
            .map { ep -> chapterFromObject(ep, result.data.id) }
    }

    private fun chapterFromObject(episode: BilibiliEpisodeDto, comicId: Int): SChapter = SChapter.create().apply {
        name = "Ep. " + episode.order.toString().removeSuffix(".0") +
            " - " + episode.title
        chapter_number = episode.order
        scanlator = this@BilibiliComics.name
        date_upload = episode.publicationTime.substringBefore("T").toDate()
        url = "/mc$comicId/${episode.id}"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/").toInt()

        val jsonPayload = buildJsonObject { put("ep_id", chapterId) }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

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
        val result = json.decodeFromString<BilibiliResultDto<BilibiliReader>>(response.body!!.string())

        if (result.code != 0) {
            return emptyList()
        }

        return result.data!!.images
            .mapIndexed { i, page -> Page(i, page.path, "") }
    }

    override fun imageUrlRequest(page: Page): Request {
        val jsonPayload = buildJsonObject {
            put("urls", buildJsonArray { add(page.url) }.toString())
        }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

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
        val result = json.decodeFromString<BilibiliResultDto<List<BilibiliPageDto>>>(response.body!!.string())
        val page = result.data!![0]

        return "${page.url}?token=${page.token}"
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

    companion object {
        private const val BASE_API_ENDPOINT = "twirp/comic.v1.Comic"

        private const val ACCEPT_JSON = "application/json, text/plain, */*"

        private val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()

        private const val FEATURED_ID = 3

        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    }
}
