package eu.kanade.tachiyomi.extension.pt.animaregia

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class AnimaRegia : ParsedHttpSource() {

    override val name = "AnimaRegia"

    override val baseUrl = "https://animaregia.net"

    override val lang = "pt"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val image = element.select("img").first()

        title = image.attr("alt")
        thumbnail_url = image.absUrl("src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .set("Referer", "$baseUrl/manga-list")
            .build()

        return GET("$baseUrl/filterList?page=$page&sortBy=views&asc=false", newHeaders)
    }

    override fun popularMangaSelector(): String = ".media .media-left a.thumbnail"

    override fun popularMangaFromElement(element: Element): SManga = mangaFromElement(element)

    override fun popularMangaNextPageSelector() = "ul.pagination li:has(a[rel='next'])"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = super.latestUpdatesParse(response)

        return MangasPage(result.mangas.distinctBy { it.title }, result.hasNextPage)
    }

    override fun latestUpdatesSelector() = ".media .media-left a.thumbnail"

    override fun latestUpdatesFromElement(element: Element): SManga = mangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
            .addQueryParameter("query", query)

       return GET(url.toString(), newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asJsonObject()

        val mangas = result["suggestions"].array.map { searchMangaFromObject(it.obj) }

        return MangasPage(mangas, false)
    }

    private fun searchMangaFromObject(obj: JsonObject): SManga = SManga.create().apply {
        title = obj["value"].string
        setUrlWithoutDomain("$baseUrl/manga/${obj["data"].string}")
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val mangaInfo = document.select("div.row div.col-sm-5")
        val listGroup = mangaInfo.select("ul.list-group")

        return SManga.create().apply {
            author = removeLabel(listGroup.select("li:contains(Autor(es):)").text())
            artist = removeLabel(listGroup.select("li:contains(Artist(s):)").text())
            genre = mangaInfo.select("li:contains(Categorias:) a")
                .joinToString { it.text() }
            status = parseStatus(removeLabel(listGroup.select("li:contains(Status:)").text().orEmpty()))
            description = mangaInfo.select("div.well").text().substringAfter("Sumário ")
            thumbnail_url = document.select("img.img-thumbnail").first().absUrl("src")
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ativo") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector(): String = "ul.chapters li.volume-0"

    override fun chapterFromElement(element: Element): SChapter {
        val firstColumn = element.select("div.col-md-5 h5.chapter-title-rtl a")
        val secondColumn = element.select("div.col-md-3")
        val thirdColumn = element.select("div.col-md-4")

        return SChapter.create().apply {
            name = firstColumn.text()
            scanlator = secondColumn.text()
            date_upload = parseChapterDate(thirdColumn.text())
            setUrlWithoutDomain(firstColumn.attr("href"))
        }
    }

    private fun parseChapterDate(date: String) : Long {
        return try {
            SimpleDateFormat("dd MMM. yyyy", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
                .set("Referer", "$baseUrl${chapter.url}".substringBeforeLast("/"))
                .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.select("div.viewer-cnt img.img-responsive")

        return pages.mapIndexed { i, element -> Page(i, "", element.absUrl("data-src"))}
    }

    override fun imageUrlParse(document: Document) = ""

    override fun searchMangaSelector() = throw Exception("This method should not be called!")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    override fun searchMangaNextPageSelector() = throw Exception("This method should not be called!")

    private fun removeLabel(text: String?): String = text!!.substringAfter(":")

    private fun Response.asJsonObject(): JsonObject = JSON_PARSER.parse(body()!!.string()).obj

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
        private val JSON_PARSER by lazy { JsonParser() }
    }
}
