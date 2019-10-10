package eu.kanade.tachiyomi.extension.en.boommanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

open class BoomManga (
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ParsedHttpSource() {

    //override val name = "BoomManga"
    //override val baseUrl = "https://m.boommanga.com/"
    //override val lang = "en"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category?sort=heat&page=$page", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category?sort=new&page=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search?keyword=$query&page=$page")?.newBuilder()
        return GET(url.toString(), headers)
    }

    override fun popularMangaSelector() = ".vertical-list > li"
    override fun searchMangaSelector() = ".vertical-list2 > li"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun chapterListSelector() = ".chapters > li"

    override fun popularMangaNextPageSelector() = "script:contains(next_page_url):not(script:contains(false))"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()


    override fun mangaDetailsRequest(manga: SManga) = GET( manga.url, headers)
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun pageListRequest(chapter: SChapter) = GET( chapter.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.url = element.select("a").attr("href")
        manga.title = element.select("h4").text().trim()
        manga.thumbnail_url = element.select("img").attr("src")

        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a").first().attr("href")
        manga.title = element.select("h4").text().trim()
        manga.author = element.select("p").first().text().trim()
        manga.thumbnail_url = element.select("img").attr("src")
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
            chapter.url = element.select("a").attr("href")
            chapter.chapter_number = element.select("[data-num]").attr("data-num").toFloat()
            val date= element.select(".date").text()
            if (date.isNotBlank()) { chapter.date_upload = parseDate(date) }
            chapter.name = nameselector(element).trim()
        return chapter
    }

    open fun nameselector(element: Element) = element.select(".name").text()

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd kk:mm:ss", Locale.US ).parse(date).time
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = authorget(document)?.trim()
        manga.artist = authorget(document)?.trim()
        manga.description = document.select(".inner-text").text().trim()
        manga.thumbnail_url = thumbnailget(document)
        manga.genre = genreget(document)
        manga.status = statusget(document)
        return manga
    }

    open fun authorget(document: Document) = document.select(".comic-info").first()?.text()?.substringAfter("：")?.substringBefore("HEAT")
    open fun thumbnailget(document: Document) = document.select(".cover img").attr("src")
    open fun genreget(document: Document) = document.select(".comic-info p").first().text()
    open fun statusget(document: Document) = SManga.UNKNOWN

    override fun pageListParse(response: Response): List<Page> {
        val body = response.asJsoup()
        val pages = mutableListOf<Page>()
        val elements = body.select("img[data-src]")
        for (i in 0 until elements.size) {
            pages.add(Page(i, "", getImage(elements[i])))
        }
        return pages
    }

    private fun getImage(element: Element): String {
        var url =
            when {
                element.attr("data-src").endsWith(".jpg") || element.attr("data-src").endsWith(".png") || element.attr("data-src").endsWith(".jpeg") -> element.attr("data-src")
                element.attr("src").endsWith(".jpg") || element.attr("src").endsWith(".png") || element.attr("src").endsWith(".jpeg") -> element.attr("src")
                else -> element.attr("data-src")
            }
        if (url.startsWith("//")) {
            url = "http:$url"
        }
        return url
    }


    override fun pageListParse(document: Document) = throw Exception("Not used")
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")

}

