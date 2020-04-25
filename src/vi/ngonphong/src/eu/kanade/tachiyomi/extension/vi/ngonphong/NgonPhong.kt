package eu.kanade.tachiyomi.extension.vi.ngonphong

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NgonPhong : ParsedHttpSource() {

    override val name = "Ngon Phong"

    override val baseUrl = "https://ngonphongcomics.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", baseUrl)

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/danh-sach-truyen/?sort=view&trang=$page", headers)
    }

    override fun popularMangaSelector() = "div.comic-item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select(".comic-title-link > a").let {
                title = it.attr("title") ?: it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img.img-thumbnail").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "ul.phantrang li a[title=next]"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/danh-sach-truyen/?sort=latest&trang=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/?s=$query", headers)
    }

    override fun searchMangaSelector() = "table.comic-list-table tbody tr"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a").first().let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
        }
    }

    override fun searchMangaNextPageSelector(): String? = null

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("div.comic-intro div.row").let { info ->
                title = info.select("h2").text()
                author = info.select("span.green").text()
                genre = info.select("strong:contains(Thể loại:) ~ a").joinToString { it.text() }
                status = info.select("strong:contains(Tình trạng:) + span").text().toStatus()
                thumbnail_url = info.select("img.img-thumbnail").attr("abs:src")
            }
            description = document.select("div.comic-intro div.row + div p").text()
        }
    }

    private fun String.toStatus() = when {
        this.contains("Đang cập nhật", ignoreCase = true) -> SManga.ONGOING
        this.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "table.table tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = element.select("td:nth-child(2)").text().toChapterDate()
        }
    }

    private fun String.toChapterDate(): Long {
        return try {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(this).time
        } catch (_: Exception) {
            0L
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("script:containsData(htmlContent)").first().data().substringAfter("htmlContent=[")
            .substringBefore("];").replace(Regex("""["\\]"""), "").split(",")
            .mapIndexed { i, image -> Page(i, "", image) }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
