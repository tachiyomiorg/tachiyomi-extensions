package eu.kanade.tachiyomi.extension.id.manhuaid

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ManhuaID : ParsedHttpSource() {

    override val name = "ManhuaID"

    override val baseUrl = "https://manhuaid.com"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "a:has(img.card-img)"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/popular/$page", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.select("img").attr("alt")
        thumbnail_url = element.select("img").attr("src")
    }

    override fun popularMangaNextPageSelector() = "[rel=nofollow]"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest/$page", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/search?q=$query", headers)

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select("table").first().select("td").get(3).text()
        title = document.select("title").text()
        description = document.select(".text-justify").text()
        genre = document.select("span.badge.badge-success.mr-1.mb-1").joinToString { it.text() }
        status = document.select("td > span.badge.badge-success").text().let {
            parseStatus(it)
        }
        thumbnail_url = document.select("img.img-fluid").attr("abs:src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "table.table.table-striped td[width] > a.text-success.text-decoration-none"

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.img-fluid.mb-0.mt-0").mapIndexed { i, element ->
            Page(i, "", element.attr("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
