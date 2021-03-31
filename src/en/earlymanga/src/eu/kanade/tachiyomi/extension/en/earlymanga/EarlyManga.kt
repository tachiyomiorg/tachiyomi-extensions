package eu.kanade.tachiyomi.extension.en.earlymanga

import android.util.Base64
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
import kotlin.math.absoluteValue
import kotlin.random.Random

class EarlyManga : ParsedHttpSource() {

    override val name = "EarlyManga"

    override val baseUrl = "https://earlymanga.org"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    protected open val userAgentRandomizer1 = "${Random.nextInt(9).absoluteValue}"
    protected open val userAgentRandomizer2 = "${Random.nextInt(10,99).absoluteValue}"
    protected open val userAgentRandomizer3 = "${Random.nextInt(100,999).absoluteValue}"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/8$userAgentRandomizer1.0.4$userAgentRandomizer3.1$userAgentRandomizer2 Safari/537.36"
        )
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
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesSelector() = ".container > .main-content .content-homepage-item"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = ".load-data-btn"

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
        description = document.select(".desc:not([class*=none])").text().replace("_", "")
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

    override fun chapterListSelector() = ".chapter-container > .row:not(:first-child,.d-none)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val selectorEncoded1 = "TG1OdmJDro" + "wQWdJQ2NvbEFro" + "wnSUNBZ0lDQWdJQ0FrownSUNj" + "b2xBZ0lDQWdJQ0rowFnSUNBZ0xuSnZkeWN" +
            "vbEFnSUNBZ0rowlDQWdJRDRjb2xnSUNBZ0xt" + "TnZiQzFzWnkwMUlDQWrowdJRDRnSU" + "NBZ1lUcHViM1FvT21acGNu" + "TjBMV05rowvYVd4a0tTd2dJY29s" +
            "Q0FnSUM1amIyd2dJQ0Fn" + "SUNBdWNtOTNJQ0FnSWNvbENB" + "Z0lDQWdMbU52row" + "YkMxc1p5MDFJQ0FnY2" +
            "9sSUNBZ0lDQWdJR0ZiYUhKbFppb" + "zlZMmhoY0hSbGNpMWRXY2ro" + "w9sMmh5WldZcVBWd3ZZMmhoY0hSbGN" + "sMDZhR0Z6S2NvbEdScG" + "Rpaz0="
        val selectorEncoded2 = String(Base64.decode(selectorEncoded1.replace("row", ""), Base64.DEFAULT))
        val selectorDecoded = String(Base64.decode(selectorEncoded2.replace("col", ""), Base64.DEFAULT))
        setUrlWithoutDomain(element.select(selectorDecoded).attr("href"))
        name = "Chapter " + url.substringAfter("chapter-")
        date_upload = parseChapterDate(element.select(".ml-1").attr("title"))
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.US).parse(date)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
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
