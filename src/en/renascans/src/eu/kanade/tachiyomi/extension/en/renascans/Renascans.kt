package eu.kanade.tachiyomi.extension.en.renascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.*

class Renascans : ParsedHttpSource() {

    override val name = "Mangasail"

    override val baseUrl = "https://renascans.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.media"

    override fun popularMangaRequest(page: Int): Request {
            return GET("$baseUrl/manga-list?page=$page")
    }

    override fun latestUpdatesSelector() = "div.events"

    override fun latestUpdatesRequest(page: Int): Request {
            return GET("$baseUrl/latest-release?page=$page")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.chart-title").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").first().attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").first().attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "li a:contains(»)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // source returns JSON data, doing a local search instead
    // need some variables accessible by multiple search functions
    private var searchQuery = ""
    private var searchPage = 1
    private var nextPageSelectorElement = Elements()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) searchPage = 1
        searchQuery = query.toLowerCase()
        return GET("$baseUrl/manga-list?page=$page")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchMatches = mutableListOf<SManga>()
        val document = response.asJsoup()
        searchMatches.addAll(getMatchesFrom(document))

        /* call another function if there's more pages to search
           not doing it this way can lead to a false "no results found"
           if no matches are found on the first page but there are matches
           on subsequent pages */
        nextPageSelectorElement = document.select(searchMangaNextPageSelector())
        while (nextPageSelectorElement.hasText()) {
            searchMatches.addAll(searchMorePages())
        }

        return MangasPage(searchMatches, false)
    }

    // search the given document for matches
    private fun getMatchesFrom(document: Document): MutableList<SManga> {
        val searchMatches = mutableListOf<SManga>()
        document.select(searchMangaSelector()).forEach {
            if (it.text().toLowerCase().contains(searchQuery)) {
                searchMatches.add(searchMangaFromElement(it))
            }
        }
        return searchMatches
    }

    // search additional pages if called
    private fun searchMorePages(): MutableList<SManga> {
        searchPage++
        val nextPage = client.newCall(GET("$baseUrl/manga-list?page=$searchPage", headers)).execute().asJsoup()
        val searchMatches = mutableListOf<SManga>()
        searchMatches.addAll(getMatchesFrom(nextPage))
        nextPageSelectorElement = nextPage.select(searchMangaNextPageSelector())

        return searchMatches
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.row")

        val manga = SManga.create()
        manga.title = infoElement.select("h2").first().text()
        manga.author = infoElement.select("dt:contains(author) + dd").text()
        manga.artist = infoElement.select("dt:contains(artist) + dd").text()
        manga.genre = infoElement.select("dt:contains(categories) + dd").text()
        val status = infoElement.select("dt:contains(status) + dd").text()
        manga.status = parseStatus(status)
        manga.description = document.select("h5 + p").text()
        manga.thumbnail_url = document.select("img.img-responsive").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "li:has([class])"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("h3 a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("h3").text()
        chapter.date_upload = parseChapterDate(element.select("div.date-chapter-title-rtl").text()) ?: 0
        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM. yyyy", Locale.US)
        }
    }

    private fun parseChapterDate(string: String): Long? {
            return dateFormat.parse(string.substringAfter("on ")).time
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.col-xs-12 img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("data-src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
