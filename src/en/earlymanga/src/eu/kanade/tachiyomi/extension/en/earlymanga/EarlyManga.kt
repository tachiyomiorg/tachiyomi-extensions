package eu.kanade.tachiyomi.extension.en.earlymanga

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

class EarlyManga : ParsedHttpSource() {

    override val name = "EarlyManga"

    override val baseUrl = "https://earlymanga.org"

    override val lang = "en"

    override val supportsLatest = true

    private val rateLimitInterceptor = RateLimitInterceptor(1) // 1 request per second

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.151 Safari/537.36")
        .add("Referer", baseUrl)

    // popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/hot-manga?page=$page", headers)

    override fun popularMangaSelector() = "div.content-homepage-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a").attr("abs:href").substringAfter(baseUrl)
        manga.title = element.select(".colum-content a.homepage-item-title").text()
        manga.thumbnail_url = element.select("a img").attr("abs:src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "li.paging:not(.disabled)"

    // latest
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = ".container > .main-content .content-homepage-item"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?search=$query", headers)
    }

    override fun searchMangaSelector() = "div.manga-entry"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a").attr("abs:href").substringAfter(baseUrl)
        manga.title = element.select("div:has(.flag)+a").attr("title")
        manga.thumbnail_url = element.select("a img").attr("abs:src")
        return manga
    }

    override fun searchMangaNextPageSelector(): String? = null

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.select(".manga-page-img").attr("abs:src")
        title = document.select("title").text()
        author = document.select(".author-link a").text()
        artist = document.select(".artist-link a").text()
        status = parseStatus(document.select(".pub_stutus").text())
        description = document.select(".desc").text()
        genre = document.select(".manga-info-card a.badge-secondary").joinToString { it.text() }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("ongoing", true) -> SManga.ONGOING
        status.contains("completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListRequest(manga: SManga) = chapterListRequest(manga.url, 1)

    private fun chapterListRequest(mangaUrl: String, page: Int): Request {
        return GET("$baseUrl$mangaUrl?page=$page", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        var nextPage = 2
        document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
        while (document.select(paginationNextPageSelector).isNotEmpty()) {
            val currentPage = document.select(".nav-link.active").attr("href")
            document = client.newCall(chapterListRequest(currentPage, nextPage)).execute().asJsoup()
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            nextPage++
        }

        return chapters
    }

    private val paginationNextPageSelector = popularMangaNextPageSelector()

    override fun chapterListSelector() = ".chapter-container > .row:not(:first-child)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select(".col>.row>.col-lg-5:not([style*=display:]):not([class*=none]) a[href*=chapter]:not([style*=display:])").attr("href"))
        name = element.select(".col>.row>.col-lg-5:not([style*=display:]):not([class*=none]) a[href*=chapter]:not([style*=display:])").attr("href").substringAfter("chapter")
        name = "Chapter" + name
        date_upload = parseChapterDate(element.select(".ml-1").attr("title"))
    }

    private fun parseChapterDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault()).parse(date)?.time ?: 0L
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(
            "img[src*=manga],img[src*=chapter],div>div>img[src]"
        ).mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")
}
