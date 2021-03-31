package eu.kanade.tachiyomi.multisrc.mangamainac

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

// Based On TCBScans and MangaMainac sources
// MangaManiac is a network of sites built by Animemaniac.co.

abstract class MangaMainac(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {
    // Info
    override val supportsLatest: Boolean = false
    override val client: OkHttpClient = network.cloudflareClient

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl)
    }
    override fun popularMangaSelector() = "#page"
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".mangainfo_body > img").attr("src")
        manga.url = element.select("#primary-menu .menu-item:first-child").attr("href")
        manga.title = element.select(".intro_content h2").text()
        return manga
    }
    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")
    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")
    override fun latestUpdatesSelector(): String = throw Exception("Not used")
    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception("No Search Function")
    override fun searchMangaNextPageSelector() = throw Exception("Not used")
    override fun searchMangaSelector() = throw Exception("Not used")
    override fun searchMangaFromElement(element: Element) = throw Exception("Not used")

    // Get Override
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }
    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.select(".intro_content").text()
        title = document.select(".intro_content h2").text()
        author = if ("Author" in info) substringextract(info, "Author(s):", "Released") else null
        artist = author
        genre = if ("Genre" in info) substringextract(info, "Genre(s):", "Status") else null
        status = when (substringextract(info, "Status:", "(")) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = if ("Description" in info) info.substringAfter("Description:").trim() else null
        thumbnail_url = document.select(".mangainfo_body img").attr("src")
    }
    private fun substringextract(text: String, start: String, end: String): String = text.substringAfter(start).substringBefore(end).trim()

    // Chapters
    override fun chapterListSelector(): String = ".chap_tab tr"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("a").text()
        url = element.select("a").attr("abs:href")
        date_upload = parseRelativeDate(element.select("#time").text().substringBefore(" ago"))
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterList = document.select(chapterListSelector()).map { chapterFromElement(it) }

        return if (hasCountdown(chapterList[0]))
            chapterList.subList(1, chapterList.size)
        else
            chapterList
    }
    private fun hasCountdown(chapter: SChapter): Boolean {
        val document = client.newCall(
            GET(chapter.url, headersBuilder().build())
        ).execute().asJsoup()

        return document
            .select("iframe[src^=https://free.timeanddate.com/countdown/]")
            .isNotEmpty()
    }

    // Subtract relative date (e.g. posted 3 days ago)
    private fun parseRelativeDate(date: String): Long {
        val calendar = Calendar.getInstance()

        if (date.contains("yesterday")) {
            calendar.apply { add(Calendar.DAY_OF_MONTH, -1) }
        } else {
            val trimmedDate = date.replace("one", "1").removeSuffix("s").split(" ")

            when (trimmedDate[1]) {
                "year" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
                "month" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
                "week" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
                "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            }
        }

        return calendar.timeInMillis
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select(".img_container img").forEach { img ->
            add(Page(size, "", img.attr("src")))
        }
    }

    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")
}
