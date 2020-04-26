package eu.kanade.tachiyomi.extension.id.mangakyo

import android.net.Uri
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

class Mangakyo : ParsedHttpSource() {
    override val name: String = "Mangakyo"
    override val lang: String = "id"
    override val baseUrl: String = "https://www.mangakyo.me"
    override val supportsLatest: Boolean = true
    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/advanced-search/page/$page/?title&author&status&order=popular", headers)
    }
    override fun popularMangaNextPageSelector(): String? = "a.next"
    override fun popularMangaSelector(): String = "div.utao"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h3").text()
        setUrlWithoutDomain(element.select("a").first().attr("abs:href"))
        thumbnail_url = element.select("img").attr("abs:src")
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/advanced-search/page/$page/?title=&author=&status=&order=update", headers)
    }
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
            .appendPath("advanced-search")
            .appendPath("page")
            .appendPath(page.toString())
            .appendQueryParameter("title", query)
            .appendQueryParameter("author", "")
            .appendQueryParameter("status", "")
            .appendQueryParameter("order", "title")
        return GET(uri.toString(), headers)
    }
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1[itemprop=headline]").text()
        thumbnail_url = document.select("div[itemprop=image] img").attr("abs:src")
        author = document.select("th:contains(Author) + td").text()
        artist = author
        val glist = document.select("th:contains(Genres) ~ td a").map { it.text() }
        genre = glist.joinToString(", ")
        status = when (document.select("th:contains(Status) ~ td").text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = document.select("span.desc p").joinToString("\n") { it.text() }
    }

    // Chapter

    override fun chapterListSelector(): String = "span.lchx a"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text()
        setUrlWithoutDomain(element.attr("abs:href"))
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("img.alignnone").forEachIndexed { index, element ->
            add(Page(index, "", element.attr("abs:src")))
        }
    }
    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")
}
