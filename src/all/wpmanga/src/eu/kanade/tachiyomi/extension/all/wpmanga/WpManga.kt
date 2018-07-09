package eu.kanade.tachiyomi.extension.all.wpmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*


open class WpManga(override val name: String, override val baseUrl: String, override val lang: String) : ParsedHttpSource() {

    override val supportsLatest = false

    override fun popularMangaSelector() = "div[id^=manga-item]"

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")

    override fun popularMangaNextPageSelector() = null


    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        element.select("img").first()?.let {
            manga.thumbnail_url = it.absUrl("src").substringBefore("?resize").substringBefore("?fit")
        }
        return manga
    }


    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/?s=$query&post_type=wp-manga", headers)
    }

    override fun searchMangaSelector() = "div.post-title"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        element.select("img").first()?.let {
            manga.thumbnail_url = it.absUrl("src").substringBefore("?resize").substringBefore("?fit")
        }

        return manga
    }

    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.tab-summary").first()
        val manga = SManga.create()
        manga.author = infoElement.select("div.author-content a")?.first()?.text()
        manga.artist = infoElement.select("div.artist-content a")?.first()?.text()
        manga.genre = infoElement.select("div.genres-content a")?.first()?.text()
        var genres = mutableListOf<String>()

        infoElement.select("div.genres-content a").orEmpty().forEach { id ->
            genres.add(id.text())
        }
        manga.genre = genres.joinToString(", ")
        manga.description = document.select("div.summary__content")?.first()?.text()
        manga.status = document.select("div.post-status div.post-content_item:contains(status) div.summary-content").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = document.select("div.summary_image img")?.first()?.absUrl("src")?.substringBefore("?resize")?.substringBefore("?fit")
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing", true) -> SManga.ONGOING
        status.contains("Completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.listing-chapters_wrap li.wp-manga-chapter"


    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val dateElement = element.select("span").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href") + "?style=list")
        chapter.name = urlElement.text()
        chapter.date_upload = dateElement.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    open fun parseChapterDate(date: String): Long? {
        val lcDate = date.toLowerCase()
        if (lcDate.endsWith(" ago"))
            parseRelativeDate(lcDate)?.let { return it }

        //Handle 'yesterday' and 'today', using midnight
        var relativeDate: Calendar? = null
        if (lcDate.startsWith("yesterday")) {
            relativeDate = Calendar.getInstance()
            relativeDate.add(Calendar.DAY_OF_MONTH, -1) //yesterday
            relativeDate.set(Calendar.HOUR_OF_DAY, 0)
            relativeDate.set(Calendar.MINUTE, 0)
            relativeDate.set(Calendar.SECOND, 0)
            relativeDate.set(Calendar.MILLISECOND, 0)
        } else if (lcDate.startsWith("today")) {
            relativeDate = Calendar.getInstance()
            relativeDate.set(Calendar.HOUR_OF_DAY, 0)
            relativeDate.set(Calendar.MINUTE, 0)
            relativeDate.set(Calendar.SECOND, 0)
            relativeDate.set(Calendar.MILLISECOND, 0)
        }

        relativeDate?.timeInMillis?.let {
            return it
        }

        return DATE_FORMAT_1.parse(date).time

    }

    /**
     * Parses dates in this form:
     * `11 days ago`
     */
    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.split(" ")

        if (trimmedDate[2] != "ago") return null

        val number = trimmedDate[0].toIntOrNull() ?: return null
        val unit = trimmedDate[1].removeSuffix("s") // Remove 's' suffix

        val now = Calendar.getInstance()

        // Map English unit to Java unit
        val javaUnit = when (unit) {
            "year", "yr" -> Calendar.YEAR
            "month" -> Calendar.MONTH
            "week", "wk" -> Calendar.WEEK_OF_MONTH
            "day" -> Calendar.DAY_OF_MONTH
            "hour", "hr" -> Calendar.HOUR
            "minute", "min" -> Calendar.MINUTE
            "second", "sec" -> Calendar.SECOND
            else -> return null
        }

        now.add(javaUnit, -number)

        return now.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        val doc = document.select("div.page-break img");

        val pages = mutableListOf<Page>()
        doc.forEach {
            // Create dummy element to resolve relative URL
            val absUrl = it.select("img").attr("src")
            pages.add(Page(pages.size, "", absUrl))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    companion object {

        private val DATE_FORMAT_1 = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    }
}