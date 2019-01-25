package eu.kanade.tachiyomi.extension.en.naniscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat

class NaniScans : ParsedHttpSource() {
    override val baseUrl = "https://naniscans.xyz"
    override val lang = "en"
    override val name = "NANI? Scans"
    override val supportsLatest = true

    private val projects = "$baseUrl/projects"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val chapterLink = element.select("div.col-8.text-truncate.d-inline-flex.flex-nowrap > a:nth-child(2)")
        setUrlWithoutDomain("${chapterLink.attr("href")}?mode=Manga")
        this.name = chapterLink.text().trim()
        this.date_upload = SimpleDateFormat("dd/MM/yyyy").parse(element.select("div.col-4.text-truncate > span").text().trim()).time
    }

    override fun chapterListSelector() = "#chapter_list > div"

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess().map {
            searchMangaParse(it, query)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = null

    override fun latestUpdatesParse(response: Response): MangasPage {
        val updates = response.asJsoup().select(latestUpdatesSelector())
        val series = mutableMapOf<String, SManga>()

        updates.forEach {
            SManga.create().run {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text().trim()

                series.put(title, this)
            }
        }

        return MangasPage(series.values.toList(), false)
    }

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl)

    override fun latestUpdatesSelector() = "body > div.container.mt-3 > div > div.col-lg-8 > div > div > div > div > div.pt-0.pb-1.mb-1.border-bottom.border-secondary.d-flex.align-items-center.flex-nowrap > a"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val element = document.select("body > div.container.mt-3 > div:nth-child(2) > div")
        author = element.select("span:nth-child(3)").text().trim()
        artist = element.select("span:nth-child(6)").text().trim()
        description = element.select("span:nth-child(9)").text().trim()
        thumbnail_url = "$baseUrl${document.select("#manga_page").attr("src")}"
        status = parseStatus(element.select("span:nth-child(12)").text().trim())
    }

    override fun pageListParse(document: Document): List<Page> {
        val docString = document.toString()
        val pages = mutableListOf<Page>()
        val imageUrls = docString.substringAfter("const pages = [", "").substringBefore("];", "").split(",").map { it.replace("\"", "") }

        imageUrls.forEach {
            pages.add(Page(pages.size, "", "$baseUrl${it.trim()}"))
        }

        return pages
    }

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaParse(response: Response): MangasPage {
        val updates = response.asJsoup().select(popularMangaSelector())
        val series = mutableListOf<SManga>()

        updates.forEach {
            SManga.create().run {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text().trim()

                series.add(this)
            }
        }

        return MangasPage(series, false)
    }

    override fun popularMangaRequest(page: Int) = GET(projects)

    override fun popularMangaSelector() = "body > div.container.mt-3 > div > div.col-lg-8 > div > div > div > div > div > div > h6 > a"

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = popularMangaRequest(page)

    override fun searchMangaSelector() = popularMangaSelector()

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val updates = response.asJsoup().select(popularMangaSelector())
        val series = mutableListOf<SManga>()

        updates.forEach {
            SManga.create().run {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text().trim()

                if (title.contains(query, true))
                    series.add(this)
            }
        }

        return MangasPage(series, false)
    }
}
