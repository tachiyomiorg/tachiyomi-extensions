package eu.kanade.tachiyomi.extension.id.nyanfm

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class NyanFM : ParsedHttpSource() {

    override val name = "Nyan FM"
    override val baseUrl = "https://nyanfm.com"
    override val lang = "id"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/scanlation/", headers)
    }

    override fun popularMangaSelector() = ".elementor-column .elementor-widget-wrap .elementor-widget-container .elementor-cta:has(.elementor-cta__bg)"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".elementor-cta__bg-wrapper .elementor-cta__bg").attr("style").substringAfter("(").substringBefore(")")

        manga.url = element.select(".elementor-cta__content a.elementor-button").attr("href").substringAfter(".com")
        manga.title = element.select(".elementor-cta__content h2").text().trim()
        return manga
    }

    override fun popularMangaNextPageSelector() = "#nav-below .nav-links .next.page-numbers"

    // latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("https://nyanfm.com/page/$page/?s=$query")
    }

    override fun searchMangaSelector() = "#main article .inside-article:has(.entry-summary:has(a))"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".post-image img").attr("src")
        element.select(".entry-summary:has(p) a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaNextPageSelector() = ".nav-links .next"

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select(".entry-content div.elementor-section-wrap").firstOrNull()?.let { infoElement ->
                thumbnail_url = infoElement.select("div.elementor-widget-wrap div.elementor-element.elementor-hidden-tablet.elementor-widget img").attr("src")
                title = infoElement.select("h1").text().trim()
                artist = infoElement.select("div.elementor-element:has(h3:contains(author)) + div.elementor-element p").firstOrNull()?.ownText()
                status = parseStatus(infoElement.select("div.elementor-element:has(h3:contains(status)) + div.elementor-element p").text())
                genre = infoElement.select("div.elementor-element:has(h3:contains(genre)) + div.elementor-element p").joinToString(", ") { it.text() }
                description = infoElement.select("div.elementor-element:has(h3:contains(sinopsis)) + div.elementor-element p").joinToString("\n") { it.text() }
            }
        }
    }

    private fun parseStatus(element: String): Int = when {
        element.toLowerCase().contains("ongoing") -> SManga.ONGOING
        element.toLowerCase().contains("completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListSelector() = "table tbody tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.select("td:last-child a").let {
            name = it.text()
            url = it.attr("href").substringAfter(baseUrl)
        }
        date_upload = parseChapterDate(element.select("td:first-child").text())
    }

    private fun parseChapterDate(date: String): Long {
        return SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).parse(date)?.time ?: 0L
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div.elementor-widget-container > div.elementor-image a > img.attachment-large.size-large").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""
}
