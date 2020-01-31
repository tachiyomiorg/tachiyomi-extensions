package eu.kanade.tachiyomi.extension.en.sleepypandascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class SleepyPandaScans : ParsedHttpSource() {

    override val name = "Sleepy Panda Scans"

    override val baseUrl = "http://sleepypandascans.co"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/Series", headers)
    }

    override fun popularMangaSelector() = "div.card.card-cascade"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.select("a").attr("href")
            title = element.select("h6").text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl)
    }

    // Only add manga to MangasPage if its title is not one we've added already
    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(latestUpdatesSelector())
            .distinctBy { it.select(".card-title").text() }
            .map { latestUpdatesFromElement(it) }

        return MangasPage(mangas, false)
    }

    // Updates less than a day old are patron only, ignore them
    override fun latestUpdatesSelector() = "div.card.card-cascade:not(div.amber)"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.select("a").attr("href")
                .replace("Reader", "Series").substringBeforeLast("/")
            title = element.select("h5").text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    // Source's website doesn't appear to have a search function; so searching locally
    private lateinit var searchQuery: String

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        searchQuery = query
        return popularMangaRequest(1)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(searchMangaSelector())
            .filter { it.text().contains(searchQuery, ignoreCase = true) }
            .map { searchMangaFromElement(it) }

        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.card-body")

        return SManga.create().apply {
            title = infoElement.select("h4").text()
            description = infoElement.select("p").text()
            thumbnail_url = client.newCall(popularMangaRequest(1)).execute().asJsoup().select(popularMangaSelector())
                .first { it.select(".card-title").text() == title }?.select("img")?.attr("abs:src")
        }
    }

    // Chapters

    // Chapters less than a day old are patron only, ignore them
    override fun chapterListSelector() = "div.list-group a:not(a[data-target])"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            url = element.attr("href")
            name = element.ownText()
            date_upload = parseRelativeDate(element.select("span").text().substringBefore(" ago"))
        }
    }

    // Subtract relative date (e.g. posted 3 days ago)
    private fun parseRelativeDate(date: String): Long {
        val calendar = Calendar.getInstance()

        if (date.contains("yesterday")) {
            calendar.apply{add(Calendar.DAY_OF_MONTH, -1)}
        } else {
            val trimmedDate = date.replace("one", "1").removeSuffix("s").split(" ")

            when (trimmedDate[1]) {
                "year" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
                "month" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
                "week" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
                "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            }
        }

        return calendar.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.view img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
