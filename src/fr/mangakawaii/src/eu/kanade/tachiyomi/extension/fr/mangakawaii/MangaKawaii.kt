package eu.kanade.tachiyomi.extension.fr.mangakawaii

import android.net.Uri
import android.util.Base64
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Heavily customized MyMangaReaderCMS source
 */
class MangaKawaii : ParsedHttpSource() {

    override val name = "Mangakawaii"
    override val baseUrl = "https://www.mangakawaii.com"
    override val lang = "fr"
    override val supportsLatest = true
    private val rateLimitInterceptor = RateLimitInterceptor(1) // 1 request per second

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    protected open val userAgentRandomizer1 = "${Random.nextInt(9).absoluteValue}"
    protected open val userAgentRandomizer2 = "${Random.nextInt(10, 99).absoluteValue}"
    protected open val userAgentRandomizer3 = "${Random.nextInt(100, 999).absoluteValue}"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/8$userAgentRandomizer1.0.4$userAgentRandomizer3.1$userAgentRandomizer2 Safari/537.36"
        )

    // Popular
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
    override fun popularMangaSelector() = "a.hot-manga__item"
    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.hot-manga__item-caption").select("div.hot-manga__item-name").text().trim()
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = "https://cdn.mangakawaii.com/uploads" + element.select("a").attr("href") + "/cover/cover_250x350.jpg"
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)
    override fun latestUpdatesSelector() = ".section__list-group li div.section__list-group-left"
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a").attr("title")
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = "https://cdn.mangakawaii.com/uploads" + element.select("a").attr("href") + "/cover/cover_250x350.jpg"
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/search").buildUpon()
            .appendQueryParameter("query", query)
            .appendQueryParameter("search_type", "manga")
        return GET(uri.toString(), headers)
    }
    override fun searchMangaSelector() = "h1 + ul a[href*=manga]"
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a").text().trim()
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = "https://cdn.mangakawaii.com/uploads" + element.select("a").attr("href") + "/cover/cover_250x350.jpg"
    }

    // Manga details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.select("div.manga-view__header-image").select("img").attr("abs:src")
        description = document.select("dd.text-justify.text-break").text()
        author = document.select("a[href*=author]").text()
        artist = document.select("a[href*=artist]").text()
        genre = document.select("a[href*=category]").joinToString { it.text() }
        status = when (document.select("span.badge.bg-success.text-uppercase").text()) {
            "En Cours" -> SManga.ONGOING
            "Terminé" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapter list
    override fun chapterListSelector() = throw Exception("Not used")
    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        var widgetDocument = document
        val widgetPageListUrl = Regex("""['"](/arrilot/load-widget.*?)['"]""").find(document.toString())?.groupValues?.get(1)
        if (widgetPageListUrl != null) {
            widgetDocument = client.newCall(GET("$baseUrl$widgetPageListUrl", headers)).execute().asJsoup()
        }

        return widgetDocument.select("tr[class*=volume-]:has(td)").map {
            SChapter.create().apply {
                url = it.select("td.table__chapter").select("a").attr("href")
                name = it.select("td.table__chapter").select("span").text().trim()
                chapter_number = it.select("td.table__chapter").select("span").text().substringAfter("Chapitre").replace(Regex("""[,-]"""), ".").trim().toFloatOrNull()
                    ?: -1F
                date_upload = it.select("td.table__date").firstOrNull()?.text()?.let { parseDate(it) }
                    ?: 0
                scanlator = document.select("[itemprop=translator] a").joinToString { it.text().replace(Regex("""[\[\]]"""), "") }
            }
        }
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date)?.time ?: 0L
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val selectorEncoded1 = "Wkdim" + "gsai" + "mgWQyV2lkMm" + "xrS2img" + "ppZDFoY" + "kd4ZElHaW" +
            "RsdimgFp6cHVi" + "M1FvVzNOeVl5" + "bimgzlpZG" + "lkWjJsbVhT" + "a3imgNJQzVq" + "YjI1MFlpZFd" +
            "saWR1WlhJdFi" + "mgpteDFhV1FnTGi" + "mg5KdmlkZHlC" + "a2FYWimgTZiaW" + "imgRtOTBL" + "QzV0ZUMxaim" +
            "gGRYUnZpZEtT" + "QTZibTki" + "mgwS0imgRwdm" + "JteGlkimgNU" + "xXTm9hV3himgr" + "aWRLU0JwYldjNm" + "JtOTBpZEti" +
            "mgGdHpp" + "ZGNtTXFQimg" + "V2RwWml" + "kbDBw"
        val selectorEncoded2 = String(Base64.decode(selectorEncoded1.replace("img", ""), Base64.DEFAULT))
        val selectorDecoded = String(Base64.decode(selectorEncoded2.replace("id", ""), Base64.DEFAULT))
        val elements = document.select(selectorDecoded)

        val pages = mutableListOf<Page>()
        var j = 0
        for (i in 0 until elements.count()) {
            if (elements[i].attr("src").trim() != "") {
                pages.add(Page(j, document.location(), elements[i].attr("src").trim()))
                ++j
            }
        }
        return pages
    }
    override fun imageUrlParse(document: Document): String = throw Exception("Not used")
    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Referer", page.url)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }
}
