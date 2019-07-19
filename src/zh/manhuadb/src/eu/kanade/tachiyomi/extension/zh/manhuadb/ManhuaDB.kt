package eu.kanade.tachiyomi.extension.zh.manhuadb

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

class ManhuaDB: ParsedHttpSource() {

    override val baseUrl: String
        get() = "https://www.manhuadb.com"
    override val lang: String
        get() = "zh"
    override val name: String
        get() = "漫画DB"
    override val supportsLatest: Boolean
        get() = true

    override fun headersBuilder(): Headers.Builder
        = super.headersBuilder().add("Referer", "https://www.manhuadb.com")

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.attr("title")
        url = element.attr("href")
    }

    /**
     * Rewrite the method to ensure consistency with previous format orders
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }.reversed()
    }

    override fun chapterListSelector(): String = "#comic-book-list > div > ol > li > a"

    override fun imageUrlParse(document: Document): String {
        val path = document.select("div.text-center > img.img-fluid").attr("src")
        return "$baseUrl$path"
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    /**
     * Rewrite the method to fit the next page selector
     */
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPageElement = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        }

        val hasNextPage = !(hasNextPageElement?.attr("class")?.contains("disabled") ?: false)

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1.comic-title").text()
        val path = document.select("td.comic-cover > img").attr("src")
        thumbnail_url = if (!path.startsWith("http")) {
            "$baseUrl$path"
        } else {
            path
        }
        author = document.select("a.comic-creator").text()
        description = document.select("p.comic_story").text()
        status = when (document.select("td > a.comic-pub-state").text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val genres = document.select("ul.tags > li").map {
            it.text()
        }
        genre = genres.joinToString(", ")
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
            pages.add(Page(i, "$baseUrl/${path}_p${i+1}.html"))
        }
        return pages
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h2").first().let {
            manga.setUrlWithoutDomain(it.select("a").first().attr("href"))
            manga.title = it.text()
        }
        element.select("a > img").first().let {
            manga.thumbnail_url = it.attr("src")
        }
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "div.form-inline > a:contains(下页)"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhua/list-page-$page.html")

    /**
     * Rewrite the method to fit the next page selector
     */
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPageElement = popularMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        }

        val hasNextPage = !(hasNextPageElement?.attr("class")?.contains("disabled") ?: false)
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaSelector(): String = "div.comic-book-unit"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("title")
        setUrlWithoutDomain(element.attr("href"))
        val path = element.select("img").attr("src")
        thumbnail_url = if (!path.startsWith("http")) {
            "$baseUrl$path"
        } else {
            path
        }
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    /**
     * Rewrite the method to fit the next page selector
     */
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPageElement = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        }

        val hasNextPage = !(hasNextPageElement?.attr("class")?.contains("disabled") ?: false)

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&p=$page", headers)
    }

    override fun searchMangaSelector(): String = "a.d-block"
}