package eu.kanade.tachiyomi.extension.en.mangaowl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaOwl : ParsedHttpSource() {

    override val name = "MangaOwl"

    override val baseUrl = "https://mangaowl.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular/$page", headers)
    }

    override fun popularMangaSelector() = "div.col-md-2"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h6 a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("div.img-responsive").attr("abs:data-background-image")

        return manga
    }

    override fun popularMangaNextPageSelector() = "div.blog-pagenat-wthree li a:contains(>>)"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/lastest/$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/$query/$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "div.navigation li a:contains(next)"

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.single_detail").first()

        return SManga.create().apply {
            title = infoElement.select("h2").first().ownText()
            author = infoElement.select("p:contains(author) a").text()
            artist = author
            status = parseStatus(infoElement.select("p:contains(pub. status)").first().ownText())
            genre = infoElement.select("a.label").mapNotNull { it.text() }.joinToString(", ")
            description = infoElement.select("div.single-right-grids.description").first().ownText()
            thumbnail_url = infoElement.select("img").first()?.let { img ->
                if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")
            }
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "div.table-chapter-list ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("a").let {
            // They replace some URLs with a different host getting a path of domain.com/reader/reader/, fix to make usable on baseUrl
            chapter.setUrlWithoutDomain(it.attr("href").replace("/reader/reader/", "/reader/"))
            chapter.name = it.select("label")[0].text()
        }
        chapter.date_upload = parseChapterDate(element.select("small").text())

        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MM/dd/yyyy", Locale.US)
        }
    }

    private fun parseChapterDate(string: String): Long {
        return dateFormat.parse(string).time
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.item img.owl-lazy").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
