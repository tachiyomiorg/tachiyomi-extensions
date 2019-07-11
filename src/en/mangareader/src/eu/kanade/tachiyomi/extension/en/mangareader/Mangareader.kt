package eu.kanade.tachiyomi.extension.en.mangareader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.*


class Mangareader : ParsedHttpSource() {

    override val name = "Mangareader"

    override val baseUrl = "http://mangareader.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.mangaresultitem"

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) {
            return GET("$baseUrl/popular/")
        } else {
            return GET("$baseUrl/popular/$nextPageNumber")
        }
    }

    // Site's page numbering is weird, have to do some work to get the right page number for additional requests
    private var nextPageNumber = ""
    val nextPageSelector = "div#sp a:contains(>)"
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        nextPageNumber = document.select(nextPageSelector).attr("href")
            .substringAfterLast("/").substringBefore("\"")

        val manga = mutableListOf<SManga>()
        document.select(popularMangaSelector()).map{manga.add(popularMangaFromElement(it))}

        return MangasPage(manga, document.select(nextPageSelector).hasText())
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        nextPageNumber = document.select(nextPageSelector).attr("href")
            .substringAfterLast("/").substringBefore("\"")

        val manga = mutableListOf<SManga>()
        document.select(latestUpdatesSelector()).map{manga.add(latestUpdatesFromElement(it))}

        return MangasPage(manga, document.select(nextPageSelector).hasText())
    }

    override fun latestUpdatesSelector() = "tr.c3"

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) {
            return GET("$baseUrl/latest/")
        } else {
            return GET("$baseUrl/latest/$nextPageNumber")
        }
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 a").first().let {
            manga.url = it.attr("href")
            manga.title = it.ownText()
        }
        manga.thumbnail_url = element.select("div.imgsearchresults").first().toString().substringAfter("url('").substringBefore("')\">")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.chapter").first().let {
            manga.url = it.attr("href")
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "Not using this"

    override fun latestUpdatesNextPageSelector() = "Not using this"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page==1) {
            return GET("$baseUrl/search/?w=$query&p", headers)
        } else {
            return GET("$baseUrl/search/?w=$query&p=$nextPageNumber", headers)
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#mangaproperties")

        val manga = SManga.create()
        manga.title = infoElement.select("h1").text()
        manga.author = infoElement.select("td.propertytitle:containsOwn(author) + td").text()
        manga.artist = infoElement.select("td.propertytitle:containsOwn(artist) + td").text()
        val status = infoElement.select("td.propertytitle:containsOwn(status) + td").text()
        manga.status = parseStatus(status)
        manga.genre = infoElement.select("td.propertytitle:containsOwn(genre) + td").text()
        manga.description = document.select("div#readmangasum p").text()
        manga.thumbnail_url = document.select("img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Site orders chapters oldest to newest, reverse that to be in line with most other sources
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() = "div#chapterlist tr:gt(0)"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.select("a").attr("href")
        chapter.name = element.select("td:has(a)").text()
        chapter.date_upload = parseDate(element.select("td:has(a) + td").text())
        return chapter
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(date).time
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val chapterUrl = document.select("select#pageMenu option").attr("value") + "/"
        document.select("select#pageMenu").text().split(" ").forEach {
            pages.add(Page(pages.size, "$chapterUrl$it"))
        }
        return pages
    }

    // Get the page
    override fun imageUrlRequest(page: Page) = GET("$baseUrl" + page.url)

   //  Get the image from the requested page
    override fun imageUrlParse (response: Response): String {
        val document = response.asJsoup()
        return document.select("a img").attr("src")
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
