package eu.kanade.tachiyomi.extension.en.valhallascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.*

class ValhallaScans : ParsedHttpSource() {

    override val name = "Valhalla Scans"

    override val baseUrl = "https://valhallascans.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/projects")
    }

    override fun popularMangaSelector() = "div.col-sm-4"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.url = it.attr("href")
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "No next page"

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

    override fun latestUpdatesSelector() = "div.col-sm-6"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("div.pt-0 a").first().let {
            manga.url = it.attr("href")
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "No next page"

    // Search

    /* Sources' websites isn't able to search the whole catalog at once, instead we'd have to
       do separate searches for ongoing, hiatus, dropped, and completed and then combine those results.
       Since the catalog is small, it seems easier to do the search client-side */
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

    // Manga details

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.py-3")

        val manga = SManga.create()
        manga.title = infoElement.select("h3").text()
        manga.author = infoElement.select("strong:contains(author) + span").text()
        manga.artist = infoElement.select("strong:contains(artist) + span").text()
        manga.description = infoElement.select("strong:contains(synopsis) + span").text()
        manga.status = parseStatus(infoElement.select("strong:contains(status) + span").text())
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapter

    override fun chapterListSelector() = "div.row:has(.col-8)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a + a")

        val chapter = SChapter.create()
        chapter.url = urlElement.attr("href")
        chapter.name = urlElement.text()
        chapter.date_upload = parseDate(element.select("div.col-4").text())
        return chapter
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date).time
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val body = FormBody.Builder()
            .add("mode", "Webtoon")
            .build()
        return POST("$baseUrl/${chapter.url}", headers, body)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div#pages img").forEachIndexed { i, element ->
            pages.add(Page(i, "", element.attr("abs:src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
