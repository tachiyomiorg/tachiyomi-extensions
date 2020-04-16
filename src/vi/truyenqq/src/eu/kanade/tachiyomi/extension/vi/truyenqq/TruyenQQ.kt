package eu.kanade.tachiyomi.extension.vi.truyenqq

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

class TruyenQQ : ParsedHttpSource() {
    override val name: String = "TruyenQQ"
    override val lang: String = "vi"
    override val baseUrl: String = "https://truyenqq.com"
    override val supportsLatest: Boolean = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().add("Referer", baseUrl)
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/top-thang/trang-$page.html", headers)
    }
    override fun popularMangaNextPageSelector(): String? = "a.pagination-link:contains(›)"
    override fun popularMangaSelector(): String = "div.story-item"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").first().attr("abs:href"))
        thumbnail_url = element.select("img.story-cover").attr("abs:src")
        title = element.select(".title-book a").text()
    }

    //Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/truyen-moi-cap-nhat/trang-$page.html", headers)
    }
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    //Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/tim-kiem/trang-$page.html").buildUpon()
            .appendQueryParameter("q",query)
        return GET(uri.toString(), headers)

        //Todo Filters
    }
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    //Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1").text()
        author = document.select(".info-item:eq(1)").text().substringAfter(":").trim()
        artist = author
        val glist = document.select(".list01 li").map { it.text() }
        genre = glist.joinToString(", ")
        description = document.select(".story-detail-info").text()
        thumbnail_url = document.select("div.left img").attr("src")
        status = when (document.select(".info-item:eq(2)").text().substringAfter(":").trim()) {
            "Đang Cập Nhật" -> SManga.ONGOING
            //"" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    //Chapters

    override fun chapterListSelector(): String = "div.works-chapter-list div.works-chapter-item"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("abs:href"))
        name = element.select("a").text().trim()
        date_upload = parseDate(element.select("div.text-right").text())
        chapter_number = name.substringAfter("Chương").trim().toFloat()
    }
    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd/MM/yyyy", Locale.US ).parse(date).time
    }

    //Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("img.lazy").forEachIndexed { index, element ->
            add(Page(index,"",element.attr("abs:src"))) }
    }
    override fun imageUrlParse(document: Document): String {
        throw Exception("Not Used")
    }

    //Not Used
}
