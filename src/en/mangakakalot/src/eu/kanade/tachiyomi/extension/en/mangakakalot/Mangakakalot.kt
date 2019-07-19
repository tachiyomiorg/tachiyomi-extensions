package eu.kanade.tachiyomi.extension.en.mangakakalot

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Mangakakalot : ParsedHttpSource() {

    override val name = "Mangakakalot"

    override val baseUrl = "http://mangakakalot.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.truyen-list > div.list-truyen-item-wrap"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga_list?type=topview&category=all&state=all&page=$page")
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga_list?type=latest&category=all&state=all&page=$page")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 a").first().let {
            manga.url = it.attr("href")
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").first().attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.page_select + a:not(.page_last)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Site ignores everything after the first word
        val substringBefore = query.replace(" ", "_").replace(",", "_").replace(":", "_")
        val url = "$baseUrl/search/$substringBefore?page=$page"
        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".panel_story_list .story_item"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.manga-info-top").first()

        val manga = SManga.create()
        manga.title = infoElement.select("h1").first().text()
        manga.author = infoElement.select("div.manga-info-top li").find { it.text().startsWith("Author") }?.text()?.substringAfter(") :")
        val status = infoElement.select("div.manga-info-top li").find { it.text().startsWith("Status") }?.text()?.substringAfter("Status :")
        manga.status = parseStatus(status)

        val genres = mutableListOf<String>()
        infoElement.select("div.manga-info-top li").find { it.text().startsWith("Genres :") }?.select("a")?.forEach { genres.add(it.text()) }
        manga.genre = genres.joinToString()
        manga.description = document.select("div#noidungm").text()
        manga.thumbnail_url = document.select("div.manga-info-pic").first().select("img").first().attr("src")
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

    override fun chapterListSelector() = "div.chapter-list div.row"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.url = urlElement.attr("href")
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(element.select("span").last().text())
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        if ("ago" in date) {
            val value = date.split(' ')[0].toInt()

            if ("min" in date) {
                return Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
            }

            if ("hour" in date) {
                return Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
            }

            if ("day" in date) {
                return Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
            }
        }

        try {
            return SimpleDateFormat("MMM-dd-yy", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
        }

        return 0L
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div#vungdoc img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("No used")

    override fun getFilterList() = FilterList()

}
