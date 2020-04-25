package eu.kanade.tachiyomi.extension.all.wpcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class WPComics(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm - dd/MM/yyyy Z", Locale.US),
    private val gmtOffset: String? = "+0500"
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private fun List<String>.doesInclude(thisWord: String): Boolean = this.any { it.contains(thisWord, ignoreCase = true) }

    // Popular

    open val popularPath = "hot"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$popularPath" + if (page > 1) "?page=$page" else "", headers)
    }

    override fun popularMangaSelector() = "div.items div.item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            thumbnail_url = imageOrNull(element.select("div.image:first-of-type img").first())
        }
    }

    override fun popularMangaNextPageSelector() = "a.next-page, a[rel=next]"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl + if (page > 1) "?page=$page" else "", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/?s=$query&post_type=comics&page=$page")
    }

    override fun searchMangaSelector() = "div.items div.item div.image a"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.attr("title")
            setUrlWithoutDomain(element.attr("href"))
            thumbnail_url = imageOrNull(element.select("img").first())
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("article#item-detail").let { info ->
                author = info.select("li.author p.col-xs-8").text()
                status = info.select("li.status p.col-xs-8").text().toStatus()
                genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }
                description = info.select("div.detail-content p").text()
                thumbnail_url = imageOrNull(info.select("div.col-image img").first())
            }
        }
    }

    private fun String?.toStatus(): Int {
        val ongoingWords = listOf("Ongoing", "Updating", "Đang tiến hành")
        val completedWords = listOf("Complete", "Hoàn thành")
        return when {
            this == null -> SManga.UNKNOWN
            ongoingWords.doesInclude(this) -> SManga.ONGOING
            completedWords.doesInclude(this) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapters

    override fun chapterListSelector() = "div.list-chapter li.row:not(.heading)"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = element.select("div.col-xs-4").text().toDate()
        }
    }

    private val currentYear by lazy { Calendar.getInstance(Locale.US)[1].toString().takeLast(2) }

    private fun String?.toDate(): Long {
        this ?: return 0

        val secondWords = listOf("second", "giây")
        val minuteWords = listOf("minute", "phút")
        val hourWords = listOf("hour", "giờ")
        val dayWords = listOf("day", "ngày")
        val agoWords = listOf("ago", "trước")

        return try {
            if (agoWords.any { this.contains(it, ignoreCase = true) }) {
                val trimmedDate = this.substringBefore(" ago").removeSuffix("s").split(" ")
                val calendar = Calendar.getInstance()

                when {
                    dayWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
                    hourWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
                    minuteWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
                    secondWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.SECOND, -trimmedDate[0].toInt()) }
                }

                calendar.timeInMillis
            } else {
                (if (gmtOffset == null) this.substringAfterLast(" ") else "$this $gmtOffset").let {
                    // timestamp has year
                    if (Regex("""\d+/\d+/\d\d""").find(it)?.value != null) {
                        dateFormat.parse(it).time
                    } else {
                        // MangaSum - timestamp sometimes doesn't have year (current year implied)
                        dateFormat.parse("$it/$currentYear").time
                    }
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    // Pages

    // sources sometimes have an image element with an empty attr that isn't really an image
    private fun imageOrNull(element: Element): String? {
        fun Element.hasValidAttr(attr: String): Boolean {
            val regex = Regex("""https?://.*""", RegexOption.IGNORE_CASE)
            return when {
                this.attr(attr).isNullOrBlank() -> false
                this.attr("abs:$attr").matches(regex) -> true
                else -> false
            }
        }

        return when {
            element.hasValidAttr("data-original") -> element.attr("abs:data-original")
            element.hasValidAttr("data-src") -> element.attr("abs:data-src")
            element.hasValidAttr("src") -> element.attr("abs:src")
            else -> null
        }
    }

    open val pageListSelector = "div.page-chapter > img, li.blocks-gallery-item img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListSelector).mapNotNull { img -> imageOrNull(img) }
            .distinct()
            .mapIndexed { i, image -> Page(i, "", image) }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
