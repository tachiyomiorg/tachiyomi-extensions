package eu.kanade.tachiyomi.extension.es.inmanga

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class InManga : ParsedHttpSource() {

    override val name = "InManga"

    override val baseUrl = "https://inmanga.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val postHeaders = headers.newBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    private val gson = Gson()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val skip = (page - 1) * 10
        val body = RequestBody.create(null, "filter%5Bgeneres%5D%5B%5D=-1&filter%5BqueryString%5D=&filter%5Bskip%5D=$skip&filter%5Btake%5D=10&filter%5Bsortby%5D=1&filter%5BbroadcastStatus%5D=0&filter%5BonlyFavorites%5D=false&d=")

        return POST("$baseUrl/manga/getMangasConsultResult", postHeaders, body)
    }

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "body"

    // Latest

    // Search filtered by "Recién actualizado"
    override fun latestUpdatesRequest(page: Int): Request {
        val skip = (page - 1) * 10
        val body = RequestBody.create(null, "filter%5Bgeneres%5D%5B%5D=-1&filter%5BqueryString%5D=&filter%5Bskip%5D=$skip&filter%5Btake%5D=10&filter%5Bsortby%5D=3&filter%5BbroadcastStatus%5D=0&filter%5BonlyFavorites%5D=false&d=")

        return POST("$baseUrl/manga/getMangasConsultResult", postHeaders, body)
    }

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "body"

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val skip = (page - 1) * 10
        val body = RequestBody.create(null, "filter%5Bgeneres%5D%5B%5D=-1&filter%5BqueryString%5D=$query&filter%5Bskip%5D=$skip&filter%5Btake%5D=10&filter%5Bsortby%5D=1&filter%5BbroadcastStatus%5D=0&filter%5BonlyFavorites%5D=false&d=")

        return POST("$baseUrl/manga/getMangasConsultResult", postHeaders, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val document = response.asJsoup()

        document.select(searchMangaSelector()).map { mangas.add(searchMangaFromElement(it)) }

        return MangasPage(mangas, document.select(searchMangaSelector()).count() == 10)
    }

    override fun searchMangaSelector() = "body > a"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("h4.m0").text()
        manga.thumbnail_url = element.select("img").attr("abs:data-src")

        return manga
    }

    override fun searchMangaNextPageSelector(): String? = null

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        document.select("div.col-md-3 div.panel.widget").let { info ->
            manga.thumbnail_url = info.select("img").attr("abs:src")
            manga.status = parseStatus(info.select(" a.list-group-item:contains(estado) span").text())
        }
        document.select("div.col-md-9").let { info ->
            manga.title = info.select("h1").text()
            manga.description = info.select("div.panel-body").text()
        }

        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("En emisión") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/chapter/getall?mangaIdentification=${manga.url.substringAfterLast("/")}", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val data = response.body()!!.string().substringAfter("{\"data\":\"").substringBeforeLast("\"}")
            .replace("\\", "")

        gson.fromJson<JsonObject>(data)["result"].asJsonArray.forEach { chapters.add(chapterFromJson(it)) }

        return chapters.sortedBy { it.chapter_number.toInt() }.reversed()
    }

    override fun chapterListSelector() = "not using"

    private fun chapterFromJson(json: JsonElement): SChapter {
        val chapter = SChapter.create()

        chapter.url = "/chapter/chapterIndexControls?identification=${json["Identification"].string}"
        json["FriendlyChapterNumberUrl"].string.replace("-", ".").let { num ->
            chapter.name = "Chapter $num"
            chapter.chapter_number = num.toFloat()
        }
        chapter.date_upload = parseChapterDate(json["RegistrationDate"].string) ?: 0

        return chapter
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
    }

    private fun parseChapterDate(string: String): Long? {
            return dateFormat.parse(string).time
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val ch = document.select("[id=\"FriendlyChapterNumberUrl\"]").attr("value")
        val title = document.select("[id=\"FriendlyMangaName\"]").attr("value")

        document.select("img.ImageContainer").forEachIndexed { i, img ->
            pages.add(Page(i, "", "$baseUrl/images/manga/$title/chapter/$ch/page/${i + 1}/${img.attr("id")}"))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
