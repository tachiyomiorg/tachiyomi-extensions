package eu.kanade.tachiyomi.extension.en.merakiscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.network.asObservableSuccess
import okhttp3.OkHttpClient
import rx.Observable
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class MerakiScans : ParsedHttpSource() {
    override val name = "MerakiScans"

    override val baseUrl = "https://merakiscans.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy", Locale.US)
        }
    }

    override fun popularMangaSelector() = "#all > #listitem > a"

    override fun latestUpdatesSelector() = "#mangalisthome > #mangalistitem > #mangaitem > #manganame > a"

    override fun popularMangaRequest(page: Int)
        = GET("$baseUrl/manga", headers)

    override fun latestUpdatesRequest(page: Int)
        = GET(baseUrl, headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.select("h1.title").text().trim()
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.text().trim()
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList)
            = GET("$baseUrl/manga", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    //This makes it so that if somebody searches for "views" it lists everything, also includes #'s.
    private fun searchMangaSelector(query: String) = "#all > #listitem > a:contains($query)"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector(query)).map { element ->
            searchMangaFromElement(element)
        }

        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response, query)
                }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply{
        val infoElement = document.select("#content2")
        author = infoElement.select("#detail_list > li:nth-child(5)").text().replace("Author:","").trim()
        artist = infoElement.select("#detail_list > li:nth-child(7)").text().replace("Artist:","").trim()
        genre = infoElement.select("#detail_list > li:nth-child(11) > a").joinToString { it.text().trim() }
        status = infoElement.select("#detail_list > li:nth-child(9)").text().replace("Status:","").trim().let {
            parseStatus(it)
        }
        description = infoElement.select("#detail_list > span").text().trim()
        thumbnail_url = infoElement.select("#info > #image > #cover_img").attr("abs:src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#chapter_table > tbody > #chapter-head"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("data-href"))
        name = element.select("td:nth-child(1)").text().trim()
        date_upload = element.select("td:nth-child(2)").text().trim().let { parseChapterDate(it) }
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date.replace(Regex("(st|nd|rd|th)"), "")).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val doc = document.toString()
        val imgarray = doc.substringAfter("var images = [").substringBefore("];").split(",").map { it.replace("\"","") }
        val mangaslug = doc.substringAfter("var manga_slug = \"").substringBefore("\";")
        val chapnum = doc.substringAfter("var viewschapter = \"").substringBefore("\";")

        return imgarray.mapIndexed { i, image ->
            Page(i, "", "$baseUrl/manga/$mangaslug/$chapnum/$image")
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
