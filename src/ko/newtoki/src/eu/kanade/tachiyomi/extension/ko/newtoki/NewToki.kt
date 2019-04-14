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
open class NewToki(override val name: String, override val baseUrl: String, private val boardName: String) : ParsedHttpSource() {
    override val lang: String = "ko"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient


    override fun popularMangaSelector() = "div#webtoon-list > ul > li"

    override fun popularMangaFromElement(element: Element): SManga {
        val linkElement = element.getElementsByTag("a").first()

        val manga = SManga.create()
        manga.setUrlWithoutDomain(linkElement.attr("href"))
        manga.title = element.select("span.title").first().ownText()
        manga.thumbnail_url = linkElement.getElementsByTag("img").attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li:not(.disabled)"

    // Do not add page parameter if page is 1 to prevent tracking.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$boardName" + if (page > 1) "/p$page" else "")

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
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/$boardName" + (if (page > 1) "/p$page" else "") + "?stx=$query")


    override fun mangaDetailsParse(document: Document): SManga {
        val info = document.select("div.view-title > .view-content").first()
        val title = document.select("div.view-content > span > b").text()
        val descriptionElement = info.select("div.row div.view-content:not([style])")
        val description = descriptionElement.map {
            it.text().trim()
        }

        val manga = SManga.create()
        manga.title = title
        manga.description = description.joinToString("\n")
        descriptionElement.forEach {
            val text = it.text()
            when {
                "작가" in text -> manga.author = it.getElementsByTag("a").text()
                "분류" in text -> {
                    val genres = mutableListOf<String>()
                    it.getElementsByTag("a").forEach { item ->
                        genres.add(item.text())
                    }
                    manga.genre = genres.joinToString(", ")
                }
                "발행구분" in text -> manga.status = parseStatus(it.getElementsByTag("a").text())
            }
        }
        return manga
    }

    private fun parseStatus(status: String) = when (status.trim()) {
        "주간", "격주", "월간", "격월/비정기", "단행본" -> SManga.ONGOING
        "단편", "완결" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
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
                val calendar = Calendar.getInstance()
                val splitDate = date.split(":")

                val hours = splitDate.first().toInt()
                val minutes = splitDate.last().toInt()

                val calendarHours = calendar.get(Calendar.HOUR)
                val calendarMinutes = calendar.get(Calendar.MINUTE)

                if (calendarHours >= hours && calendarMinutes > minutes) {
                    calendar.add(Calendar.DATE, -1)
                }

                calendar.timeInMillis
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


    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)


    //We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList()
}