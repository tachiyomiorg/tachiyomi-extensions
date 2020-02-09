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
import okhttp3.*
import org.jsoup.nodes.Element
import rx.Observable
import java.lang.Exception
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

abstract class MangasProject(override val name: String,
                             override val baseUrl: String) : HttpSource() {

    override val lang = "pt"

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
        .add("X-Requested-With", "XMLHttpRequest")

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/home/most_read?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asJsonObject()

        // If "most_read" have boolean false value, then it doesn't have next page.
        if (!result["most_read"]!!.isJsonArray)
            return MangasPage(emptyList(), false)

        val popularMangas = result["most_read"].array
            .map { popularMangaItemParse(it.obj) }

        val page = response.request().url().queryParameter("page")!!.toInt()

        return MangasPage(popularMangas, page < 10)
    }

    private fun popularMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["serie_name"].string
        thumbnail_url = obj["cover"].string
        url = obj["link"].string
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/home/releases?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (response.code() == 500)
            return MangasPage(emptyList(), false)

        val result = response.asJsonObject()

        val latestMangas = result["releases"].array
            .map { latestMangaItemParse(it.obj) }

        val page = response.request().url().queryParameter("page")!!.toInt()

        return MangasPage(latestMangas, page < 5)
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

        return POST("$baseUrl/lib/search/series.json", headers, form)
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

    override fun mangaDetailsRequest(manga: SManga): Request {
        val newHeaders = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Referer", baseUrl)
            .build()

        return GET(baseUrl + manga.url, newHeaders)
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
            .map { it.trim() }

        val seriesAuthor = seriesAuthors
            .filter { !it.contains("(Arte)") }
            .joinToString("; ") {
                it.split(", ")
                    .reversed()
                    .joinToString(" ")
            }

        return SManga.create().apply {
            thumbnail_url = seriesData.select("div.series-img > div.cover > img").attr("src")
            description = seriesData.select("span.series-desc").text()

            status = parseStatus(seriesBlocked, isCompleted)
            author = seriesAuthor
            artist = seriesAuthors.filter { it.contains("(Arte)") }
                .map { it.replace("\\(Arte\\)".toRegex(), "").trim() }
                .joinToString("; ") {
                    it.split(", ")
                        .reversed()
                        .joinToString(" ")
                }
                .ifEmpty { seriesAuthor }
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
        val newHeaders = headersBuilder()
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
                .map { chapterListItemParse(it.obj) }
                .toMutableList()

            val newRequest = chapterListRequestPaginated(mangaUrl, mangaId, ++page)
            result = client.newCall(newRequest).execute().asJsonObject()
        }

        return chapters
    }

    private fun chapterListItemParse(obj: JsonObject): SChapter {
        val scan = obj["releases"].obj.entrySet().first().value.obj
        val chapterName = obj["chapter_name"]!!.string

        return SChapter.create().apply {
            name = "Cap. ${obj["number"].string}" + (if (chapterName == "") "" else " - $chapterName")
            date_upload = parseChapterDate(obj["date_created"].string.substringBefore("T"))
            scanlator = scan["scanlators"]!!.array
                .joinToString { it.obj["name"].string }
            url = scan["link"].string
            chapter_number = obj["number"].string.toFloatOrNull() ?: 0f
        }
    }

    private fun parseChapterDate(date: String?) : Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Referer", baseUrl + chapter.url)
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    private fun pageListApiRequest(chapterUrl: String, token: String): Request {
        val newHeaders = headersBuilder()
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
        val readerSrc = document.select("script[src*=\"reader.\"]")
            ?.attr("src") ?: ""

        val token = TOKEN_REGEX.find(readerSrc)?.groupValues?.get(1) ?: ""

        if (token.isEmpty())
            throw Exception("Não foi possível obter o token de leitura.")

        return chain.proceed(pageListApiRequest(request.url().toString(), token))
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asJsonObject()

        return result["images"].array
            .filter { it.string.startsWith("http") }
            .mapIndexed { i, obj -> Page(i, "", obj.string)}
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.imageUrl!!)
    }

    override fun imageUrlParse(response: Response): String = ""

    private fun Response.asJsonObject(): JsonObject = JSON_PARSER.parse(body()!!.string()).obj

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.87 Safari/537.36"

        private val TOKEN_REGEX = "token=(.*)&id".toRegex()

        private val JSON_PARSER by lazy { JsonParser() }
    }
}
