package eu.kanade.tachiyomi.extension.ar.mangazen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaZen : ParsedHttpSource() {

    override val baseUrl = "https://manga-zen.com"

    override val lang = "ar"

    override val name = "MangaZen"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/قائمة-المانجا/page/$page", headers)

    override fun popularMangaSelector() = "a[title][alt]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
        thumbnail_url = element.select("img").attr("abs:src").substringBeforeLast("?quality")
    }

    override fun popularMangaNextPageSelector() = "div.pagination:not(:has(span:last-child))"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/قائمة-المانجا/page/$page/?order=update", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/قائمة-المانجا/page/$page/?title=$query", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select("div.desc").text()
        genre = document.select("div.genre-info > a[itemprop=genre]").joinToString { it.text() }
        status = parseStatus(document.select("div.spe").first().text())
    }

    private fun parseStatus(status: String) = when {
        status.contains("مستمر") -> SManga.ONGOING
        else -> SManga.COMPLETED
    }

    override fun chapterListSelector() = "div.epsleft"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").text().trim()
        date_upload = dateFormat.parse(element.select("span.date").text().trim())?.time ?: 0
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy", Locale("ar"))
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("noscript > img#imagech").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
