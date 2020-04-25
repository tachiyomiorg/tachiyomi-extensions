package eu.kanade.tachiyomi.extension.all.genkan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
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

abstract class Genkan(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.list-item"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/comics?page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    // Track which manga titles have been added to latestUpdates's MangasPage
    private val latestUpdatesTitles = mutableSetOf<String>()

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) latestUpdatesTitles.clear()
        return GET("$baseUrl/latest?page=$page", headers)
    }

    // To prevent dupes, only add manga to MangasPage if its title is not one we've added already
    override fun latestUpdatesParse(response: Response): MangasPage {
        val latestManga = mutableListOf<SManga>()
        val document = response.asJsoup()

        document.select(latestUpdatesSelector()).forEach { element ->
            latestUpdatesFromElement(element).let { manga ->
                if (manga.title !in latestUpdatesTitles) {
                    latestManga.add(manga)
                    latestUpdatesTitles.add(manga.title)
                }
            }
        }

        return MangasPage(latestManga, document.select(latestUpdatesNextPageSelector()).hasText())
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.list-title").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = styleToUrl(element.select("a.media-content").first())
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "[rel=next]"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/comics?query=$query", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    private fun styleToUrl(element: Element): String {
        return element.attr("style").substringAfter("(").substringBefore(")")
            .let { if (it.startsWith("http")) it else baseUrl + it }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("div#content h5").first().text()
            description = document.select("div.col-lg-9").text().substringAfter("Description ").substringBefore(" Volume")
            thumbnail_url = styleToUrl(document.select("div.media a").first())
        }
    }

    override fun chapterListSelector() = "div.col-lg-9 div.flex"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {

            val urlElement = element.select("a.item-author")
            val chapNum = urlElement.attr("href").split("/").last()

            setUrlWithoutDomain(urlElement.attr("href"))
            name = if (urlElement.text().contains("Chapter $chapNum")) {
                urlElement.text()
            } else {
                "Ch. $chapNum: ${urlElement.text()}"
            }
            date_upload = parseChapterDate(element.select("a.item-company").first().text()) ?: 0
        }
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM d, yyyy", Locale.US)
        }
    }

    // If the date string contains the word "ago" send it off for relative date parsing otherwise use dateFormat
    private fun parseChapterDate(string: String): Long? {
        return if ("ago" in string) {
            parseRelativeDate(string) ?: 0
        } else {
            dateFormat.parse(string).time
        }
    }

    // Subtract relative date (e.g. posted 3 days ago)
    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.substringBefore(" ago").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "month" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
            "week" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
            "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "minute" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "second" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val allImages = document.select("div#pages-container + script").first().data()
            .substringAfter("[").substringBefore("];")
            .replace(Regex("""["\\]"""), "")
            .split(",")

        for (i in allImages.indices) {
            pages.add(Page(i, "", allImages[i]))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun imageRequest(page: Page): Request {
        return if (page.imageUrl!!.startsWith("http")) GET(page.imageUrl!!, headers) else GET(baseUrl + page.imageUrl!!, headers)
    }

    override fun getFilterList() = FilterList()
}

// For sites using the older Genkan CMS that didn't have a search function

abstract class GenkanOriginal(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : Genkan(name, baseUrl, lang) {

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

        /* call another function if there's more pages to search
           not doing it this way can lead to a false "no results found"
           if no matches are found on the first page but there are matcheszz
           on subsequent pages */
        nextPageSelectorElement = document.select(searchMangaNextPageSelector())
        while (nextPageSelectorElement.hasText()) {
            searchMatches.addAll(searchMorePages())
        }

        return MangasPage(searchMatches, false)
    }

    // search the given document for matches
    private fun getMatchesFrom(document: Document): MutableList<SManga> {
        val searchMatches = mutableListOf<SManga>()
        document.select(searchMangaSelector())
            .filter { it.text().contains(searchQuery, ignoreCase = true) }
            .map { searchMatches.add(searchMangaFromElement(it)) }

        return searchMatches
    }

    // search additional pages if called
    private fun searchMorePages(): MutableList<SManga> {
        searchPage++
        val nextPage = client.newCall(popularMangaRequest(searchPage)).execute().asJsoup()
        val searchMatches = mutableListOf<SManga>()
        searchMatches.addAll(getMatchesFrom(nextPage))
        nextPageSelectorElement = nextPage.select(searchMangaNextPageSelector())

        return searchMatches
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
}
