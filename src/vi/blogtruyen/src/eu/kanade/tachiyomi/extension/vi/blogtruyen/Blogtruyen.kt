package eu.kanade.tachiyomi.extension.vi.blogtruyen

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Blogtruyen source
 */

class Blogtruyen : ParsedHttpSource() {
	override val lang: String = "vi"

    override val name = "Blogtruyen"

    override val baseUrl = "http://blogtruyen.com/"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "p:has(span.ellipsis)"

    override fun latestUpdatesSelector() = "section.list-mainpage.listview > div > div > div > div.fl-l"

    fun popularMangaInitialUrl() = "$baseUrl"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/ajax/Search/AjaxLoadListManga?key=tatca&orderBy=3&p=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page-$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = element.select("img").first().attr("alt").toString()
            manga.thumbnail_url = element.select("img").first().attr("src")
            //manga.title = it.text().trim()
            //manga.title = it.attr("href").toString().substringAfterLast('/')
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "span.page > a"

    override fun latestUpdatesNextPageSelector() =  "ul.pagination.paging.list-unstyled > li:nth-last-child(2) > a"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/timkiem/nangcao/1/0/-1/-1?txt=$query&p=$page")

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = "ul.pagination.list-unstyled > li:nth-last-child(2) > a"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("section.manga-detail").first()

        val manga = SManga.create()


        manga.author = infoElement.select("p:contains(Tác giả:)").text().toString().substringAfterLast(':')
        manga.title = document.title().substringBeforeLast(" |")
        manga.genre = infoElement.select("p:contains(Thể loại:) > *:gt(0)").text()
        manga.description = infoElement.select("div.content").text()
        manga.status = infoElement.select("p:contains(Trạng thái:)").text().toString().substringAfterLast(':').orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = document.select("div.thumbnail img").first()?.attr("src")

        return manga
    }

    fun parseStatus(status: String) = when {
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Hoàn Thành") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#list-chapters p"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("span.publishedDate").first()?.text()?.let {
            SimpleDateFormat("dd/MM/yyyy").parse(it).time
        } ?: 0
        return chapter
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("article#content > img").forEach {
            pages.add(Page(i++, "", it.attr("src")))
        }
        return pages
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""

    var status = arrayOf("Tất cả", "Đang tiến hành", "Hoàn thành", "Ngưng")

    private class Status : Filter.Select<String>("Status", arrayOf("Ngưng", "Đang tiến hành", "Đã hoàn thành", "Tất cả"))
    private class Genre(name: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

 /**
  * Need help with putting filter for genres and status in search function
  
    private class GenreFilter(val uriParam: String, displayName: String) : Filter.TriState(displayName)

    private class GenreGroup : Filter.Group<GenreFilter>("Genres", listOf(
            GenreFilter("54", "16+"),
            GenreFilter("45", "18+"),
            GenreFilter("1", "Action"),
            GenreFilter("2", "Adult"),
            GenreFilter("3", "Adventure"),
            GenreFilter("4", "Anime"),
            GenreFilter("5", "Comedy"),
            GenreFilter("6", "Comic"),
            GenreFilter("7", "Doujinshi"),
            GenreFilter("49", "Drama"),
            GenreFilter("48", "Ecchi"),
            GenreFilter("60", "Event BT"),
            GenreFilter("50", "Fantasy"),
            GenreFilter("61", "Game"),
            GenreFilter("51", "Gender Bender"),
            GenreFilter("12", "Harem"),
            GenreFilter("13", "Historical"),
            GenreFilter("14", "Horror"),
            GenreFilter("14", "Horror"),
            GenreFilter("15", "Josei"),
            GenreFilter("16", "Live action"),
            GenreFilter("46", "Magic"),
            GenreFilter("55", "manga"),
            GenreFilter("17", "Manhua"),
            GenreFilter("17", "Manhua"),
            GenreFilter("18", "Manhwa"),
            GenreFilter("19", "Martial Arts"),
            GenreFilter("20", "Mature"),
            GenreFilter("21", "Mecha"),
            GenreFilter("22", "Mystery"),
            GenreFilter("56", "Nấu Ăn"),
            GenreFilter("62", "NTR"),
            GenreFilter("23", "One shot"),
            GenreFilter("24", "Psychological"),
            GenreFilter("25", "Romance"),
            GenreFilter("26", "School Life"),
            GenreFilter("27", "Sci-fi"),
            GenreFilter("28", "Seinen"),
            GenreFilter("29", "Shoujo"),
            GenreFilter("30", "Shoujo Ai"),
            GenreFilter("31", "Shounen"),
            GenreFilter("32", "Shounen Ai"),
            GenreFilter("33", "Slice of life"),
            GenreFilter("34", "Smut"),
            GenreFilter("35", "Soft Yaoi"),
            GenreFilter("36", "Soft Yuri"),
            GenreFilter("37", "Sports"),
            GenreFilter("38", "Supernatural"),
            GenreFilter("39", "Tạp chí truyện tranh"),
            GenreFilter("40", "Tragedy"),
            GenreFilter("58", "Trap (Crossdressing)"),
            GenreFilter("57", "Trinh Thám"),
            GenreFilter("41", "Truyện scan"),
            GenreFilter("53", "Video Clip"),
            GenreFilter("42", "VnComic"),
            GenreFilter("52", "Webtoon"),
            GenreFilter("63", "Xuyên không/Hồi sinh"),
            GenreFilter("59", "Yuri")
    ))

*/
}
