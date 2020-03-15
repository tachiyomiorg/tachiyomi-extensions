package eu.kanade.tachiyomi.extension.en.mangahasu

import android.util.Base64
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
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
import java.util.Locale

class Mangahasu : ParsedHttpSource() {

    override val name = "Mangahasu"

    override val baseUrl = "http://mangahasu.se"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().add("Referer", baseUrl)
    }

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/directory.html?page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest-releases.html?page=$page", headers)

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

    override fun popularMangaNextPageSelector() = "a[title = Tiếp]"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET(
            "$baseUrl/advanced-search.html?keyword=$query&author=&artist=&status=&typeid=&page=$page",
            headers
        )
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
            SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(it)?.time ?: 0
        } ?: 0
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Chapter\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {

        //Grab All Pages from site
        //Some images are place holders on new chapters.

        val pages = mutableListOf<Page>().apply {
            document.select("div.img img").forEach {
                val pageNumber = it.attr("class").substringAfter("page").toInt()
                add(Page(pageNumber, "", it.attr("src")))
            }
        }

        //Some images are not yet loaded onto Mangahasu's image server.
        //Decode temporary URLs and replace placeholder images.

        val lstDUrls =
            document.select("script:containsData(lstDUrls)").html().substringAfter("lstDUrls")
                .substringAfter("\"").substringBefore("\"")
        if (lstDUrls != "W10=") { //Base64 = [] or empty file
            val decoded = String(Base64.decode(lstDUrls, Base64.DEFAULT))
            val json = JsonParser().parse(decoded).array
            json.forEach {
                val pageNumber = it["page"].int
                pages.removeAll { page -> page.index == pageNumber }
                pages.add(Page(pageNumber, "", it["url"].string))
            }
        }
        pages.sortBy { page -> page.index }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""
}
