package eu.kanade.tachiyomi.source.online.vietnamese

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.*

class Truyentranhlh : HttpSource() {

    override val name = "TruyenTranhLH"

    override val baseUrl = "http://truyentranhlh.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    fun popularMangaSelector() = "div.media-body > h3"

    fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&name=&genre=&sort=views&sort_type=DESC", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }


    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&name=&genre=&sort=last_update&sort_type=DESC", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain("/" + it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    fun popularMangaNextPageSelector() = "i.glyphicon.glyphicon-chevron-right"

    fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/app/manga/controllers/search.single.php?q=" + query, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        var jsonData = response.asJsoup().text()
        jsonData = jsonData.substring(1, jsonData.length - 1)
        val elementArray = JsonParser().parse(jsonData)
                .asJsonObject
                .getAsJsonArray("data")
        val mangas = elementArray.map { element ->
            searchMangaFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    fun searchMangaFromElement(element: JsonElement): SManga {
        val result = element.asJsonObject
        val manga = SManga.create()
        manga.title = result.get("primary").toString().replace("\"", "")
        manga.url = "/" + result.get("onclick").toString().replace("\"window.location='", "").replace("'\"", "")
        return manga
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.select("ul.manga-info").first()

        val manga = SManga.create()
        manga.author = infoElement.select("a.btn.btn-xs.btn-info").first()?.text()
        manga.genre = infoElement.select("a.btn.btn-xs.btn-danger").text()
        manga.description = document.select("h3:contains(Sơ lược) + p").text()
        manga.status = infoElement.select("a.btn.btn-xs.btn-success").last()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = document.select("img.thumbnail").first()?.attr("src")
        return manga
    }

    fun parseStatus(status: String) = when {
        status.contains("Chưa hoàn thành") -> SManga.ONGOING
        status.contains("Đã hoàn thành") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    fun chapterListSelector() = "table.table.table-hover > tbody > tr"

    fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("td > a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(cleanUrl(urlElement.attr("href")))
        chapter.name = urlElement.select("b").text()
        chapter.date_upload = element.select("td > i > time").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    fun cleanUrl(url: String): String {
        val index = url.lastIndexOf(baseUrl)
        if (index != -1) return url.substring(index)
        return "/" + url
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords: List<String> = date.split(" ")
        if (dateWords.size == 3) {
            val timeAgo = Integer.parseInt(dateWords[0])
            val dates: Calendar = Calendar.getInstance()
            if (dateWords[1].contains("phút")) {
                dates.add(Calendar.MINUTE, -timeAgo)
            } else if (dateWords[1].contains("giờ")) {
                dates.add(Calendar.HOUR_OF_DAY, -timeAgo)
            } else if (dateWords[1].contains("ngày")) {
                dates.add(Calendar.DAY_OF_YEAR, -timeAgo)
            } else if (dateWords[1].contains("tuần")) {
                dates.add(Calendar.WEEK_OF_YEAR, -timeAgo)
            } else if (dateWords[1].contains("tháng")) {
                dates.add(Calendar.MONTH, -timeAgo)
            } else if (dateWords[1].contains("năm")) {
                dates.add(Calendar.YEAR, -timeAgo)
            }
            return dates.timeInMillis
        }
        return 0L
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div.chapter-content > img").forEach {
            pages.add(Page(i++, "", it.attr("src")))
        }
        return pages
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(response: Response): String {
        return ""
    }
}