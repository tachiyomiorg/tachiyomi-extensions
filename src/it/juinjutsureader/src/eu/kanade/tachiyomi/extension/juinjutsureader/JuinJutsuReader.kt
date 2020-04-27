package eu.kanade.tachiyomi.extension.it.juinjutsureader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class JuinJutsuReader : ParsedHttpSource() {
    override val name = "Juin Jutsu Team Reader"

    override val baseUrl = "https://www.juinjutsureader.ovh"

    override val lang = "it"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = "div.series_element"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/directory/$page", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").first().attr("href"))
        title = element.select("a[title]").first().attr("title")
        thumbnail_url = element.select("a > img").attr("src")
    }

    override fun popularMangaNextPageSelector() = ".next"

    override fun latestUpdatesSelector() = "div.title_manga > a"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest/$page", headers)

    //This page has no thumbnails
    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    //Following search related code was all taken from the GenkanOriginal class in Genkan.kt
    private var searchQuery = ""
    private var searchPage = 1
    private var nextPageSelectorElement = Elements()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) searchPage = 1
        searchQuery = query
        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchMatches = mutableListOf<SManga>()
        val document = response.asJsoup()
        searchMatches.addAll(getMatchesFrom(document))
        nextPageSelectorElement = document.select(searchMangaNextPageSelector())
        while (nextPageSelectorElement.hasText()) {
            searchMatches.addAll(searchMorePages())
        }

        return MangasPage(searchMatches, false)
    }

    private fun getMatchesFrom(document: Document): MutableList<SManga> {
        val searchMatches = mutableListOf<SManga>()
        document.select(searchMangaSelector())
            .filter { it.text().contains(searchQuery, ignoreCase = true) }
            .map { searchMatches.add(searchMangaFromElement(it)) }

        return searchMatches
    }

    private fun searchMorePages(): MutableList<SManga> {
        searchPage++
        val nextPage = client.newCall(popularMangaRequest(searchPage)).execute().asJsoup()
        val searchMatches = mutableListOf<SManga>()
        searchMatches.addAll(getMatchesFrom(nextPage))
        nextPageSelectorElement = nextPage.select(searchMangaNextPageSelector())

        return searchMatches
    }

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select(".autore").first().ownText().removePrefix(": ")
        description = document.select(".trama").first().ownText().removePrefix(": ")
        thumbnail_url = document.select("img.thumb").attr("src")
    }

    override fun chapterListSelector() = "div.element"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").attr("title")
        date_upload = dateFormat.parse(element.select(".meta_r").text()).time ?: 0
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("yyyy.MM.dd")
        }
    }

    private fun pageListSelector() = "a[href*=page]:not([onclick])"

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select(pageListSelector()).mapIndexed { i, element ->
            val doc = client.newCall(GET(element.attr("abs:href"), headers)).execute().asJsoup()
            pages.add(Page(i, "", doc.select("img.open.open_image").attr("src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
