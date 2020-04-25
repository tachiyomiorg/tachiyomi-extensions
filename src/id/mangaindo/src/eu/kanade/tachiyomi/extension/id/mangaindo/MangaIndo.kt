package eu.kanade.tachiyomi.extension.id.mangaindo

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaIndo : ParsedHttpSource() {

    override val name = "Manga Indo"

    override val baseUrl = "https://mangaindo.web.id"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/cpt/products/?limit=20&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun popularMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return MangasPage(super.latestUpdatesParse(response).mangas.distinctBy { it.url }, false)
    }

    override fun latestUpdatesSelector() = "li.rpwe-li a"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.text().substringBeforeLast("–").trim()
            setUrlWithoutDomain(element.attr("href").substringBeforeLast("-indonesia"))
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/api/cpt/products/?limit=20&page=$page&post_title=$query", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return gson.fromJson<JsonObject>(response.body()!!.string()).let { json ->
            val mangas = json["data"].asJsonArray.map { data ->
                SManga.create().apply {
                    title = data["post"]["post_title"].asString
                    url = "/${data["post"]["post_name"].asString}/"
                    thumbnail_url = data["acf"]["m-cover"]["value"].asString
                }
            }
            MangasPage(mangas, json["page"].int < json["total_page"].int && json["data"].asJsonArray.count() == 20)
        }
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return document.select("div#main").let { info ->
            SManga.create().apply {
                title = info.select("h2").text()
                author = info.select("span#m-author").text()
                artist = info.select("span#m-artist").text()
                status = info.select("span#m-status").text().toStatus()
                genre = info.select("span#m-genre a").joinToString { it.text() }
                description = info.select("span#m-synopsis").text()
                thumbnail_url = info.select("div#m-cover img").attr("abs:src")
            }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "ul.lcp_catlist li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = parseChapterDate(element.select("span").text())
        }
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).parse(date).time
        } catch (e: Exception) {
            0L
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.entry-content img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
