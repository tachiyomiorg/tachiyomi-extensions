package eu.kanade.tachiyomi.extension.all.wpcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Small catalog at time of extension's creation, can't be sure how $page will be used in requests
 * Will have to add that later if their catalog grows
 */

abstract class WPComics(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm - dd/MM/yyyy Z", Locale.US),
    private val gmtOffset: String? = "+0500"
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/hot/", headers)
    }

    override fun popularMangaSelector() = "div.items div.item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            thumbnail_url = element.select("div.image:first-of-type img").attr("abs:src")

        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/?s=$query&post_type=comics")
    }

    override fun searchMangaSelector() = "div.items div.item div.image a"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.attr("title")
            setUrlWithoutDomain(element.attr("href"))
            thumbnail_url = element.select("img").attr("abs:data-src")
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
                thumbnail_url = info.select("div.col-image img").let {
                    if (it.hasAttr("data-src")) it.attr("abs:data-src") else it.attr("abs:src")
                }
            }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Updating", ignoreCase = true) -> SManga.ONGOING
        this.contains("Complete", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
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

    private fun String?.toDate(): Long {
        return try {
            dateFormat.parse(this + if (gmtOffset != null) " $gmtOffset" else "").time
        } catch (_: Exception) {
            0L
        }
    }

    // Pages

    private fun imageOrNull(element: Element): String? {
        return when {
            element.attr("data-original").contains(Regex("""\.(jpg|png)""", RegexOption.IGNORE_CASE)) -> element.attr("abs:data-original")
            element.attr("data-src").contains(Regex("""\.(jpg|png)""", RegexOption.IGNORE_CASE)) -> element.attr("abs:data-src")
            element.attr("src").contains(Regex("""\.(jpg|png)""", RegexOption.IGNORE_CASE)) -> element.attr("abs:src")
            else -> null
        }
    }

    open val pageListSelector = "div.page-chapter > img, li.blocks-gallery-item img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListSelector).mapIndexed { i, img -> (Page(i, "", imageOrNull(img))) }
            .filterNot { it.imageUrl == null }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
