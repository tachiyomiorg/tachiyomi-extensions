package eu.kanade.tachiyomi.extension.all.toomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

abstract class ToomicsGlobal(private val siteLang: String,
                             private val dateFormat: String,
                             private val locale: Locale,
                             override val lang: String = siteLang,
                             displayName: String = "") : ParsedHttpSource() {

    override val name = "Toomics (Only free chapters)" + (if (displayName.isNotEmpty()) " ($displayName)" else "")

    override val baseUrl = "https://global.toomics.com"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/$siteLang")
        .add("User-Agent", USER_AGENT)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$siteLang/index/set_display/?display=A&return=/$siteLang", headers)
    }

    // ToomicsGlobal does not have a popular list, so use recommended instead.
    override fun popularMangaSelector(): String = "div.section_most div.list_wrap ul.slick_item li div a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.visual div.main_text h4.title").text()
        thumbnail_url = element.select("div.visual > p > img").attr("src")
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$siteLang/index/set_display/?display=A&return=/$siteLang", headers)
    }

    override fun latestUpdatesSelector(): String = "div#section_todayup div.list_wrap ul.slick_item li div a"

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headers.newBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        val form = FormBody.Builder()
            .add("toonData", query)
            .build()

        return POST("$baseUrl/$siteLang/webtoon/ajax_search", newHeaders, form)
    }

    override fun searchMangaSelector(): String = "div.recently_list ul li a"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.search_box dl dt span.title").text()
        thumbnail_url = element.select("div.search_box p.img img").attr("src")

        val toonId = element.attr("href")
            .substringAfter("?toon=")
            .substringBefore("&")
        url = "/$siteLang/webtoon/episode/toon/$toonId"
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val header = document.select("#glo_contents header.ep-cover_ch div.title_content")

        title = header.select("h1").text()
        author = header.select("p.type_box span.writer").text()
        artist = header.select("p.type_box span.writer").text()
        genre = header.select("p.type_box span.type").text().replace("/", ",")
        description = header.select("h2").text()
        thumbnail_url = document.select("head meta[property='og:image']").attr("content")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return super.fetchChapterList(manga)
            .map { it.reversed().filter { c -> !c.url.startsWith("VIP") } }
    }

    override fun chapterListSelector(): String = "section.ep-body ol.list-ep li.normal_ep a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val num = element.select("div.cell-num span.num").text()
        val isVip = element.attr("onclick").contains("login")
        val numText = if (num.isNotEmpty()) "$num - " else ""

        name = numText + element.select("div.cell-title strong.tit").first().ownText()
        chapter_number = num.toFloatOrNull() ?: 0f
        date_upload = parseChapterDate(element.select("div.cell-time time").text()!!)
        scanlator = "Toomics"
        url = (if (isVip) "VIP:" else "") + "/$siteLang" +
            element.attr("onclick")
                .substringAfter("'/$siteLang")
                .substringBefore("'")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val toonId = chapter.url.substringAfterLast("toon/")
        val newHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/$siteLang/webtoon/episode/toon/$toonId")
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        val url = document.select("head meta[property='og:url']").attr("content")

        return document.select("main.viewer-body div.viewer-imgs div img")
            .mapIndexed { i, el -> Page(i, url, el.attr("data-original"))}
    }

    override fun imageUrlParse(document: Document): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun parseChapterDate(date: String) : Long {
        return try {
            SimpleDateFormat(dateFormat, locale).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
    }
}
