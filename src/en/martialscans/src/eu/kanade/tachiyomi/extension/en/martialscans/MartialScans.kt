package eu.kanade.tachiyomi.extension.en.martialscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MartialScans : ParsedHttpSource() {

    override val name = "MartialScans"

    override val baseUrl = "https://martialscans.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:86.0) Gecko/20100101 Firefox/86.0")

    // popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comics", headers)

    override fun popularMangaSelector() = "div.latestCards .row div"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a").attr("abs:href").substringAfter(baseUrl)
        manga.title = element.select("h2").text()
        // to do
//        manga.thumbnail_url = element.select("img.card-img").attr("abs:src")
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // latest
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String? = throw Exception("Not used")

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.select(".cover + div.center img").attr("abs:src")
        title = document.select("h1").text()
        author = document.select("h5:contains(Author) span").text()
        artist = document.select("h5:contains(Artist) span").text()
        // to do
//        status = parseStatus(document.select("").text())
        description = document.select("p:contains(Description)").firstOrNull()?.ownText()
        genre = document.select("p:contains(Tags) span").joinToString { it.text() }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("ongoing", true) -> SManga.ONGOING
        status.contains("completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListSelector() = ".row div.col-xl-9.col-lg-12.col-md-12.col-sm-12.col-12 div:has(> a)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("h6").text()
        // to do
//        chapter.date_upload = element.select("").firstOrNull()?.text()?.let { parseChapterDate(it) } ?: 0

        return chapter
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(
            ".block.center:has(img) img"
        ).mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")
}
