package eu.kanade.tachiyomi.extension.en.mangahasu

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
import java.text.SimpleDateFormat
import java.util.*

class Mangahasu: ParsedHttpSource() {

    override val id: Long = 12

    override val name = "Mangahasu"

    override val baseUrl = "http://mangahasu.se"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/directory.html?page=$page")

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/latest-releases.html?page=$page")

    override fun popularMangaSelector() = "div.div_item"

    override fun latestUpdatesSelector() = "div.div_item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first().attr("src")
        element.select("a.name-manga").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.Tiếp"

    override fun latestUpdatesNextPageSelector() = "a.Tiếp"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/advanced-search.html?keyword=$query&author=&artist=&status=&typeid=&page=$page")
    }

    override fun searchMangaSelector() =
            popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    // max 200 results
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".info-c").first()

        val manga = SManga.create()
        manga.author = infoElement.select(".info")[0].text()
        manga.artist = infoElement.select(".info")[1].text()
        manga.genre = infoElement.select(".info")[3].text()
        manga.status = parseStatus(infoElement.select(".info")[4].text())

        manga.description = document.select("div.content-info > div > p").first()?.text()
        manga.thumbnail_url = document.select("div.info-img img").attr("src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Ongoing") -> SManga.ONGOING
        element.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()

        chapter.date_upload = element.select(".date-updated").last()?.text()?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(it).time
        } ?: 0
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""\s*([0-9]+)(\s-\s)([0-9]+)\s*""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    val number = it.groups[3]?.value!!
                    chapter.chapter_number = number.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div.img img").forEach { element ->
            i++
            pages.add(Page(i, "", element.attr("src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

}