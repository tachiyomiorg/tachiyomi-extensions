package eu.kanade.tachiyomi.extension.ko.newtoki

import android.annotation.SuppressLint
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

/**
 * NewToki Source
 **/
class NewToki : ParsedHttpSource() {
    override val name = "NewToki"
    override val baseUrl = "https://newtoki.net"
    override val lang: String = "ko"

    // Latest updates currently returns duplicate manga as it separates manga into chapters
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div#webtoon-list > ul > li"

    override fun popularMangaFromElement(element: Element): SManga {
        val linkElement = element.getElementsByTag("a").first()

        val manga = SManga.create()
        manga.setUrlWithoutDomain(linkElement.attr("href"))
        manga.title = element.attr("date-title")
        manga.thumbnail_url = linkElement.getElementsByTag("img").attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li:not(.disabled)"

    // Do not add page parameter if page is 1 to prevent tracking.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comic" + if (page > 1) "p$page" else "")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = try {
            !document.select(popularMangaNextPageSelector()).last().hasClass("active")
        } catch (_: Exception) {
            false
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaSelector()
    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/comic" + (if (page > 1) "/p$page" else "") + "?stx=$query")


    override fun mangaDetailsParse(document: Document): SManga {
        val info = document.select("div.view-title").first()
        val authorText = info.select("span.label.btn-info").text()
        val title = info.select(".view-content > span > b").text()
        val genres = mutableListOf<String>()
        info.select("span.label.label-success").forEach {
            genres.add(it.text())
        }

        val manga = SManga.create()
        manga.title = title
        manga.author = authorText
        manga.genre = genres.joinToString(", ")
        manga.status = SManga.UNKNOWN
        return manga
    }

    override fun chapterListSelector() = "div.serial-list > ul.list-body > li.list-item"

    override fun chapterFromElement(element: Element): SChapter {
        val linkElement = element.select(".wr-subject > a.item-subject").last()
        val rawName = linkElement.ownText().trim()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(linkElement.attr("href"))
        chapter.chapter_number = parseChapterNumber(rawName)
        chapter.name = rawName
        chapter.date_upload = parseChapterDate(element.select(".wr-date").last().text().trim())
        return chapter
    }

    private fun parseChapterNumber(name: String): Float {
        try {
            if (name.contains("[단편]")) return 1f
            // `특별` means `Special`, so It can be buggy. so pad `편`(Chapter) to prevent false return
            if (name.contains("번외") || name.contains("특별편")) return -2f
            val regex = Regex("([0-9]+)(?:[-.]([0-9]+))?(?:화)")
            val (ch_primal, ch_second) = regex.find(name)!!.destructured
            return (ch_primal + if (ch_second.isBlank()) "" else ".$ch_second").toFloatOrNull() ?: -1f
        } catch (e: Exception) {
            e.printStackTrace()
            return -1f
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        return try {
            if (date.contains(":")) {
                Calendar.getInstance().time.time
            } else {
                SimpleDateFormat("yyyy.MM.dd").parse(date).time
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }


    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        try {
            document.select(".view-padding img")
                    .map {
                        val origin = it.attr("data-original")
                        if (origin.isNullOrEmpty()) it.attr("content") else origin
                    }
                    .forEach {
                        val url = if (it.contains("://")) it else baseUrl + it
                        pages.add(Page(pages.size, "", url))
                    }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return pages
    }


    // Latest not supported
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("This method should not be called!")
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("This method should not be called!")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")


    //We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList()
}