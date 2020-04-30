package eu.kanade.tachiyomi.extension.zh.qimiaomh

import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Qimiaomh : ParsedHttpSource() {
    override val name: String = "奇妙漫画"
    override val lang: String = "zh"
    override val baseUrl: String = "https://www.qimiaomh.com"
    override val supportsLatest: Boolean = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/list-1------hits--$page.html", headers)
    }
    override fun popularMangaNextPageSelector(): String? = "a:contains(下一页)"
    override fun popularMangaSelector(): String = "div.classification"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        url = element.select("a").first().attr("href")
        thumbnail_url = element.select("img.lazyload").attr("abs:data-src")
        title = element.select("a").first().text()
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/list-1------updatetime--$page.html", headers)
    }
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw Exception("不管用 (T_T)")
        // Todo Filters
    }
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1.title").text()
        author = document.select("p.author").first().ownText()
        artist = author
        val glist = document.select("span.labelBox a").map { it.text() }
        genre = glist.joinToString(", ")
        description = document.select("p#worksDesc").text().trim()
        thumbnail_url = document.select("div.ctdbLeft img").attr("src")
        status = when (document.select("a.status").text().substringAfter(":").trim()) {
            "连载中" -> SManga.ONGOING
            "完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapters

    override fun chapterListSelector(): String = "div.comic-content-list ul.comic-content-c"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.select("a").first().attr("href")
        name = element.select("li.tit").text().trim()
    }
    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date).time
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        val script = document.select("script:containsData(var did =)").html()
        var did = script.substringAfter("var did = ").substringBefore(";")
        var sid = script.substringAfter("var sid = ").substringBefore(";")
        val url = "$baseUrl/Action/Play/AjaxLoadImgUrl?did=$did&sid=$sid&tmp=${Random().nextFloat()}"
        val body = client.newCall(GET(url, headers)).execute().body()!!.string()
        val json = JsonParser().parse(body).asJsonObject
        val images = json["listImg"].asJsonArray
        images.forEachIndexed { index, jsonElement ->
            add(Page(index, "", jsonElement.string))
        }
    }
    override fun imageUrlParse(document: Document): String {
        throw Exception("Not Used")
    }

    // Not Used
}
