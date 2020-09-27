package eu.kanade.tachiyomi.extension.ja.shonenjumpplus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor

class ShonenJumpPlus : ParsedHttpSource() {

    override val name = "Shonen Jump+"

    override val baseUrl = "https://shonenjumpplus.com"

    override val lang = "ja"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { imageIntercept(it) }
        .build()

    private val dayOfWeek: String by lazy {
        Calendar.getInstance()
            .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US)!!
            .toLowerCase(Locale.US)
    }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series", headers)

    override fun popularMangaSelector(): String = "ul.series-list li a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h2.series-list-title").text()
        thumbnail_url = element.select("div.series-list-thumb img")
            .attr("data-src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector(): String = "h2.series-list-date-week.$dayOfWeek + ul.series-list li a"

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
                .addQueryParameter("q", query)

            return GET(url.toString(), headers)
        }

        val listMode = (filters[0] as SeriesListModeFilter).state
        return GET("$baseUrl/series/${LIST_MODES[listMode].second}", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request().url().toString().contains("search"))
            return super.searchMangaParse(response)

        return popularMangaParse(response)
    }

    override fun searchMangaSelector() = "ul.search-series-list li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.title-box p.series-title").text()
        thumbnail_url = element.select("div.thmb-container a img").attr("src")
        setUrlWithoutDomain(element.select("div.thmb-container a").attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("section.series-information div.series-header")

        title = infoElement.select("h1.series-header-title").text()
        author = infoElement.select("h2.series-header-author").text()
        artist = author
        description = infoElement.select("p.series-header-description").text()
        thumbnail_url = infoElement.select("div.series-header-image-wrapper img")
            .attr("data-src")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val readableProductList = document.select("div.js-readable-product-list").first()!!
        val latestListEndpoint = HttpUrl.parse(readableProductList.attr("data-latest-list-endpoint"))!!
        val firstListEndpoint = HttpUrl.parse(readableProductList.attr("data-first-list-endpoint"))!!
        val numberSince = latestListEndpoint.queryParameter("number_since")!!.toInt()
            .coerceAtLeast(firstListEndpoint.queryParameter("number_since")!!.toInt())

        val newHeaders = headers.newBuilder()
            .set("Referer", response.request().url().toString())
            .build()
        var readMoreEndpoint = firstListEndpoint.newBuilder()
            .setQueryParameter("number_since", numberSince.toString())
            .toString()

        val chapters = mutableListOf<SChapter>()

        var request = GET(readMoreEndpoint, newHeaders)
        var result = client.newCall(request).execute()

        while (result.code() != 404) {
            val json = result.asJsonObject()
            readMoreEndpoint = json["nextUrl"].string
            val tempDocument = Jsoup.parse(json["html"].string, response.request().url().toString())

            chapters += tempDocument
                .select("ul.series-episode-list " + chapterListSelector())
                .map { element -> chapterFromElement(element) }

            request = GET(readMoreEndpoint, newHeaders)
            result = client.newCall(request).execute()
        }

        return chapters
    }

    override fun chapterListSelector() = "li.episode:has(span.series-episode-list-is-free)"

    override fun chapterFromElement(element: Element): SChapter {
        val info = element.select("a.series-episode-list-container").first() ?: element
        val mangaUrl = element.ownerDocument().location()

        return SChapter.create().apply {
            name = info.select("h4.series-episode-list-title").text()
            date_upload = info.select("span.series-episode-list-date").first()
                ?.text().orEmpty()
                .tryParseDate()
            scanlator = "集英社"
            setUrlWithoutDomain(if (info.tagName() == "a") info.attr("href") else mangaUrl)
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val episodeJson = document.select("script#episode-json")
            .attr("data-value")
            .let { JSON_PARSER.parse(it).obj }

        return episodeJson["readableProduct"]["pageStructure"]["pages"].asJsonArray
            .filter { it["type"].string == "main" }
            .mapIndexed { i, pageObj ->
                val imageUrl = HttpUrl.parse(pageObj["src"].string)!!.newBuilder()
                    .addQueryParameter("width", pageObj["width"].string)
                    .addQueryParameter("height", pageObj["height"].string)
                    .toString()
                Page(i, document.location(), imageUrl)
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private class SeriesListModeFilter : Filter.Select<String>(
        "一覧",
        LIST_MODES.map { it.first }.toTypedArray()
    )

    override fun getFilterList(): FilterList = FilterList(SeriesListModeFilter())

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        if (!request.url().toString().startsWith(CDN_URL)) {
            return chain.proceed(request)
        }

        val width = request.url().queryParameter("width")!!.toInt()
        val height = request.url().queryParameter("height")!!.toInt()

        val newUrl = request.url().newBuilder()
            .removeAllQueryParameters("width")
            .removeAllQueryParameters("height")
            .build()
        request = request.newBuilder().url(newUrl).build()

        val response = chain.proceed(request)
        val image = decodeImage(response.body()!!.byteStream(), width, height)
        val body = ResponseBody.create(MediaType.parse("image/png"), image)
        return response.newBuilder().body(body).build()
    }

    private fun decodeImage(image: InputStream, width: Int, height: Int): ByteArray {
        val input = BitmapFactory.decodeStream(image)
        val cWidth = (floor(width.toDouble() / (DIVIDE_NUM * MULTIPLE)) * MULTIPLE).toInt()
        val cHeight = (floor(height.toDouble() / (DIVIDE_NUM * MULTIPLE)) * MULTIPLE).toInt()

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val imageRect = Rect(0, 0, width, height)
        canvas.drawBitmap(input, imageRect, imageRect, null)

        for (e in 0 until DIVIDE_NUM * DIVIDE_NUM) {
            val x = e % DIVIDE_NUM * cWidth
            val y = (floor(e.toFloat() / DIVIDE_NUM) * cHeight).toInt()
            val cellSrc = Rect(x, y, x + cWidth, y + cHeight)

            val row = floor(e.toFloat() / DIVIDE_NUM).toInt()
            val dstE = e % DIVIDE_NUM * DIVIDE_NUM + row
            val dstX = dstE % DIVIDE_NUM * cWidth
            val dstY = (floor(dstE.toFloat() / DIVIDE_NUM) * cHeight).toInt()
            val cellDst = Rect(dstX, dstY, dstX + cWidth, dstY + cHeight)
            canvas.drawBitmap(input, cellSrc, cellDst, null)
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    private fun String.tryParseDate(): Long {
        return try {
            DATE_PARSER.parse(this)!!.time
        } catch (e: ParseException) {
            0L
        }
    }

    private fun Response.asJsonObject(): JsonObject = JSON_PARSER.parse(body()!!.string()).obj

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Safari/537.36"
        private val JSON_PARSER by lazy { JsonParser() }
        private val DATE_PARSER by lazy { SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH) }

        private val LIST_MODES = listOf(
            Pair("ジャンプ＋連載一覧", ""),
            Pair("ジャンプ＋読切シリーズ", "oneshot"),
            Pair("連載終了作品", "finished")
        )

        private const val CDN_URL = "https://cdn-ak-img.shonenjumpplus.com"
        private const val DIVIDE_NUM = 4
        private const val MULTIPLE = 8
    }
}
