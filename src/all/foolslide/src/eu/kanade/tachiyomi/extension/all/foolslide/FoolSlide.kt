package eu.kanade.tachiyomi.extension.all.foolslide

import com.github.salomonbrys.kotson.get
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.HashSet
import java.util.Locale

abstract class FoolSlide(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    val urlModifier: String = ""
) : ParsedHttpSource() {

    protected open val dedupeLatestUpdates = true

    override val supportsLatest = true

    override fun popularMangaSelector() = "div.group"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$urlModifier/directory/$page/", headers)
    }

    private val latestUpdatesUrls = HashSet<String>()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mp = super.latestUpdatesParse(response)
        return if (dedupeLatestUpdates) {
            val mangas = mp.mangas.distinctBy { it.url }.filterNot { latestUpdatesUrls.contains(it.url) }
            latestUpdatesUrls.addAll(mangas.map { it.url })
            MangasPage(mangas, mp.hasNextPage)
        } else mp
    }

    override fun latestUpdatesSelector() = "div.group"

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) {
            latestUpdatesUrls.clear()
        }
        return GET("$baseUrl$urlModifier/latest/$page/")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("a[title]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }

        element.select("img").first()?.let {
            manga.thumbnail_url = it.absUrl("src").replace("/thumb_", "/")
        }

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a[title]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.next"

    override fun latestUpdatesNextPageSelector() = "div.next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("search", query)

        return POST("$baseUrl$urlModifier/search/", headers, form.build())
    }

    override fun searchMangaSelector() = "div.group"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a[title]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun searchMangaNextPageSelector() = "a:has(span.next)"

    override fun mangaDetailsRequest(manga: SManga) = allowAdult(super.mangaDetailsRequest(manga))

    open val mangaDetailsInfoSelector = "div.info"
    open val mangaDetailsThumbnailSelector = "div.thumbnail img"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(mangaDetailsInfoSelector).first().text()

        val manga = SManga.create()
        manga.author = infoElement.substringAfter("Author:").substringBefore("Artist:")
        manga.artist = infoElement.substringAfter("Artist:").substringBefore("Synopsis:")
        manga.description = infoElement.substringAfter("Synopsis:")
        manga.thumbnail_url = document.select(mangaDetailsThumbnailSelector).first()?.absUrl("src")

        return manga
    }

    /**
     * Transform a GET request into a POST request that automatically authorizes all adult content
     */
    private fun allowAdult(request: Request) = allowAdult(request.url().toString())

    private fun allowAdult(url: String): Request {
        return POST(url, body = FormBody.Builder()
            .add("adult", "true")
            .build())
    }

    override fun chapterListRequest(manga: SManga) = allowAdult(super.chapterListRequest(manga))

    override fun chapterListSelector() = "div.group div.element, div.list div.element"

    open val chapterDateSelector = "div.meta_r"

    open val chapterUrlSelector = "a[title]"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(chapterUrlSelector).first()
        val dateElement = element.select(chapterDateSelector).first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = dateElement.text()?.let { parseChapterDate(it.substringAfter(", ")) }
            ?: 0
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
        } else if (lcDate.startsWith("tomorrow")) {
            relativeDate = Calendar.getInstance()
            relativeDate.add(Calendar.DAY_OF_MONTH, +1) //tomorrow
            relativeDate.set(Calendar.HOUR_OF_DAY, 0)
            relativeDate.set(Calendar.MINUTE, 0)
            relativeDate.set(Calendar.SECOND, 0)
            relativeDate.set(Calendar.MILLISECOND, 0)
        }

        relativeDate?.timeInMillis?.let {
            return it
        }

        var result = DATE_FORMAT_1.parseOrNull(date)

        for (dateFormat in DATE_FORMATS_WITH_ORDINAL_SUFFIXES) {
            if (result == null)
                result = dateFormat.parseOrNull(date)
            else
                break
        }

        for (dateFormat in DATE_FORMATS_WITH_ORDINAL_SUFFIXES_NO_YEAR) {
            if (result == null) {
                result = dateFormat.parseOrNull(date)

                if (result != null) {
                    // Result parsed but no year, copy current year over
                    result = Calendar.getInstance().apply {
                        time = result
                        set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                    }.time
                }
            } else break
        }

        return result?.time ?: 0L
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

    private fun SimpleDateFormat.parseOrNull(string: String): Date? {
        return try {
            parse(string)
        } catch (e: ParseException) {
            null
        }
    }

    override fun pageListRequest(chapter: SChapter) = allowAdult(super.pageListRequest(chapter))

    override fun pageListParse(document: Document): List<Page> {
        val doc = document.toString()
        val jsonstr = doc.substringAfter("var pages = ").substringBefore(";")
        val json = JsonParser().parse(jsonstr).asJsonArray
        val pages = mutableListOf<Page>()
        json.forEach {
            // Create dummy element to resolve relative URL
            val absUrl = document.createElement("a")
                .attr("href", it["url"].asString)
                .absUrl("href")

            pages.add(Page(pages.size, "", absUrl))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    companion object {
        private val ORDINAL_SUFFIXES = listOf("st", "nd", "rd", "th")
        private val DATE_FORMAT_1 = SimpleDateFormat("yyyy.MM.dd", Locale.US)
        private val DATE_FORMATS_WITH_ORDINAL_SUFFIXES = ORDINAL_SUFFIXES.map {
            SimpleDateFormat("dd'$it' MMMM, yyyy", Locale.US)
        }
        private val DATE_FORMATS_WITH_ORDINAL_SUFFIXES_NO_YEAR = ORDINAL_SUFFIXES.map {
            SimpleDateFormat("dd'$it' MMMM", Locale.US)
        }
    }
}
