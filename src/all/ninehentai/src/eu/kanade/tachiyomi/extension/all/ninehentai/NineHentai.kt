package eu.kanade.tachiyomi.extension.all.ninehentai

import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLEncoder
import java.util.*

open class NineHentai : ParsedHttpSource() {

    final override val baseUrl = "https://9hentai.com"

    override val name = "NineHentai"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return POST(baseUrl + SEARCH_URL, headers, buildRequestBody(page = page, sort = 1))
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return POST(baseUrl + SEARCH_URL, headers, buildRequestBody(page = page))
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { response ->
                    popularMangaParse(response)
                }
    }


    override fun popularMangaParse(response: Response): MangasPage {
        val list = getMangaList(response)
        return MangasPage(list, list.isNotEmpty())
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
                .asObservableSuccess()
                .map { response ->
                    latestUpdatesParse(response)
                }
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    private fun getMangaList(response: Response): List<SManga> {
        val jsonData = response.body()!!.string()
        val jsonObject = JsonParser().parse(jsonData).asJsonObject
        val results = jsonObject.getAsJsonArray("results")
        return parseSearch(results.toList())
    }

    private fun parseSearch(jsonArray: List<JsonElement>): List<SManga> {
        val mutableList = mutableListOf<SManga>()
        jsonArray.forEach { json ->
            val manga = SManga.create()
            val id = json["id"].string
            manga.url = "$baseUrl/g/$id"
            manga.title = json["title"].string
            manga.thumbnail_url = json["image_server"].string + id + "/" + "cover.jpg"
            mutableList.add(manga)
        }
        return mutableList
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(mangaDetailsRequest(manga))
                .asObservableSuccess()
                .map { response ->
                    chapterListParse(response)
                }
    }

    private fun getChapter(response: Response): SChapter {
        val jsonData = response.body()!!.string()
        val jsonObject = JsonParser().parse(jsonData).asJsonObject
        val jsonArray = jsonObject.getAsJsonObject("results")

        val sChapter = SChapter.create()

        jsonArray.let { json ->
            val id = json["id"].string
            sChapter.url = "$baseUrl/g/$id"
            sChapter.name = "chapter"
            //api doesnt return date so setting to current date for now
            sChapter.date_upload = Date().time
        }
        return sChapter

    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return POST(baseUrl + SEARCH_URL, headers, buildRequestBody(query, page))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonData = response.body()!!.string()
        val jsonObject = JsonParser().parse(jsonData).asJsonObject
        val results = jsonObject.getAsJsonObject("results")
        return parseSearch(listOf(results))[0]
    }

    override fun chapterListParse(response: Response): List<SChapter> = listOf(getChapter(response))

    override fun pageListParse(document: Document) = throw Exception("Not used")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val mangaId = chapter.url.substringAfter("/g/").toInt()

        return client.newCall(POST(baseUrl + MANGA_URL, headers, buildIdBody(mangaId)))
                .asObservableSuccess()
                .map { response ->
                    pageListParse(response)
                }
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsonData = response.body()!!.string()
        val jsonObject = JsonParser().parse(jsonData).asJsonObject
        val jsonArray = jsonObject.getAsJsonObject("results")
        var imageUrl: String
        var totalPages: Int
        var mangaId: String
        jsonArray.let { json ->
            mangaId = json["id"].string
            imageUrl = json["image_server"].string + mangaId + "/"
            totalPages = json["total_page"].int
        }
        val pages = mutableListOf<Page>()

        for (i in 1..totalPages) {
            pages.add(Page(pages.size, "", "$imageUrl$i.jpg"))
        }

        return pages
    }

    private fun buildRequestBody(searchText: String = "", page: Int = 0, sort: Int = 0): RequestBody {
        //in the future switch this to dtos and actually build the json.  This is just a work around for
        //initial release, then you can have actual tag searching etc
        val json = """{"search":{"text":"${URLEncoder.encode(searchText, "UTF-8")}","page":$page,"sort":$sort,"pages":{"range":[0,2000]},"tag":{"text":"","type":1,"tags":[],"items":{"included":[],"excluded":[]}}}}"""
        return RequestBody.create(MEDIA_TYPE, json)
    }

    override fun mangaDetailsRequest(smanga: SManga): Request {
        val id = smanga.url.substringAfter("/g/").toInt()
        return POST(baseUrl + MANGA_URL, headers, buildIdBody(id))
    }

    private fun buildIdBody(id: Int): RequestBody {
        val dto = eu.kanade.tachiyomi.extension.all.ninehentai.id(id)
        return RequestBody.create(MEDIA_TYPE, Gson().toJson(dto))
    }

    override fun imageUrlParse(document: Document): String = ""

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    override fun chapterListSelector(): String = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun popularMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    companion object {
        private val MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
        private const val SEARCH_URL = "/api/getBook"
        private const val MANGA_URL = "/api/getBookByID"
    }
}