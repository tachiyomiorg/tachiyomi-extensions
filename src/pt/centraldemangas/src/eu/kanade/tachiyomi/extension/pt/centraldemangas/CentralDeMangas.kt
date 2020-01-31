package eu.kanade.tachiyomi.extension.pt.centraldemangas

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CentralDeMangas : ParsedHttpSource() {

    override val name = "Central de Mangás"

    override val baseUrl = "http://centraldemangas.online"

    override val lang = "pt"

    override val supportsLatest = true

    // Sometimes the site is very slow.
    override val client: OkHttpClient =
        network.client.newBuilder()
            .connectTimeout(3, TimeUnit.MINUTES)
            .readTimeout(3, TimeUnit.MINUTES)
            .writeTimeout(3, TimeUnit.MINUTES)
            .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.ui.eight.doubling.stackable.cards div.card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.content a").last().text()
        url = element.select("div.content a").last().attr("href")
        thumbnail_url = element.select("div.ui.image a img").first()?.attr("src")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.ui.black.segment div.ui.divided.celled.list div.item"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.content div.header a.popar").last().text()
        url = element.select("div.content div.header a.popar").last().attr("href")
        thumbnail_url = element.select("div.ui.tiny.bordered.image a img").first()
            ?.attr("src")
            ?.replace("60x80", "150x200")
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response, query)
                }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headers.newBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return GET("$baseUrl/api/titulos", newHeaders)
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val result = response.asJsonArray()

        val resultFiltered = result
            .filter { it["title"].string.contains(query, true) }
            .map {
                SManga.create().apply {
                    title = it["title"].string
                    url = it["url"].string
                    thumbnail_url = getCoverUrl(it["url"].string.substringAfterLast("/"))
                }
            }

        return MangasPage(resultFiltered, false)
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val elementList = document.select("div.ui.black.segment div.ui.relaxed.list").first()

        author = elementList.select("div.item:eq(3) div.content div.description").text()
        artist = elementList.select("div.item:eq(2) div.content div.description").text()
        genre = elementList.select("div.item:eq(4) div.content div.description a")
                .joinToString { it.text() }

        status = elementList.select("div.item:eq(6) div.content div.description")
                .text().orEmpty().let { parseStatus(it) }

        description = elementList.select("div.item:eq(0) div.content div.description").text()
        thumbnail_url = elementList.select("div.item:eq(0) div.content div.description img")
            .attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Em publicação") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        // Filter only manga chapters.
        return super.chapterListParse(response)
            .filter { !it.url.contains("/novel/") }
    }

    override fun chapterListSelector() = "table.ui.small.compact.very.basic.table tbody tr:not(.active)"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val firstColumn = element.select("td:eq(0)")
        val secondColumn = element.select("td:eq(1)")

        url = firstColumn.select("a").first().attr("href")
        name = firstColumn.select("a").first().text()
        date_upload = secondColumn.select("small").first()?.text()
            ?.let { parseChapterDate(it) } ?: 0
    }

    private fun parseChapterDate(date: String) : Long {
        return try {
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script").last().data()
        val urlSuffix = script
            .substringAfter(SCRIPT_URL_BEGIN)
            .substringBefore(SCRIPT_URL_END)
        val pages = script
            .substringAfter(SCRIPT_PAGES_BEGIN)
            .substringBefore(SCRIPT_PAGES_END)
            .replace("'", "")
            .split(",")

        val chapterUrl = document.select("meta[property='og:url']").attr("content")

        return pages
            .mapIndexed { i, page -> Page(i, chapterUrl, "$urlSuffix$page.jpg") }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = Headers.Builder()
            .add("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(document: Document) = ""

    override fun searchMangaSelector() = throw Exception("This method should not be called!")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    private fun getCoverUrl(slug: String): String = "$COVER_CDN/150x200/$slug.jpg"

    private fun Response.asJsonArray(): JsonArray = JSON_PARSER.parse(body()!!.string()).array

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
        private const val COVER_CDN = "http://capas.centraldemangas.com.br"

        private const val SCRIPT_URL_BEGIN = "var urlSulfix = '"
        private const val SCRIPT_URL_END = "';"
        private const val SCRIPT_PAGES_BEGIN = "var pages = ["
        private const val SCRIPT_PAGES_END = ",];"

        val JSON_PARSER by lazy { JsonParser() }
    }
}
