package eu.kanade.tachiyomi.extension.en.mangacow

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

class Mangacow : ParsedHttpSource() {
    override val name = "Mangacow"

    override val baseUrl = "http://mngcow.co"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val pagesUrlPattern by lazy {
            Pattern.compile("""arr_img.push\(\"(.*?)\"\)""")
        }

        val dateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy")
        }
    }

    override fun popularMangaSelector() = "ul#wpm_mng_lst > li > div.det > a"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int)
        = GET("$baseUrl/manga-list/all/any/most-popular/$page/", headers)

    override fun latestUpdatesRequest(page: Int)
        = GET("$baseUrl/manga-list/all/any/last-updated/$page/", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text().trim()
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    // Mangacow does not have many series, so everything is displayed on a single page.
    // If this changes someday, I will update *NextPageSelector() accordingly.
    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("txt_wpm_wgt_mng_sch_nme", query)
            add("cmd_wpm_wgt_mng_sch_sbm", "1")
        }
        return POST("${baseUrl}/manga-list/search/", headers, form.build())
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infoElement = document.select("div.mng_ifo").first()

        author = infoElement.select("a[href*='/manga-list/author/']").map {
            it.text().trim()
        }.joinToString(", ")
        artist = infoElement.select("a[href*='/manga-list/author/']").map {
            it.text().trim()
        }.joinToString(", ")
        genre = infoElement.select("a[href*='/manga-list/category/']").map {
            it.text().trim()
        }.joinToString(", ")
        description = infoElement.select("div.mngdesc").first()?.text()?.trim()
        status = infoElement.select("a[href*='/manga-list/status/']").first().text().let {
            parseStatus(it)
        }
        thumbnail_url = infoElement.select("div.cvr_ara > img.cvr")?.attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var response = response
        val chapters = mutableListOf<SChapter>()
        do {
            val document = response.asJsoup()
            document.select(chapterListSelector()).forEach {
                chapters.add(chapterFromElement(it))
            }
            val nextPage = chapterListNextPageSelector().let { document.select(it).first() }
            if (nextPage != null) {
                response = client.newCall(GET(nextPage.attr("href"))).execute()
            }
        } while (nextPage != null)
        return chapters
    }

    private fun chapterListNextPageSelector() = "ul.pgg > li > a:contains(Next)"

    override fun chapterListSelector() = "ul.mng_chp > li.lng_ > a.lst"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.select("b.val").first().text().trim()
        date_upload = element.select("b.dte").text()?.substringAfterLast("Published on ")?.trim()?.let {
            parseChapterDate(it)
        } ?: 0L
    }

    private fun parseChapterDate(date: String): Long {
       return try {
            dateFormat.parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter) = POST(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val pages = mutableListOf<Page>()
        val m = pagesUrlPattern.matcher(response.body().string())
        var i = 0
        while (m.find()) {
            pages.add(Page(i++, "", m.group(1)))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""

    override fun getFilterList() = FilterList()
}