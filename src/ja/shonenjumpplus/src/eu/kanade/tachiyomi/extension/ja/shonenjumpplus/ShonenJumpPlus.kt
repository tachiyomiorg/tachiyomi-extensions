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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
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

class ShonenJumpPlus : ParsedHttpSource() {

    override val name = "Shonen Jump+"

    override val baseUrl = "https://shonenjumpplus.com"

    override val lang = "ja"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { imageIntercept(it) }
        .build()

    private val dayOfWeek: String
        get() = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "sunday"
            Calendar.MONDAY -> "monday"
            Calendar.TUESDAY -> "tuesday"
            Calendar.WEDNESDAY -> "wednesday"
            Calendar.THURSDAY -> "thursday"
            Calendar.FRIDAY -> "friday"
            else -> "saturday"
        }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series", headers)

    override fun popularMangaSelector(): String = "ul.series-list li a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h2.series-list-title").first()!!.text()
        thumbnail_url = element.select("div.series-list-thumb img").first()!!.attr("data-src")
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

        val path = arrayOf("", "oneshot", "finished")[(filters[0] as SeriesListMode).state]
        return GET("$baseUrl/series/$path", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request().url().toString().contains("search"))
            return super.searchMangaParse(response)

        return popularMangaParse(response)
    }

    override fun searchMangaSelector() = "ul.search-series-list li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.title-box p.series-title").first()!!.text()
        thumbnail_url = element.select("div.thmb-container a img").first()!!.attr("src")
        setUrlWithoutDomain(element.select("div.thmb-container a").first()!!.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("section.series-information div.series-header")

        title = infoElement.select("h1.series-header-title").first()!!.text()
        author = infoElement.select("h2.series-header-author").first()!!.text()
        artist = author
        description = infoElement.select("p.series-header-description").first()!!.text()
        thumbnail_url = infoElement.select("div.series-header-image-wrapper img").first()!!.attr("data-src")
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

        var result = client.newCall(GET(readMoreEndpoint, newHeaders)).execute()

        while (result.code() != 404) {
            val json = result.asJsonObject()
            readMoreEndpoint = json["nextUrl"].string
            val tempDocument = Jsoup.parse(json["html"].string)

            chapters += tempDocument
                .select("ul.series-episode-list " + chapterListSelector())
                .map { element -> chapterFromElement(element, response.request().url().toString()) }

            result = client.newCall(GET(readMoreEndpoint, newHeaders)).execute()
        }

        return chapters
    }

    override fun chapterListSelector() = "li.episode:has(span.series-episode-list-is-free)"

    private fun chapterFromElement(element: Element, mangaUrl: String): SChapter {
        val info = element.select("a.series-episode-list-container").first() ?: element

        return SChapter.create().apply {
            name = info.select("h4.series-episode-list-title").first()!!.text()
            date_upload = parseChapterDate(info.select("span.series-episode-list-date").first()?.text().orEmpty())
            scanlator = "集英社"
            setUrlWithoutDomain(if (info.tagName() == "a") info.attr("href") else mangaUrl)
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val episodeId = chapter.url.substringAfterLast("/")
        return GET("$baseUrl/episode/$episodeId.json", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val json = response.asJsonObject()
        val pages = json["readableProduct"]["pageStructure"]["pages"].asJsonArray

        return pages
            .filter { it["type"].string == "main" }
            .mapIndexed { i, pageObj ->
                val imageUrl = "${pageObj["src"].string}?width=${pageObj["width"].string}&height=${pageObj["height"].string}"
                Page(i, "", imageUrl)
            }
    }

    override fun imageUrlParse(document: Document) = ""

    private class SeriesListMode : Filter.Select<String>("一覧", arrayOf("ジャンプ＋連載一覧", "ジャンプ＋読切シリーズ", "連載終了作品"))

    override fun getFilterList(): FilterList = FilterList(SeriesListMode())

    override fun chapterFromElement(element: Element): SChapter = throw Exception("This method should not be called!")

    override fun pageListParse(document: Document): List<Page> = throw Exception("This method should not be called!")

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

    private fun parseChapterDate(date: String): Long {
        return try {
            DATE_PARSER.parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    private fun Response.asJsonObject(): JsonObject = JSON_PARSER.parse(body()!!.string()).obj

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
        private val JSON_PARSER by lazy { JsonParser() }
        private val DATE_PARSER by lazy { SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH) }

        private const val CDN_URL = "https://cdn-ak-img.shonenjumpplus.com"
        private const val DIVIDE_NUM = 4
        private const val MULTIPLE = 8
    }
}
