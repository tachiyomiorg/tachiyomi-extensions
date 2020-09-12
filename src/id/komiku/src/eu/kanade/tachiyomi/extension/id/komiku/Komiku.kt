package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.util.Calendar
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Komiku : ParsedHttpSource() {
    override val name = "Komiku"

    override val baseUrl = "https://komiku.co.id/"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.bge"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/other/hot/page/$page/?orderby=modified", headers)

    private val coverRegex = Regex("""(/Manga-|/Manhua-|/Manhwa-)""")
    private val coverUploadRegex = Regex("""/uploads/\d\d\d\d/\d\d/""")

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a:has(h3)").attr("href"))
        title = element.select("h3").text().trim()

        // scraped image doesn't make for a good cover; so try to transform it
        // make it null if it contains upload date as those URLs aren't very useful
        thumbnail_url = element.select("img").attr("abs:src")
            .substringBeforeLast("?")
            .replace(coverRegex, "/Komik-")
            .let { if (it.contains(coverUploadRegex)) null else it }
    }

    override fun popularMangaNextPageSelector() = "a.next.popunder"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga/page/$page", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page/?post_type=manga&s=$query", headers)

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "a.next"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select("#Sinopsis > p").text().trim()
        genre = document.select("li[itemprop=genre] > a").joinToString { it.text() }
        status = parseStatus(document.select("table.inftable tr > td:contains(Status) + td").text())
        thumbnail_url = document.select("div.ims > img").attr("abs:src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "table.chapter tr:has(td.judulseries)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a.popunder").attr("href"))
        name = element.select("a.popunder").attr("title")

        // Has datetime attribute, but all are set to start of current day for whatever reason, so parsing text instead
        date_upload = parseRelativeDate(element.select("time").text().trim()) ?: 0
    }

    // Used Google translate here
    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.substringBefore(" lalu").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "tahun" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
            "bulan" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
            "minggu" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
            "hari" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            "jam" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "menit" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "detik" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.bc > img").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
