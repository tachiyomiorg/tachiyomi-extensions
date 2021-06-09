package eu.kanade.tachiyomi.multisrc.mangasproject

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class MangasProject(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : HttpSource() {

    override val supportsLatest = true

    // Sometimes the site is slow.
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)
        .add("User-Agent", USER_AGENT)

    // Use internal headers to allow "Open in WebView" to work.
    private fun sourceHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", ACCEPT_JSON)
        .add("X-Requested-With", "XMLHttpRequest")

    protected val sourceHeaders: Headers by lazy { sourceHeadersBuilder().build() }

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/home/most_read?page=$page&type=", sourceHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<MangasProjectMostReadDto>(response.body!!.string())

        val popularMangas = result.mostRead.map(::popularMangaFromObject)

        val hasNextPage = response.request.url.queryParameter("page")!!.toInt() < 10

        return MangasPage(popularMangas, hasNextPage)
    }

    private fun popularMangaFromObject(serie: MangasProjectSerieDto) = SManga.create().apply {
        title = serie.serieName
        thumbnail_url = serie.cover
        url = serie.link
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/home/releases?page=$page&type=", sourceHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.decodeFromString<MangasProjectReleasesDto>(response.body!!.string())

        val latestMangas = result.releases.map(::latestMangaFromObject)

        val hasNextPage = response.request.url.queryParameter("page")!!.toInt() < 5

        return MangasPage(latestMangas, hasNextPage)
    }

    private fun latestMangaFromObject(serie: MangasProjectSerieDto) = SManga.create().apply {
        title = serie.name
        thumbnail_url = serie.image
        url = serie.link
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("search", query)
            .build()

        val newHeaders = sourceHeadersBuilder()
            .add("Content-Length", form.contentLength().toString())
            .add("Content-Type", form.contentType().toString())
            .build()

        return POST("$baseUrl/lib/search/series.json", newHeaders, form)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<MangasProjectSearchDto>(response.body!!.string())

        // If "series" have boolean false value, then it doesn't have results.
        if (result.series is JsonPrimitive)
            return MangasPage(emptyList(), false)

        val searchMangas = json.decodeFromJsonElement<List<MangasProjectSerieDto>>(result.series)
            .map(::searchMangaFromObject)

        return MangasPage(searchMangas, false)
    }

    private fun searchMangaFromObject(serie: MangasProjectSerieDto) = SManga.create().apply {
        title = serie.name
        thumbnail_url = serie.cover
        url = serie.link
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val seriesData = document.select("#series-data")

        val isCompleted = seriesData.select("span.series-author i.complete-series").first() != null

        // Check if the manga was removed by the publisher.
        val seriesBlocked = document.select("div.series-blocked-img").first()

        val seriesAuthors = document.select("div#series-data span.series-author").text()
            .substringAfter("Completo")
            .substringBefore("+")
            .split("&")
            .groupBy(
                { it.contains("(Arte)") },
                {
                    it.replace(" (Arte)", "")
                        .trim()
                        .split(", ")
                        .reversed()
                        .joinToString(" ")
                }
            )

        return SManga.create().apply {
            thumbnail_url = seriesData.select("div.series-img > div.cover > img").attr("src")
            description = seriesData.select("span.series-desc span").text()

            status = parseStatus(seriesBlocked, isCompleted)
            author = seriesAuthors[false]?.joinToString(", ") ?: author
            artist = seriesAuthors[true]?.joinToString(", ") ?: author
            genre = seriesData.select("div#series-data ul.tags li")
                .joinToString { it.text() }
        }
    }

    private fun parseStatus(seriesBlocked: Element?, isCompleted: Boolean) = when {
        seriesBlocked != null -> SManga.LICENSED
        isCompleted -> SManga.COMPLETED
        else -> SManga.ONGOING
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        if (manga.status != SManga.LICENSED)
            return super.fetchChapterList(manga)

        return Observable.error(Exception(MANGA_REMOVED))
    }

    private fun chapterListRequestPaginated(mangaUrl: String, id: String, page: Int): Request {
        val newHeaders = sourceHeadersBuilder()
            .set("Referer", baseUrl + mangaUrl)
            .build()

        return GET("$baseUrl/series/chapters_list.json?page=$page&id_serie=$id", newHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val licensedMessage = document.select("div.series-blocked-img").firstOrNull()

        if (licensedMessage != null) {
            // If the manga is licensed and has been removed from the source,
            // the extension will not fetch the chapters, even if they are returned
            // by the API. This is just to mimic the website behavior.
            throw Exception(MANGA_REMOVED)
        }

        val mangaUrl = response.request.url.toString().replace(baseUrl, "")
        val mangaId = mangaUrl.substringAfterLast("/")
        var page = 1

        var chapterListRequest = chapterListRequestPaginated(mangaUrl, mangaId, page)
        var result = client.newCall(chapterListRequest).execute().let {
            json.decodeFromString<MangasProjectChapterListDto>(it.body!!.string())
        }

        if (result.chapters is JsonPrimitive)
            return emptyList()

        val chapters = mutableListOf<SChapter>()

        while (result.chapters is JsonArray) {
            chapters += json.decodeFromJsonElement<List<MangasProjectChapterDto>>(result.chapters)
                .flatMap(::chaptersFromObject)
                .toMutableList()

            chapterListRequest = chapterListRequestPaginated(mangaUrl, mangaId, ++page)
            result = client.newCall(chapterListRequest).execute().let {
                json.decodeFromString(it.body!!.string())
            }
        }

        return chapters
    }

    private fun chaptersFromObject(chapter: MangasProjectChapterDto): List<SChapter> {
        return chapter.releases.values.map { release ->
            SChapter.create().apply {
                name = "Cap. ${chapter.number}" +
                    (if (chapter.name.isEmpty()) "" else " - ${chapter.name}")
                date_upload = chapter.dateCreated.substringBefore("T").toDate()
                scanlator = release.scanlators
                    .mapNotNull { scan -> scan.name.ifEmpty { null } }
                    .sorted()
                    .joinToString()
                url = release.link
                chapter_number = chapter.number.toFloatOrNull() ?: -1f
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .add("Accept", ACCEPT)
            .add("Accept-Language", ACCEPT_LANGUAGE)
            .set("Referer", "$baseUrl/home")
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    private fun pageListApiRequest(chapterUrl: String, token: String): Request {
        val newHeaders = sourceHeadersBuilder()
            .set("Referer", chapterUrl)
            .build()

        val id = chapterUrl
            .substringBeforeLast("/")
            .substringAfterLast("/")

        return GET("$baseUrl/leitor/pages/$id.json?key=$token", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val readerToken = getReaderToken(document) ?: throw Exception(TOKEN_NOT_FOUND)
        val chapterUrl = getChapterUrl(response)

        val apiRequest = pageListApiRequest(chapterUrl, readerToken)
        val apiResponse = client.newCall(apiRequest).execute().let {
            json.decodeFromString<MangasProjectReaderDto>(it.body!!.string())
        }

        return apiResponse.images
            .filter { it.startsWith("http") }
            .mapIndexed { i, imageUrl -> Page(i, chapterUrl, imageUrl) }
    }

    open fun getChapterUrl(response: Response): String {
        return response.request.url.toString()
    }

    protected open fun getReaderToken(document: Document): String? {
        return document.select("script[src*=\"reader.\"]").firstOrNull()
            ?.attr("abs:src")
            ?.toHttpUrlOrNull()
            ?.queryParameter("token")
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .removeAll("Referer")
            .set("Accept", ACCEPT_IMAGE)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_JSON = "application/json, text/javascript, */*; q=0.01"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Safari/537.36"

        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }

        private const val MANGA_REMOVED = "Mangá licenciado e removido pela fonte."
        private const val TOKEN_NOT_FOUND = "Não foi possível obter o token de leitura."
    }
}
