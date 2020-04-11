package eu.kanade.tachiyomi.extension.pt.mangasproject

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.lang.Exception
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class MangasProject(
    override val name: String,
    override val baseUrl: String
) : HttpSource() {

    override val lang = "pt-BR"

    override val supportsLatest = true

    // Sometimes the site is slow.
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .addInterceptor { pageListIntercept(it) }
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)

    // Use internal headers to allow "Open in WebView" to work.
    private fun sourceHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("X-Requested-With", "XMLHttpRequest")

    private val sourceHeaders: Headers by lazy { sourceHeadersBuilder().build() }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/home/most_read?page=$page", sourceHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asJsonObject()

        val popularMangas = result["most_read"].array
            .map { popularMangaItemParse(it.obj) }

        val hasNextPage = response.request().url().queryParameter("page")!!.toInt() < 10

        return MangasPage(popularMangas, hasNextPage)
    }

    private fun popularMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["serie_name"].string
        thumbnail_url = obj["cover"].string
        url = obj["link"].string
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/home/releases?page=$page", sourceHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.asJsonObject()

        val latestMangas = result["releases"].array
            .map { latestMangaItemParse(it.obj) }

        val hasNextPage = response.request().url().queryParameter("page")!!.toInt() < 5

        return MangasPage(latestMangas, hasNextPage)
    }

    private fun latestMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["name"].string
        thumbnail_url = obj["image"].string
        url = obj["link"].string
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("search", query)
            .build()

        return POST("$baseUrl/lib/search/series.json", sourceHeaders, form)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asJsonObject()

        // If "series" have boolean false value, then it doesn't have results.
        if (!result["series"]!!.isJsonArray)
            return MangasPage(emptyList(), false)

        val searchMangas = result["series"].array
            .map { searchMangaItemParse(it.obj) }

        return MangasPage(searchMangas, false)
    }

    private fun searchMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["name"].string
        thumbnail_url = obj["cover"].string
        url = obj["link"].string
        author = obj["author"].string
        artist = obj["artist"].string
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
            .groupBy({ it.contains("(Arte)") }, {
                it.replace(" (Arte)", "")
                    .trim()
                    .split(", ")
                    .reversed()
                    .joinToString(" ")
            })

        return SManga.create().apply {
            thumbnail_url = seriesData.select("div.series-img > div.cover > img").attr("src")
            description = seriesData.select("span.series-desc").text()

            status = parseStatus(seriesBlocked, isCompleted)
            author = seriesAuthors[false]?.joinToString("; ") ?: author
            artist = seriesAuthors[true]?.joinToString("; ") ?: author
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

        return Observable.error(Exception("Mangá licenciado e removido pela editora."))
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")

        return chapterListRequestPaginated(manga.url, id, 1)
    }

    private fun chapterListRequestPaginated(mangaUrl: String, id: String, page: Int): Request {
        val newHeaders = sourceHeadersBuilder()
            .set("Referer", baseUrl + mangaUrl)
            .build()

        return GET("$baseUrl/series/chapters_list.json?page=$page&id_serie=$id", newHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var result = response.asJsonObject()

        if (!result["chapters"]!!.isJsonArray)
            return emptyList()

        val mangaUrl = response.request().header("Referer")!!
        val mangaId = mangaUrl.substringAfterLast("/")
        var page = 1

        val chapters = mutableListOf<SChapter>()

        while (result["chapters"]!!.isJsonArray) {
            chapters += result["chapters"].array
                .flatMap { chapterListItemParse(it.obj) }
                .toMutableList()

            val newRequest = chapterListRequestPaginated(mangaUrl, mangaId, ++page)
            result = client.newCall(newRequest).execute().asJsonObject()
        }

        return chapters
    }

    private fun chapterListItemParse(obj: JsonObject): List<SChapter> {
        val chapterName = obj["chapter_name"]!!.string

        return obj["releases"].obj.entrySet().map {
            val release = it.value.obj

            SChapter.create().apply {
                name = "Cap. ${obj["number"].string}" + (if (chapterName == "") "" else " - $chapterName")
                date_upload = parseChapterDate(obj["date_created"].string.substringBefore("T"))
                scanlator = release["scanlators"]!!.array
                    .map { scanObj -> scanObj.obj["name"].string }
                    .sorted()
                    .joinToString()
                url = release["link"].string
                chapter_number = obj["number"].string.toFloatOrNull() ?: 0f
            }
        }
    }

    private fun parseChapterDate(date: String?) : Long {
        return try {
            DATE_FORMATTER.parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + chapter.url)
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

    private fun pageListIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val result = chain.proceed(request)

        if (!request.url().toString().contains("capitulo-"))
            return result

        val document = result.asJsoup()
        val token = document.select("script[src*=\"reader.\"]").first()
            ?.let {
                HttpUrl.parse(it.attr("abs:src"))!!
                    .queryParameter("token")
            } ?: throw Exception("Não foi possível obter o token de leitura.")

        return chain.proceed(pageListApiRequest(request.url().toString(), token))
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asJsonObject()
        val chapterUrl = response.request().header("Referer")!!

        return result["images"].array
            .filter { it.string.startsWith("http") }
            .mapIndexed { i, obj -> Page(i, chapterUrl, obj.string)}
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun Response.asJsonObject(): JsonObject = JSON_PARSER.parse(body()!!.string()).obj

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.87 Safari/537.36"

        private val JSON_PARSER by lazy { JsonParser() }

        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    }
}
