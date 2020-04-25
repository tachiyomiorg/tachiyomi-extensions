package eu.kanade.tachiyomi.extension.zh.manhuadb

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.util.regex.Pattern
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ManhuaDB : ParsedHttpSource() {

    override val baseUrl = "https://www.manhuadb.com"

    override val lang = "zh"

    override val name = "漫画DB"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder().add("Referer", "https://www.manhuadb.com")

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.attr("title")
        url = element.attr("href")
    }

    /**
     * Rewrite the method to ensure consistency with previous format orders
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() = "#comic-book-list > div > ol > li > a"

    override fun imageUrlParse(document: Document): String {
        return document.select("div.text-center > img.img-fluid").attr("abs:src")
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1.comic-title").text()
        thumbnail_url = document.select("td.comic-cover > img").attr("abs:src")
        author = document.select("a.comic-creator").text()
        description = document.select("p.comic_story").text()
        status = when (document.select("td > a.comic-pub-state").text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = document.select("ul.tags > li a").joinToString { it.text() }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val pageStr = document.select("ol.breadcrumb > li:eq(2)").text()
        val pageNumMatcher = Pattern.compile("共\\s*(\\d+)").matcher(pageStr)
        if (pageNumMatcher.find()) {
            val page = Integer.parseInt(pageNumMatcher.group(1))
            var path = document.select("ol.breadcrumb > li:eq(2) > a").attr("href")
            path = path.substring(1, path.length - 5)
            for (i in 0 until page)
            pages.add(Page(i, "$baseUrl/${path}_p${i + 1}.html"))
        }
        return pages
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h2").first().let {
            manga.setUrlWithoutDomain(it.select("a").first().attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("a > img").attr("abs:src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "a:contains(下页):not(.disabled)"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhua/list-page-$page.html")

    override fun popularMangaSelector() = "div.comic-book-unit"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("title")
        setUrlWithoutDomain(element.attr("href"))
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&p=$page", headers)
    }

    override fun searchMangaSelector() = "a.d-block"
}
