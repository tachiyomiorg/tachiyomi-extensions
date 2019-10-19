package eu.kanade.tachiyomi.extension.fr.scantrad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*
import rx.Observable

class Scantrad : ParsedHttpSource() {

    override val name = "Scantrad"

    override val baseUrl = "https://scantrad.fr"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas", headers)
    }

    override fun popularMangaSelector() = "ul#projects-list li a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("span.project-name").text()

        return manga
    }

    override fun popularMangaNextPageSelector() = "Not needed"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/mangas", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()

        document.select(latestUpdatesSelector()).map { mangas.add(latestUpdatesFromElement(it)) }

        return MangasPage(mangas.distinctBy { it.title }, false)
    }

    override fun latestUpdatesSelector() = "ul#chapters-list li a:first-of-type"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.url = element.attr("href").substringAfter("mangas").removeSuffix("/").substringBeforeLast("/")
        manga.title = element.select("h3").first().ownText()

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
        return MangasPage(popularMangaParse(response).mangas.filter { it.title.toLowerCase().contains(query.toLowerCase()) }, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Details

    private fun setThumbnail(title: String): String {
        return client.newCall(GET("https://myanimelist.net/manga.php?q=" + title.replace(" ", "%20"), headers)).execute().asJsoup().select("a.hoverinfo_trigger").first().attr("href").let {
            client.newCall(GET(it, headers)).execute().asJsoup().select("img.ac").first().attr("src")
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.select("div.project-content h1").text()
        manga.description = document.select("div.project-content div.synopsis").text()
        manga.thumbnail_url = setThumbnail(manga.title)

        return manga
    }

    // Chapters

    override fun chapterListSelector() = "ul#project-chapters-list li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(element.select("a:first-of-type").attr("href"))
        chapter.name = element.select("div.name-chapter").text()
        chapter.date_upload = parseChapterDate(element.select("span.chapter-date").text())

        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(" ")[3].toInt()

        return when (date.split(" ")[4]) {
           "minute", "minutes" -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "heure", "heures" -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "jour", "jours" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "semaine", "semaines" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "mois" -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "an", "ans" -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.center select[name=chapter-page]:not(.mobile) option").forEachIndexed { i, page ->
            pages.add(Page(i, page.attr("value"), ""))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String {
        return document.select("div.image img").attr("abs:src")
    }

    override fun getFilterList() = FilterList()

}
