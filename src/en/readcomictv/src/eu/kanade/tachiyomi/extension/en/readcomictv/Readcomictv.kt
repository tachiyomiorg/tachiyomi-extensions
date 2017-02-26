package eu.kanade.tachiyomi.extension.en.readcomictv

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat

class Readcomictv : ParsedHttpSource() {

    override val lang = "en"
    override val name = "Readcomictv"
    override val supportsLatest = true
    override val baseUrl = "http://readcomics.tv"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val info = element.select("a")
        chapter.name=info.text()
        chapter.setUrlWithoutDomain(info.attr("href") + "/full")
        chapter.date_upload = SimpleDateFormat("M/d/y").parse(element.select("span").text()).time
        return chapter
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).asReversed()
    }

    override fun chapterListSelector(): String {
        return "ul.basic-list li"
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("imageUrlParse not implemented")
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text()
        return manga
    }

    override fun latestUpdatesNextPageSelector(): String? {
        return null
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("http://readcomics.tv/comic-updates/$page",headers)
    }

    override fun latestUpdatesSelector(): String {
        return ".hlb-name"
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val info = document.select(".manga-details table")
        manga.author = info.select("tr:nth-child(5) > td:nth-child(2)").text()
        manga.artist = info.select("tr:nth-child(5) > td:nth-child(2)").text()
        manga.description = document.select(".pdesc").text()
        manga.thumbnail_url = document.select(".manga-image img").attr("src")
        manga.genre = info.select("tr:nth-child(6) > td:nth-child(2)").text()
        val  status = info.select("tr:nth-child(4) > td:nth-child(2)").text()
        manga.status = if (status == "Completed") SManga.COMPLETED else if (status == "Ongoing") SManga.ONGOING else SManga.UNKNOWN
        return manga
    }

    override fun pageListParse(document: Document): List<Page> {
        var i = 0
        return document.select(".chapter_img").map {
            Page(i++,"",it.attr("src"))
        }
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.text()
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    override fun popularMangaNextPageSelector(): String? = ".general-nav :last-child"

    override fun popularMangaRequest(page: Int): Request {
        return GET("http://readcomics.tv/popular-comic/$page",headers)
    }

    override fun popularMangaSelector(): String = ".manga-box h3 a"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.text()
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    override fun searchMangaNextPageSelector(): String? {
        return ".general-nav :last-child[href]"
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("http://readcomics.tv/advanced-search?key=$query&page=$page",headers)
    }

    override fun searchMangaSelector(): String {
        return ".manga-box h3 a"
    }

}