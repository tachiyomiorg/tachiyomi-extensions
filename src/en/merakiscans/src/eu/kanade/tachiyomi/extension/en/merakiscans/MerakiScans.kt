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

class MerakiScans : ParsedHttpSource() {
    override val name = "MerakiScans"

    override val baseUrl = "https://merakiscans.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy")
        }
    }


    override fun popularMangaSelector() = "#all > #listitem > a"

    override fun latestUpdatesSelector() = "#mangalisthome > #mangalistitem > #mangaitem > #manganame > a"

    override fun popularMangaRequest(page: Int)
        = GET("$baseUrl/manga", headers)

    override fun latestUpdatesRequest(page: Int)
        = GET("$baseUrl", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.select("h1.title").text().trim()
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.text().trim()
    }

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList)
            = GET("$baseUrl/manga", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    //This makes it so that if somebody searches for "views" it lists everything, also includes #'s.
    private fun searchMangaSelector(query: String) = "#all > #listitem > a:contains($query)"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = null

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector(query)).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
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
        genre = infoElement.select("#detail_list > li:nth-child(11) > a").map {
            it.text().trim()
        }.joinToString(", ")
        status = infoElement.select("#detail_list > li:nth-child(9)").text().replace("Status:","").trim().let {
            parseStatus(it)
        }
        description = infoElement.select("#detail_list > span").text().trim()
        thumbnail_url = "$baseUrl" + infoElement.select("#info > #image > #cover_img").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var response = response
        val chapters = mutableListOf<SChapter>()
        val document = response.asJsoup()
        document.select(chapterListSelector()).forEach {
            chapters.add(chapterFromElement(it))
        }
        return chapters
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
        val pages = mutableListOf<Page>()
        //$it
        imgarray.forEach {
            pages.add(Page(pages.size, "", "$baseUrl/manga/$mangaslug/$chapnum/$it"))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
