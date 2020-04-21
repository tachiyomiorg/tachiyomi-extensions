package eu.kanade.tachiyomi.extension.en.renascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import com.github.salomonbrys.kotson.*
import com.google.gson.Gson
import com.google.gson.JsonObject

class Renascans : ParsedHttpSource() {

    override val name = "Renascence Scans (Renascans)"

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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?query=$query")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchMatches = mutableListOf<SManga>()

        val array = Gson().fromJson<JsonObject>(response.body()!!.string())["suggestions"].asJsonArray
        for (i in 0 until array.size()) {
            val manga = SManga.create()
            manga.title = array[i]["value"].asString
            manga.url = "/manga/" + array[i]["data"].asString
            manga.thumbnail_url = "$baseUrl/uploads/manga/" + array[i]["data"].asString + "/cover/cover_250x350"
            searchMatches.add(manga)
        }
        return MangasPage(searchMatches, false)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "None"

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
            var page = it.attr("data-src")
            if (page.isNotEmpty()) {
                pages.add(Page(pages.size, "", page))
            }
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
