package eu.kanade.tachiyomi.extension.en.heavenmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*


class Heavenmanga : ParsedHttpSource() {

    override val name = "Heaven Manga"

    override val baseUrl = "http://heavenmanga.vip"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaSelector() = "div.comics-grid div.entry"

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) {
            return GET("$baseUrl/manga-list/")
        } else {
            return GET("$baseUrl/manga-list/page-$page")
        }
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) {
            return GET("$baseUrl/latest-update/")
        } else {
            return GET("$baseUrl/latest-update/page-$page")
        }
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        val item = element.select("h3 a")
        manga.url = item.attr("href")
        manga.title = item.text()
        manga.thumbnail_url = element.select("a.thumb > img").attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "div.pagination > a.next"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/?s=$query"
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.comic-info")

        val manga = SManga.create()
        val author = infoElement.select("div.author").text()
        val genre = infoElement.select("div.genre").text()
        val status = infoElement.select("div.update > span:eq(1)").text()

        manga.title = infoElement.select("h1.name").text()
        manga.author = author.substring(author.indexOf(":") + 2)
        manga.status = parseStatus(status)
        manga.genre = genre.substring(genre.indexOf(":") + 2)
        manga.description = document.select("div.comic-description p").text()
        manga.thumbnail_url = infoElement.select("div.thumb img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListSelector() = "div.chapters-wrapper div.two-rows"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.url = urlElement.attr("href")
        chapter.name = element.select("div.r1").text()

        val date = element.select("div.chapter-date").text()
        if (!date.isNullOrEmpty()) chapter.date_upload = parseDate(date)
        return chapter
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(date).time
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.chapter-content img").forEach {
            val url = it.attr("src")
            if (url != "") {
                pages.add(Page(pages.size, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
