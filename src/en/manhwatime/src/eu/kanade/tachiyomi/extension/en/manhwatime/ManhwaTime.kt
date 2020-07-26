package eu.kanade.tachiyomi.extension.en.manhwatime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class ManhwaTime : ParsedHttpSource() {

    override val name = "ManhwaTime"

    override val baseUrl = "https://manhwatime.xyz"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private fun Elements.imgAttr(): String? = this.firstOrNull()?.let {
        if (it.hasAttr("data-src")) it.attr("abs:data-src") else it.attr("abs:src")
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/?status=&type=&order=Popular&title=", headers)
    }

    override fun popularMangaSelector() = "div.post-show div.animepost"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.data > a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.select("img").imgAttr()
        }
    }

    // small catalog, can't tell what this should be at this point
    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?status=&type=&order=Latest+Update&title=", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl?s=$query", headers)
    }

    override fun searchMangaSelector() = "div.animepost a"

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
            with(document.select("div.infoanime")) {
                thumbnail_url = select("img").imgAttr()
                description = select("div.summary__content p, div[itemprop=description] p")
                    .filterNot { it.text().contains("Manhwa Synopsis", ignoreCase = true) }
                    .joinToString("\n\n") { it.text() }
                genre = select("div.genre-info a").joinToString { it.text() }
                author = select("span:contains(Author)").firstOrNull()?.ownText()
                status = select("span:contains(Status)").firstOrNull()?.ownText().toStatus()
            }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Publishing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Complete", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "div#chapter_list li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("span.lchx a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = simpleDateFormat.parse(element.select("span.date").text())?.time ?: 0
        }
    }

    private val simpleDateFormat by lazy { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-area img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
