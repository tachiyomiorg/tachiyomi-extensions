package eu.kanade.tachiyomi.extension.en.leviatanscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

open class LeviatanScans(
        override val name: String,
        override val baseUrl: String,
        override val lang: String
) : ParsedHttpSource() {

    override val supportsLatest = true

    // Popular Manga

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga", headers)

    override fun popularMangaSelector() = "div.page-item-detail.manga"

    override fun popularMangaNextPageSelector() = NO_SELECTOR

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select("div.post-title.font-title a").first().let {
                manga.setUrlWithoutDomain(it.attr("href"))
                manga.title = it.ownText()
            }

            select("img").first()?.let {
                manga.thumbnail_url = it.absUrl("src").replace("110x150", "193x278")
            }
        }
        return manga
    }

    // Latest Updates

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl)

    override fun latestUpdatesSelector() = "div.item__wrap"

    override fun latestUpdatesNextPageSelector() = NO_SELECTOR

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mp = super.latestUpdatesParse(response)
        val mangas = mp.mangas.distinctBy { it.url }
        return MangasPage(mangas, mp.hasNextPage)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        // Even if it's different from the popular manga's list, the relevant classes are the same
        return popularMangaFromElement(element)
    }

    // Search Manga

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/?s=$query", headers)

    // Just presume the result is like the popular manga page.
    // Actually it never finds a manga so I don't know how it should look like.
    override fun searchMangaSelector() = "div.page-item-detail.manga"

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = NO_SELECTOR

    // Manga Details Parse

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        with(document) {
            select("div.post-title h3").first()?.let {
                manga.title = it.ownText()
            }
            select("div.author-content").first()?.let {
                manga.author = it.text()
            }
            select("div.artist-content").first()?.let {
                manga.artist = it.text()
            }
            select("div.description-summary div.summary__content p").let {
                manga.description = it.joinToString(separator = "\n\n") { p ->
                    p.text().replace("<br>", "\n")
                }
            }
            select("div.summary_image img").first()?.let {
                manga.thumbnail_url = it.absUrl("src")
            }
        }

        return manga
    }

    override fun chapterListSelector() = "div.listing-chapters_wrap li.wp-manga-chapter"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            select("a").first()?.let { urlElement ->
                chapter.setUrlWithoutDomain(urlElement.attr("href"))
                chapter.name = urlElement.text()
            }

            select("span.chapter-release-date i").first()?.let {
                chapter.date_upload = parseChapterDate(it.text()) ?: 0
            }
        }

        return chapter
    }

    open fun parseChapterDate(date: String): Long? {
        val lcDate = date.toLowerCase()
        if (lcDate.endsWith(" ago"))
            parseRelativeDate(lcDate)?.let { return it }

        //Handle 'yesterday' and 'today', using midnight
        if (lcDate.startsWith("ayer"))
            return Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -1) //yesterday
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        return DATE_FORMAT.parseOrNull(date)?.time
    }

    // Parses dates in this form:
    // 21 horas ago
    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.split(" ")

        if (trimmedDate[2] != "ago") return null

        val number = trimmedDate[0].toIntOrNull() ?: return null
        val unit = trimmedDate[1]

        val now = Calendar.getInstance()

        // Map Spanish unit to Java unit
        val javaUnit = when (unit) {
            "año", "años" -> Calendar.YEAR
            "mes", "meses" -> Calendar.MONTH
            "semana", "semanas" -> Calendar.WEEK_OF_MONTH
            "día", "días" -> Calendar.DAY_OF_MONTH
            "hora", "horas" -> Calendar.HOUR
            "minuto", "minutos" -> Calendar.MINUTE
            "segundo", "segundos" -> Calendar.SECOND
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

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reading-content div.page-break.no-gaps").mapIndexed { index, element ->
            Page(index, "", element.select("img").first()?.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    companion object {
        private const val NO_SELECTOR = "NoSelectorABCDEFGH" // I hope this doesn't match anything
        private val DATE_FORMAT = SimpleDateFormat("MMMM dd, yy", Locale("es", "ES"))
    }
}