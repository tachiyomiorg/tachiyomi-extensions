package eu.kanade.tachiyomi.extension.all.ciayo

import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

abstract class Ciayo(override val lang: String) : HttpSource() {

    //Info
    override val name: String = "Ciayo Comics"
    override val baseUrl: String = "https://www.ciayo.com"
    private val apiUrl = "https://vueserion.ciayo.com/3.3/comics"
    override val supportsLatest: Boolean = true

    //Page Helpers
    private var next: String? = ""
    private var previous: String? = ""

    //Popular
    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body()!!.string()
        val json = JsonParser().parse(body)["c"]
        val data = json["data"].asJsonArray

        val mangas = data.map { jsonObject ->
            popularMangaFromJson(jsonObject)
        }

        previous = next
        next = json["meta"]["cursor"]["next"].nullString

        val hasNextPage = json["meta"]["more"].string == "true"

        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaRequest(page: Int): Request {
        when (page) {
            1 -> {
                previous = ""
                next = ""
            }
            2 -> previous = "null"
        }
        val url =
            "$apiUrl/?current=$next&previous=$previous&app=desktop&type=comic&count=15&language=$lang&with=image,genres"
        return GET(url)
    }

    private fun popularMangaFromJson(json: JsonElement): SManga = SManga.create().apply {
        title = json["title"].string
        setUrlWithoutDomain(json["share_url"].string)
        thumbnail_url = json["image"]["cover"].string
    }

    //Latest

    override fun latestUpdatesParse(response: Response): MangasPage {
        val body = response.body()!!.string()
        val json = JsonParser().parse(body)["c"]
        val data = json["data"].asJsonArray

        val mangas = data.map { jsonObject ->
            latestUpdatesFromJson(jsonObject)
        }

        previous = next
        next = json["meta"]["cursor"]["next"].nullString

        val hasNextPage = json["meta"]["more"].string == "true"

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        when (page) {
            1 -> {
                previous = ""
                next = ""
            }
            2 -> previous = "null"
        }
        val url =
            "$apiUrl/new-release?app=desktop&language=$lang&current=$next&previous=$previous&count=10&with=image,genres&type=comic"
        return GET(url)
    }

    private fun latestUpdatesFromJson(json: JsonElement): SManga = popularMangaFromJson(json)

    //Search

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)

    }

    private fun searchMangaSelector() = "div.ais-Hits li.ais-Hits-item"
    private fun searchMangaNextPageSelector() = "a[aria-label=Next page]"
    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src")
        element.select("a.comic-link").apply {
            title = this.text().trim()
            url = this.attr("href")
        }

    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/$lang/search?query=$query&page=$page")
    }

    //Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val script = document.select("script:containsdata(NEXT_DATA)").html()
        val data = script.substringAfter("__NEXT_DATA__ =").substringBefore("};").trim() + "}"
        val json = JsonParser().parse(data)["props"]["pageProps"]["comicProfile"]
        return SManga.create().apply {
            title = json["title"].string
            author = json["author"].string
            artist = author
            description = json["description"].string
            genre = json["genres"].asJsonArray.joinToString(", ") { it["name"].string }
            thumbnail_url = json["image"]["cover"].string
        }
    }

    //Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/$slug/chapters?current=&count=999&app=desktop&language=$lang&with=image,comic&sort=desc")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body()!!.string()
        val json = JsonParser().parse(body)["c"]
        val data = json["data"].asJsonArray
        return data.filterNot { it["status"].string == "coming-soon" }.map {
            SChapter.create().apply {
                name = "${it["episode"].string} - ${it["name"].string}"
                scanlator = "[${it["status"].string}]"
                setUrlWithoutDomain(it["share_url"].string)
                date_upload = it["release_date"].long * 1000
            }
        }

    }

    //Pages

    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        val document = response.asJsoup()
        document.select("div.chapterViewer img").forEach {
            add(Page(size, "", it.attr("abs:src")))
        }

    }

    override fun imageUrlParse(response: Response): String = throw Exception("ImgParse Not Used")

}
