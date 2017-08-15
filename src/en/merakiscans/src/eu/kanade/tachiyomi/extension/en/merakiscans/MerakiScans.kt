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
        val pagesUrlPattern by lazy {
            Pattern.compile("""\"url\":\"(.*?)\"""")
        }

        val dateFormat by lazy {
            SimpleDateFormat("yyyy.MM.dd")
        }
    }

    override fun popularMangaSelector() = "div.list > div.group > div.title > a"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int)
        = GET("$baseUrl/reader/directory/$page/", headers)

    override fun latestUpdatesRequest(page: Int)
        = GET("$baseUrl/reader/latest/$page/", headers)

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
            add("search", query)
        }
        return POST("$baseUrl/reader/search/$page", headers, form.build())
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.panel > div.comic > div.large > div.info").first()

        val manga = SManga.create()
        manga.author = infoElement.select("b:contains(Author)").first()?.nextSibling()?.toString()?.substringAfter(": ")
        manga.artist = infoElement.select("b:contains(Artist)").first()?.nextSibling()?.toString()?.substringAfter(": ")
        manga.genre = ""
        manga.description = infoElement.select("b:contains(Synopsis)").first()?.nextSibling()?.toString()?.substringAfter(": ")
        manga.status = SManga.UNKNOWN
        manga.thumbnail_url = document.select("div.thumbnail > img")?.attr("src")
        return manga
    }

    override fun chapterListSelector() = "div.list > div.group > div.element"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("div.title > a").let {
            chapter.setUrlWithoutDomain(it.attr("href"))
            chapter.name = it.text().trim()
        }
        chapter.date_upload = element.select("div.meta_r").first()?.ownText()?.substringAfterLast(", ")?.trim()?.let {
            parseChapterDate(it)
        } ?: 0L
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return if (date == "Today") {
            Calendar.getInstance().timeInMillis
        } else if (date == "Yesterday") {
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