package eu.kanade.tachiyomi.extension.en.mangareader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

abstract class MRP(
    override val name: String,
    override val baseUrl: String
) : ParsedHttpSource() {

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular" + if (page > 1) "/${(page - 1) * 30}" else "", headers)
    }

    override fun popularMangaSelector() = "div.mangaresultitem"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 a").first().let {
            manga.url = it.attr("href")
            manga.title = it.ownText()
        }
        manga.thumbnail_url = element.select("div.imgsearchresults").first().toString().substringAfter("url('").substringBefore("')\">")
        return manga
    }

    override fun popularMangaNextPageSelector() = "div#sp strong + a"

    private var nextLatestPage: String? = null

    override fun latestUpdatesRequest(page: Int): Request {
        return if (page == 1) {
            nextLatestPage = null
            GET("$baseUrl/latest", headers)
        } else {
            GET(nextLatestPage!!, headers)
        }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        nextLatestPage = document.select(latestUpdatesNextPageSelector()).firstOrNull()?.attr("abs:href")

        return MangasPage(mangas, nextLatestPage != null)
    }

    override fun latestUpdatesSelector() = "tr.c3"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.chapter").first().let {
            manga.url = it.attr("href")
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/?w=$query" + if (page > 1) "&p=${(page - 1) * 30}" else "", headers)
    }

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
        manga.genre = infoElement.select("td.propertytitle:containsOwn(genre) + td a").joinToString { it.text() }
        manga.description = document.select("div#readmangasum p").text()
        manga.thumbnail_url = document.select("img").attr("src")
        return manga
    }

    protected fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Site orders chapters oldest to newest, reverse that to be in line with most other sources
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() = "table#listing tr:not(.table_head)"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("a").let {
            chapter.setUrlWithoutDomain(it.attr("href"))
            chapter.name = it.text()
        }
        chapter.date_upload = parseDate(element.select("td + td").text())
        return chapter
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(date)?.time ?: 0
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
    override fun imageUrlRequest(page: Page) = GET(baseUrl + page.url)

    // Get the image from the requested page
    override fun imageUrlParse(document: Document): String {
        return document.select("a img").attr("abs:src")
    }
}
