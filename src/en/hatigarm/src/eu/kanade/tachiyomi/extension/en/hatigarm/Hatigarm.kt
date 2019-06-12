package eu.kanade.tachiyomi.extension.en.Hatigarm

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat

class Hatigarm : ParsedHttpSource() {

    override val name = "Hatigarm Scans (non-Foolslide)"

    override val baseUrl = "http://hatigarmscans.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.col-sm-6"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga-list?page=$page")
    }

    override fun latestUpdatesSelector() = "div.manga-item"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-release?page=$page")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h5 a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("a.thumbnail img").first().attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = null
        return manga
    }

    override fun popularMangaNextPageSelector() = "li:has([rel=\"next\"])"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    //website's search returns JSON data; searching through titles found in an open directory instead
    private var searchQuery = ""
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        searchQuery = query.toLowerCase()
        return GET("http://hatigarmscans.net/uploads/manga/", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchMatches = mutableListOf<SManga>()

        val document = response.asJsoup()
        document.select("tr:gt(2) a").forEach {
            if (it.attr("href").replace("""[-/]""".toRegex(), " ").toLowerCase().contains(searchQuery)) {
                val searchMatch = searchMangaFromElement(it)
                searchMatches.add(searchMatch)
            }
        }

        return MangasPage(searchMatches, false)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = "/manga/" + element.attr("href")
        manga.title = element.attr("href").replace("""[-/]""".toRegex(), " ")
        return manga
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.col-sm-12").first()

        val manga = SManga.create()
        manga.title = infoElement.select("h2").text()
        manga.author = infoElement.select("dt:contains(author) + dd").text()
        manga.artist = infoElement.select("dt:contains(artist) + dd").text()
        val status = infoElement.select("dt:contains(status) + dd").text()
        manga.status = parseStatus(status)
        manga.genre = infoElement.select("dt:contains(categories) + dd").text()
        manga.description = document.select("div.well:contains(summary)").text().substringAfter("Summary ")
        manga.thumbnail_url = document.select("div.col-sm-4 img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Complete") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.chapters li:has([style])"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text().substringBefore(" download")

        val cDate = element.select("div.date-chapter-title-rtl").text()
        chapter.date_upload = parseChapterDate(cDate)
        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM. yyyy")
        }
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date.replace(Regex("(st|nd|rd|th)"), "")).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div#all img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("data-src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("No used")

    override fun getFilterList() = FilterList()

}
