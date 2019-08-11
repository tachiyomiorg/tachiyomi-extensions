package eu.kanade.tachiyomi.extension.zh.tohomh123

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
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
import java.text.SimpleDateFormat
import java.util.*

class Tohomh : ParsedHttpSource() {

    override val name = "Tohomh123"

    override val baseUrl = "https://www.tohomh123.com"

    override val lang = "zh"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/f-1-------hits--$page.html", headers)
    }

    override fun popularMangaSelector() = "div.mh-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h2 a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        manga.thumbnail_url = element.select("p").attr("style").substringAfter("(").substringBefore(")")
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.page-pagination li a:contains(>)"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/f-1------updatetime--$page.html", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/action/Search?keyword=$query&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.banner_detail_form").first()

        val manga = SManga.create()
        manga.title = infoElement.select("h1").first().text()
        manga.author = infoElement.select("div.info p.subtitle").text().substringAfter(":").trim()
        val status = infoElement.select("div.banner_detail_form div.info span.block:contains(状态) span").text()
        manga.status = parseStatus(status)
        manga.description = infoElement.select("div.info p.content").text()
        manga.thumbnail_url = infoElement.select("div.banner_detail_form div.cover img").first().attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("连载中") -> SManga.ONGOING
        status.contains("完结") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "ul#detail-list-select-1 li a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
        // Add date for most recent chapter
        document.select("div.banner_detail_form div.info span:contains(更新时间)").text()
            .substringAfter("：").trim().let { chapters[0].date_upload = parseChapterDate(it) }
        return chapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.ownText()
        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        }
    }

    private fun parseChapterDate(string: String): Long {
            return dateFormat.parse(string).time
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val script = document.select("script:containsData(did)").first().data()
        val did = script.substringAfter("did=").substringBefore(";")
        val sid = script.substringAfter("sid=").substringBefore(";")
        val lastPage = script.substringAfter("pcount =").substringBefore(";").trim().toInt()

        for (i in 1..lastPage) {
            pages.add(Page(i, "$baseUrl/action/play/read?did=$did&sid=$sid&iid=$i", ""))
        }
        return pages
    }

    private val gson = Gson()

    override fun imageUrlParse(response: Response): String {
        return gson.fromJson<JsonObject>(response.body()!!.string())["Code"].asString
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
