package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class HolyManga : ParsedHttpSource() {

    override val name = "HolyManga"

    override val baseUrl = "http://ww1.holymanga.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    // This returns 12 manga or so, main browsing for this source should be through latest
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaSelector() = "section#popular div.entry.vertical"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h2 a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").first().attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "Not needed"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-update/page-$page/", headers)
    }

    override fun latestUpdatesSelector() = "div.comics-grid > div.entry"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").first().attr("src")
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "a.next:has(i.fa-angle-right)"

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/admin-ajax.php?action=resultautosearch&key=$query", headers)
    }

    override fun searchMangaSelector() = "a"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text()
        manga.thumbnail_url = client.newCall(GET(baseUrl + manga.url, headers)).execute().asJsoup()
            .select("div.single-comic img").attr("src")

        return manga
    }

    override fun searchMangaNextPageSelector() = "Not needed"

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.single-comic").first()

        val manga = SManga.create()
        manga.title = infoElement.select("h1").first().text()
        manga.author = infoElement.select("div.author a").text()
        val status = infoElement.select("div.update span[style]").text()
        manga.status = parseStatus(status)
        manga.genre = infoElement.select("div.genre").text().substringAfter("Genre(s): ")
        manga.description = infoElement.select("p").text()
        manga.thumbnail_url = infoElement.select("img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "h2.chap a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        val paginationSelector = latestUpdatesNextPageSelector()
        var document = response.asJsoup()
        var dateIndex = 0
        var continueParsing = true

        // Chapter list is paginated
        while (continueParsing) {
            // Chapter titles and urls
            document.select(chapterListSelector()).map{allChapters.add(chapterFromElement(it))}
            // Chapter dates
            document.select("div.chapter-date").forEach {
                allChapters[dateIndex].date_upload = parseChapterDate(it.text())
                dateIndex++
            }
            // Next page of chapters
            if (document.select(paginationSelector).isNotEmpty()) {
                document = client.newCall(GET(document.select(paginationSelector)
                    .attr("href"), headers)).execute().asJsoup()
            } else {
                continueParsing = false
            }
        }

        return allChapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.text()
        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MM/dd/yyyy", Locale.US)
        }
    }

    private fun parseChapterDate(string: String): Long {
            return dateFormat.parse(string).time
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.chapter-content img").forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
