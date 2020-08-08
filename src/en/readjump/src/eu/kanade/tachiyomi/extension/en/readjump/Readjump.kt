package eu.kanade.tachiyomi.extension.en.readjump

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.util.Calendar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Readjump : ParsedHttpSource() {

    override val name = "Readjump"

    override val baseUrl = "https://readjump.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas", headers)
    }

    override fun popularMangaSelector() = "div.h-left a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("div.hmi-titre").text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "Not needed"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()

        document.select(latestUpdatesSelector()).map { mangas.add(latestUpdatesFromElement(it)) }

        return MangasPage(mangas.distinctBy { it.url }, false)
    }

    override fun latestUpdatesSelector() = "div.h-left > div.home-manga"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.select("div.hmi-titre a").first().attr("abs:href"))
        manga.title = element.select("div.hmi-titre a").first().text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun latestUpdatesNextPageSelector() = "not needed"

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        return MangasPage(popularMangaParse(response).mangas.filter { it.title.contains(query, ignoreCase = true) }, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        document.select("div.mf-info").let {
            manga.thumbnail_url = it.select("div.poster img").attr("abs:src")
        }
        document.select("div#chap-top").let {
            manga.title = it.select("div.titre").text()
            manga.description = it.select("div.synopsis").text()
        }
        document.select("div.info").let {
            manga.status = parseStatus(it.select("div.sub-i:last-of-type span").text())
        }
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "div.chapitre"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("div.ch-right a.chr-button:last-of-type").attr("href"))
        chapter.name = element.select("div.ch-left div.chl-titre").text()
        chapter.date_upload = parseChapterDate(element.select("div.chl-date").text())
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(" ")[0].toIntOrNull()

        return if (value != null) {
            when (date.split(" ")[1]) {
                "minute", "minutes" -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "hour", "hours" -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "day", "days" -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "week", "weeks" -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "month" -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "year", "years" -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                else -> {
                    return 0L
                }
            }
        } else {
            return 0L
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.sc-lel img[id]").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("abs:data-src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
