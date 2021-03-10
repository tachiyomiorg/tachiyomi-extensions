package eu.kanade.tachiyomi.extension.en.tcbscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class TCBScans : ParsedHttpSource() {

    override val name = "TCB Scans"
    override val baseUrl = "https://onepiecechapters.com"
    override val lang = "en"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl)
    }

    override fun popularMangaSelector() = "#page"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".mangainfo_body > img").attr("src")
        manga.url = element.select("#primary-menu .menu-item:first-child").attr("href")
        manga.title = element.select(".intro_content h2").text()
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.select(".mangainfo_body > img").attr("src")
        title = document.select(".intro_content h2").text()
        status = parseStatus(document.select(".intro_content").text())
        description = document.select(".intro_content").joinToString("\n") { it.text() }
    }

    private fun parseStatus(element: String): Int = when {
        element.toLowerCase().contains("ongoing (pub") -> SManga.ONGOING
        element.toLowerCase().contains("completed (pub") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListSelector() = "table.chap_tab tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("a").text()
        chapter.date_upload = element.select("#time i").last()?.text()?.let { parseChapterDate(it) }
            ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords: List<String> = date.split(" ")
        if (dateWords.size == 3) {
            val timeAgo = Integer.parseInt(dateWords[0])
            val dates: Calendar = Calendar.getInstance()
            when {
                dateWords[1].contains("minute") -> {
                    dates.add(Calendar.MINUTE, -timeAgo)
                }
                dateWords[1].contains("hour") -> {
                    dates.add(Calendar.HOUR_OF_DAY, -timeAgo)
                }
                dateWords[1].contains("day") -> {
                    dates.add(Calendar.DAY_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("week") -> {
                    dates.add(Calendar.WEEK_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("month") -> {
                    dates.add(Calendar.MONTH, -timeAgo)
                }
                dateWords[1].contains("year") -> {
                    dates.add(Calendar.YEAR, -timeAgo)
                }
            }
            return dates.timeInMillis
        }
        return 0L
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterList = document.select(chapterListSelector()).map { chapterFromElement(it) }

        return if (hasCountdown(chapterList[0]))
            chapterList.subList(1, chapterList.size)
        else
            chapterList
    }

    private fun hasCountdown(chapter: SChapter): Boolean {
        val document = client.newCall(
            GET(
                baseUrl + chapter.url,
                headersBuilder().build()
            )
        ).execute().asJsoup()

        return document
            .select("iframe[src^=https://free.timeanddate.com/countdown/]")
            .isNotEmpty()
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select(".container .img_container center img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""
}
