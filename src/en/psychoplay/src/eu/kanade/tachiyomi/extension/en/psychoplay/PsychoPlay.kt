package eu.kanade.tachiyomi.extension.en.psychoplay

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class PsychoPlay : ParsedHttpSource() {

    override val name = "PsychoPlay"

    override val baseUrl = "https://psychoplay.co"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.thumbnail"

    override fun popularMangaRequest(page: Int): Request {
            return GET("$baseUrl/series?page=$page")
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
            return GET("$baseUrl/latest?page=$page")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h6 a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = baseUrl + element.select("img").first().attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = ":containsOwn(next):not([href=\"\"])"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series?q=$query"
        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div.thumbnail"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.row").first()

        val manga = SManga.create()
        manga.title = infoElement.select("h2").first().text()

        manga.description = document.select("div.panel-body").text().substringAfter("Synopsis ")
        manga.thumbnail_url = baseUrl + document.select("div.media-left a").first().select("img").first().attr("src")
        return manga
    }

    override fun chapterListSelector() = "li.media"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text().substringBefore("Added")
        chapter.date_upload = parseChapterDate(urlElement.text().substringAfter("Added ")) ?: 0
        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM d, yyyy")
        }
    }

    // If the date string contains the word "on" simply dateformat it, otherwise send it off to parse relatively
    private fun parseChapterDate(string: String): Long? {
        if ("on " in string) {
            return dateFormat.parse(string.substringAfter("on ")).time
        } else {
            return parseRelativeDate(string) ?: 0
        }
    }

    // Subtract relative date (e.g. posted 3 days ago)
    private fun parseRelativeDate(date: String): Long? {
        var trimmedDate = date.substringBefore(" ago").replace(",", " ").split(" ")

        val calendar = Calendar.getInstance()
        var numIndex = -1
        trimmedDate.forEach {
            when (it){
                "month", "months" -> calendar.apply{add(Calendar.MONTH, -trimmedDate.get(numIndex).toInt())}
                "week", "weeks" -> calendar.apply{add(Calendar.WEEK_OF_MONTH, -trimmedDate.get(numIndex).toInt())}
                "day", "days" -> calendar.apply{add(Calendar.DAY_OF_MONTH, -trimmedDate.get(numIndex).toInt())}
                "hour", "hours" -> calendar.apply{add(Calendar.HOUR_OF_DAY, -trimmedDate.get(numIndex).toInt())}
                "minute", "minutes" -> calendar.apply{add(Calendar.MONTH, -trimmedDate.get(numIndex).toInt())}
                "second", "seconds" -> calendar.apply{add(Calendar.SECOND, 0)}
            }
            numIndex++
        }

        return calendar.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.row img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("No used")

    override fun getFilterList() = FilterList()

}
