package eu.kanade.tachiyomi.extension.en.manhwamanga

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Nsfw
class ManhwaManga : ParsedHttpSource() {

    override val name = "ManhwaManga.net"

    override val baseUrl = "https://manhwamanga.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/most-views", headers)
    }

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {

        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("a").attr("title")
        manga.thumbnail_url = element.select("a img").attr("src")

        return manga
    }

    override fun popularMangaNextPageSelector(): Nothing? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-updates", headers)
    }

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga {

        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("a").attr("title")
        manga.thumbnail_url = element.select("a img").attr("src")
        return manga
    }

    override fun latestUpdatesNextPageSelector(): Nothing? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/?s=$query")?.newBuilder()
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "div.col-xs-12.col-sm-12.col-md-9.col-truyen-main > div:nth-child(1) > div > div:nth-child(2) > div:nth-child(n)"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("a").attr("title")
        manga.thumbnail_url = element.select("a img").attr("src")

        return manga
    }

    override fun searchMangaNextPageSelector(): Nothing? = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("div.col-xs-12.col-sm-8.col-md-8.desc > h3").text()
        description = document.select("div.desc-text > p").text()
        thumbnail_url = document.select("div.books > div > img").attr("src")
        author = document.select("div.info > div:nth-child(1) > a").attr("title")
        genre = document.select("div.info > div:nth-child(2) > a").joinToString { it.text() }
        status = document.select("div.info > div:nth-child(3) > span").text().let {
            when {
                it.contains("Ongoing") -> SManga.ONGOING
                it.contains("Completed") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListSelector() = "#list-chapter > div.row > div > ul > li:nth-child(n)"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(element.select("a").attr("href"))
        chapter.name = element.select("a").attr("title")

        return chapter
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("#content-fiximg > p:nth-child(n) img").forEachIndexed { index, element ->
            add(Page(index, "", element.attr("src")))
        }
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not used")
}
