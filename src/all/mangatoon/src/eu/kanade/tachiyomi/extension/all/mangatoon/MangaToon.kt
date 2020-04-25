package eu.kanade.tachiyomi.extension.all.mangatoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class MangaToon(
    override val lang: String,
    private val urllang: String
) : ParsedHttpSource() {

    override val name = "MangaToon (Limited)"
    override val baseUrl = "https://mangatoon.mobi"
    override val supportsLatest = true

    override fun popularMangaSelector() = "div.genre-content div.items a"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = "div.recommend-item"
    override fun chapterListSelector() = "a.episode-item"

    override fun popularMangaNextPageSelector() = "span.next"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        val page0 = page - 1
        return GET("$baseUrl/$urllang/genre/hot?page=$page0", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        val page0 = page - 1
        return GET("$baseUrl/$urllang/genre/new?page=$page0", headers)
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/$urllang/search?word=$query")?.newBuilder()
        return GET(url.toString(), headers)
    }

    // override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    // override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)
    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url + "/episodes", headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = (element.select("a").first().attr("href"))
        manga.title = element.select("div.recommend-comics-title").text().trim()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = (element.select("a").first().attr("href"))
        manga.title = element.select("div.content-title").text().trim()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
            chapter.url = element.select("a").first().attr("href")
            chapter.chapter_number = element.select("div.item-left").text().trim().toFloat()
        val date = element.select("div.episode-date").text()
            chapter.date_upload = parseDate(date)
            chapter.name = if (chapter.chapter_number> 20) { "\uD83D\uDD12 " } else { "" } + element.select("div.episode-title").text().trim()
        return chapter
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date).time
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = document.select("div.created-by").text().trim()
        manga.artist = manga.author
        manga.description = document.select("div.description").text().trim()
        manga.thumbnail_url = document.select("div.detail-top-right img").attr("abs:src")
        val glist = document.select("div.description-tag div.tag").map { it.text() }
        manga.genre = glist.joinToString(", ")
        manga.status = when (document.select("span.update-date")?.first()?.text()) {
            "Update" -> SManga.ONGOING
            "End", "完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.asJsoup()
        val pages = mutableListOf<Page>()
        val elements = body.select("div.pictures img")
        for (i in 0 until elements.size) {
            pages.add(Page(i, "", elements[i].attr("abs:src")))
        }
        if (pages.size == 1) throw Exception("Locked episode, download MangaToon APP and read for free!")
        return pages
    }

    override fun pageListParse(document: Document) = throw Exception("Not used")
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")
}
