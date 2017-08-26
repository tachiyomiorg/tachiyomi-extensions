package eu.kanade.tachiyomi.extension.en.merakiscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.regex.Pattern

class MerakiScans : ParsedHttpSource() {
    override val name = "MerakiScans"

    override val baseUrl = "http://merakiscans.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy")
        }
    }

    override fun popularMangaSelector() = "div.mng_lst > div.nde > div.det > a"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int)
        = GET("$baseUrl/manga-list/all/any/most-popular/", headers)

    override fun latestUpdatesRequest(page: Int)
        = GET("$baseUrl/manga-list/all/any/last-updated/", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text().trim()
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "div.next > a.gbutton:contains(Next »)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("cmd_wpm_search", "Search")
            add("txt_wpm_wgt_mng_sch_nme", query)
            add("cmd_wpm_wgt_mng_sch_sbm", "1")
        }
        return POST("$baseUrl/manga-list/search/", headers, form.build())
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.mng_det > div.mng_ifo")

        val manga = SManga.create()
        infoElement.select("div.det > p").forEachIndexed { i, el ->
            if (i == 0) {
                manga.description = el.text().trim()
            }
            when (el.select("b").text().trim()) {
                "Author" -> manga.author = el.select("a").text()?.trim()
                "Artist" -> manga.artist = el.select("a").text()?.trim()
                "Category" -> manga.genre = el.select("a").map {
                        it.text().trim()
                    }.joinToString(", ")
                "Status" -> manga.status = el.select("a").text().orEmpty().let {
                        parseStatus(it)
                    }
            }
        }
        manga.thumbnail_url = infoElement.select("div.cvr_ara > img").attr("src")

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.lst > li.lng_ > a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.select("b.val").text().trim()
        chapter.date_upload = element.select("b.dte").text().trim().let { parseChapterDate(it) }
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter) = POST(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> =
        response.asJsoup().select("center#longWrap > img").mapIndexed { i, img ->
            Page(i, "", img.attr("src"))
        }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""

    override fun getFilterList() = FilterList()
}