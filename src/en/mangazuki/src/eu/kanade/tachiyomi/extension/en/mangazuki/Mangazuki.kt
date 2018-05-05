package eu.kanade.tachiyomi.extension.en.mangazuki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class Mangazuki : ParsedHttpSource() {

    override val name = "Mangazuki"

    override val baseUrl = "https://mangazuki.co"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)

    override fun popularMangaSelector() = "div.filter-content > div.col-sm-6"

    override fun latestUpdatesSelector() = "div.timeline > dl > dd"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga-list", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    private fun mangaFromElement(query: String, element: Element): SManga {
        val manga = SManga.create()
        element.select(query).first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return mangaFromElement("a.chart-title", element)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return mangaFromElement("h3.events-heading > a", element)
    }

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/manga-list").newBuilder()

        return GET(url.toString(), headers)
    }
    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val commonPath = "div.container > div:nth-of-type(3) > div"
        val infoElement = document.select("$commonPath > div:nth-of-type(1) div.widget-container > dl > *")

        val manga = SManga.create()
        manga.author = infoElement.getDetail("Author")
        manga.artist = infoElement.getDetail("Artist")
        manga.genre = infoElement.getDetail("Categories").replace(Regex("\\s+,\\s+"),", ")
        manga.description = document.select("$commonPath > div:nth-of-type(2) > div > div.widget-container > p").text()
        manga.status = parseStatus(infoElement.getDetail("Status"))
        manga.thumbnail_url = document.select("div.boxed > img.img-responsive").first().attr("src")
        return manga
    }

    private fun Elements.getDetail(field: String): String {
        for (e in this) {
            if (e.text().contains(field)) {
                return e.nextElementSibling().text()
            }
        }
        return "Unknown"
    }

    private fun parseStatus(status: String) = when (status) {
        "Ongoing" -> SManga.ONGOING
        "Complete" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.chapters > li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("h3.chapter-title-rtl > a")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("div.date-chapter-title-rtl").first().let {
            parseDateFromElement(it)
        }
        return chapter
    }

    private fun parseDateFromElement(dateElement: Element): Long {
        val dateAsString = dateElement.text().filterNot { it == '.'}

        val date: Date
        try {
            date = dateFormat.parse(dateAsString)
        } catch (e: ParseException) {
            return 0
        }

        return date.time
    }

    override fun pageListParse(document: Document) = document.select("div#all > img").mapIndexed { i, element -> Page(i, "", element.attr("data-src")) }

    override fun imageUrlParse(document: Document) = ""
}