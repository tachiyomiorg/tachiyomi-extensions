package eu.kanade.tachiyomi.extension.en.mangatown

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.model.SManga.Companion.LICENSED
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.*


class Mangatown : ParsedHttpSource() {

    override val name = "Mangatown"

    override val baseUrl = "http://mangatown.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "li:has(a.manga_cover)"
    override fun popularMangaRequest(page: Int): Request {
            return GET("$baseUrl/directory/0-0-0-0-0-0/$page.htm")
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest/$page.htm")

    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("p.title a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next:not([href^=javascript])"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search.php?name=$query"
        return POST(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.article_content")

        val manga = SManga.create()
        manga.title = infoElement.select("h1").text()
        manga.author = infoElement.select("b:containsOwn(author) + a").text()
        manga.artist = infoElement.select("b:containsOwn(artist) + a").text()

        val status = infoElement.select("li:contains(status)").first().text().substringAfter(":").split(" ").first()
        if(infoElement.select("div.chapter_content:contains(has been licensed)").isNotEmpty()) {
            manga.status = LICENSED
        } else {
            manga.status = parseStatus(status)
        }

        manga.genre = infoElement.select("li:contains(genre)").first().text().substringAfter(":")
        manga.description = document.select("span#show").text()
        manga.thumbnail_url = document.select("div.detail_info img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.chapter_list li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))

        val nameWithDate = element.text()
        val cDate = element.select("li span.time").text()
        chapter.name = nameWithDate.substringBefore(cDate)
        if (element.select("li span.new").toString().isNotEmpty()) { chapter.name = chapter.name.substringBefore("new") }
        chapter.date_upload = parseDate(cDate)
        return chapter
    }

    private fun parseDate(date: String): Long {
        when (date) {
            "Today" -> return Calendar.getInstance().apply{}.timeInMillis
            "Yesterday" -> return Calendar.getInstance().apply{add(Calendar.DAY_OF_MONTH, -1)}.timeInMillis
            else -> {
                return SimpleDateFormat("MMM d, yyyy", Locale.US).parse(date).time
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("select#top_chapter_list ~ div.page_select option:not(:contains(featured))").forEach {
            pages.add(Page(pages.size, it.attr("value").substringAfter("com")))
        }
        return pages
    }

    // Get the page
    override fun imageUrlRequest(page: Page) = GET("$baseUrl" + page.url)

   //  Get the image from the requested page
    override fun imageUrlParse (response: Response): String {
        val document = response.asJsoup()
        return document.select("div#viewer img").attr("src")
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("No used")

    override fun getFilterList() = FilterList()

}
