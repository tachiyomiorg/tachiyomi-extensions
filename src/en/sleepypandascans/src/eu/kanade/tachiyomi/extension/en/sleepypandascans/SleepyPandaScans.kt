package eu.kanade.tachiyomi.extension.en.sleepypandascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

class SleepyPandaScans : ParsedHttpSource() {

    override val name = "Sleepy Panda Scans"

    override val baseUrl = "http://sleepypandascans.co"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/Series", headers)
    }

    override fun popularMangaSelector() = "div.card.card-cascade"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.url = element.select("a").attr("href")
        manga.title = element.select("h6").text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "Not needed"

    // Latest

    // Track which manga titles have been added to latestUpdates's MangasPage
    private val latestUpdatesTitles = mutableSetOf<String>()

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) latestUpdatesTitles.clear()
        return GET(baseUrl)
    }

    // Only add manga to MangasPage if its title is not one we've added already
    override fun latestUpdatesParse(response: Response): MangasPage {
        val latestManga = mutableListOf<SManga>()
        val document = response.asJsoup()

        document.select(latestUpdatesSelector()).forEach { element ->
            latestUpdatesFromElement(element).let { manga ->
                if (manga.title !in latestUpdatesTitles) {
                    latestManga.add(manga)
                    latestUpdatesTitles.add(manga.title)
                }
            }
        }
        return MangasPage(latestManga, document.select(latestUpdatesNextPageSelector()).hasText())
    }

    // Updates less than a day old are patron only, ignore them
    override fun latestUpdatesSelector() = "div.card.card-cascade:not(div.amber)"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.url = element.select("a").attr("href")
            .replace("Reader", "Series").substringBeforeLast("/")
        manga.title = element.select("h5").text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    // Source's website doesn't appear to have a search function; so searching locally
    private var searchQuery = ""

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        searchQuery = query.toLowerCase()
        return popularMangaRequest(1)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchMatches = mutableListOf<SManga>()
        val document = response.asJsoup()

        document.select(searchMangaSelector())
            .filter { it.text().toLowerCase().contains(searchQuery) }
            .map { searchMatches.add(searchMangaFromElement(it)) }

        return MangasPage(searchMatches, false)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.card-body")

        val manga = SManga.create()
        manga.title = infoElement.select("h4").text()
        manga.description = infoElement.select("p").text()
        return manga
    }

    // Chapters

    // Chapters less than a day old are patron only, ignore them
    override fun chapterListSelector() = "div.list-group a:not(a[data-target])"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.attr("href")
        chapter.name = element.ownText()
        chapter.date_upload = parseRelativeDate(element.select("span").text().substringBefore(" ago"))
        return chapter
    }

    // Subtract relative date (e.g. posted 3 days ago)
    private fun parseRelativeDate(date: String): Long {
        val calendar = Calendar.getInstance()

        if (date.contains("yesterday")) {
            calendar.apply{add(Calendar.DAY_OF_MONTH, -1)}
        } else {
            val trimmedDate = date.replace("one", "1").removeSuffix("s").split(" ")

            when (trimmedDate[1]) {
                "year" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
                "month" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
                "week" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
                "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            }
        }

        return calendar.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.view img").forEach {
            pages.add(Page(pages.size, "", it.attr("abs:src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
