package eu.kanade.tachiyomi.extension.en.shoujosense

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
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

class ShoujoSense : ParsedHttpSource() {

    override val id: Long = 150

    override val name = "ShoujoSense"

    override val baseUrl = "http://reader.shoujosense.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("yyyy.MM.dd")
        }

        val pagesUrlPattern by lazy {
            Pattern.compile("""\"url\":\"(.*?)\"""")
        }
    }

    override fun popularMangaSelector() = "div.list div.group"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int)
        = GET("$baseUrl/directory/$page", headers)

    override fun latestUpdatesRequest(page: Int)
        = GET("$baseUrl/latest/$page", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("div.title a:eq(0)").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "div.next > a:contains(Next »)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("search", query)
        }
        return POST("$baseUrl/search", headers, form.build())
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.info").first()
        val manga = SManga.create()
        manga.author = infoElement.select("b:contains(Author):first-child").first()?.text()?.split(": ")?.last()
        // ShoujoSense does not have genre tags
        manga.genre = ""
        manga.description = infoElement.select("b:contains(Synopsis:first-child").first()?.text()?.split(": ")?.last()
        manga.status = SManga.UNKNOWN
        manga.thumbnail_url = infoElement.select("img").first()?.attr("src")
        return manga
    }

    fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.list div.element"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        print(chapter.url)
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("div.meta_r").first()?.text()?.split(", ")?.get(1)?.let {
            parseChapterDate(it)
        } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return if ("Today" in date) {
            Calendar.getInstance().timeInMillis
        } else if ("Yesterday" in date) {
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis
        } else {
            try {
                dateFormat.parse(date).time
            } catch (e: ParseException) {
                0L
            }
        }
    }

    override fun pageListRequest(chapter: SChapter) = POST(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body().string()
        val pages = mutableListOf<Page>()

        val p = pagesUrlPattern
        val m = p.matcher(body)

        var i = 0
        while (m.find()) {
            val url = m.group(1)
            pages.add(Page(i++, "", url.replace("""\\"""", "/")))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""
}