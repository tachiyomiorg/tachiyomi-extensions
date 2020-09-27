package eu.kanade.tachiyomi.extension.vi.truyentranhlh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TruyenTranhLH : ParsedHttpSource() {

    override val name = "TruyenTranhLH"

    override val baseUrl = "https://truyentranhlh.net"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:75.0) Gecko/20100101 Firefox/75.0")

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/tim-kiem?sort=top&page=$page", headers)
    }

    override fun popularMangaSelector() = "div.thumb-item-flow"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.series-title a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("div.content").attr("abs:data-bg")
        }
    }

    override fun popularMangaNextPageSelector() = "div.pagination_wrap a.page_num.current + a:not(.disabled)"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/tim-kiem?sort=update&page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/tim-kiem?q=$query&sort=update&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.top-part")
        return SManga.create().apply {
            genre = infoElement.select("span.info-name:contains(Thể loại) + span a").joinToString { it.text() }
            author = infoElement.select("span.info-name:contains(Tác giả) + span").text()
            status = infoElement.select("span.info-name:contains(Tình trạng) + span").text().toStatus()
            thumbnail_url = infoElement.select("div.content").attr("style")
                .let { Regex("""url\("(.*)"\)""").find(it)?.groups?.get(1)?.value }
            description = document.select("div.summary-content").text()
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        this.contains("Đã hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector(): String = "ul.list-chapters a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.select("div.chapter-name").text()
            date_upload = element.select("div.chapter-time").firstOrNull()?.text()
                ?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it)?.time ?: 0L } ?: 0
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#chapter-content img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
