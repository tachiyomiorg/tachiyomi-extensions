package eu.kanade.tachiyomi.extension.ar.andromedascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class AndromedaScans : ParsedHttpSource() {
    override val name = "AndromedaScans"

    override val baseUrl = "https://andromedax.net"

    override val supportsLatest = true

    override val lang = "ar"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/projects", headers)

    override fun popularMangaSelector() = "div.flexbox2-content"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a").attr("title")
        thumbnail_url = element.select("img").attr("abs:src").substringBeforeLast("resize")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesSelector() = "div.flexbox3-item"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "[rel=next]"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val type = "application/x-www-form-urlencoded; charset=UTF-8"
        val body = RequestBody.create(MediaType.parse(type), "action=data_fetch&keyword=$query")

        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, body)
    }

    override fun searchMangaSelector() = "div.searchbox"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select("ul.series-infolist span")[3].text()
        genre = document.select("div.series-genres > a[rel=tag]").joinToString { it.text() }
        description = document.select("div.series-synops").text().trim()
    }

    override fun chapterListSelector() = "ul.series-chapterlist > li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("span").first().ownText()
        date_upload = dateFormat.parse(element.select("span.date").text())?.time ?: 0
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("noscript > img:not([alt=Andromeda Scans])").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
