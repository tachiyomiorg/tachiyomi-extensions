package eu.kanade.tachiyomi.extension.en.xoxocomics

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
import okhttp3.Headers
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import rx.Observable

class XoXoComics: ParsedHttpSource() {

    override val name = "XOXOComics"

    override val baseUrl = "https://xoxocomics.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.image > a"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/popular-comics?page=$page", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
        thumbnail_url = element.select("img").attr("data-original")
    }

    override fun popularMangaNextPageSelector() = "[rel=next]"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/new-comics?page=$page", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/search?keyword=$query&page=$page", headers)

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select("li.author.row a").text()
        description = document.select("div.detail-content p").text()
        genre = document.select("li.kind.row a").joinToString { it.text() }
        status = document.select("li.status.row p.col-xs-8").text().let {
            parseStatus(it)
        }
        title = document.select("div.mrt10 > a > span[itemprop=name]").text()
        thumbnail_url = document.select("div.detail-info img").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "li.row:has(div.col-xs-9.chapter > a)"

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        var continueParsing = true

        while (continueParsing) {
            document.select(chapterListSelector()).map{ chapters.add(chapterFromElement(it)) }
            document.select(popularMangaNextPageSelector()).let{
                if (it.isNotEmpty()) {
                    document = client.newCall(GET(it.attr("abs:href"), headers)).execute().asJsoup()
                } else {
                    continueParsing = false
                }
            }
        }
        return chapters
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").text()
        date_upload = dateFormat.parse(element.select("div.col-xs-3.text-center").text()).time ?: 0
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd/MM/yyyy")
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.page-chapter > img").mapIndexed { i, element ->
            Page(i, "", element.attr("data-original"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}

