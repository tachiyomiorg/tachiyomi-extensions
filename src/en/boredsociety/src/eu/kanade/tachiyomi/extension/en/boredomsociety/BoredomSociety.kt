package eu.kanade.tachiyomi.extension.en.boredomsociety

import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class BoredomSociety : ParsedHttpSource() {

    override val name = "Boredom Society"

    override val baseUrl = "https://boredomsociety.xyz"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl + ALL_URL, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { response ->
                    popularMangaParse(response)
                }
    }


    override fun popularMangaParse(response: Response): MangasPage {
        val jsonArray = getJsonArray(response)
        val list = parseData(jsonArray.toList())
        return MangasPage(list, false)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
                .asObservableSuccess()
                .map { response ->
                    latestUpdatesParse(response)
                }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val jsonArray = getJsonArray(response)
        val sortedJson = jsonArray.sortedBy { it["last_updated"].long }
        val list = parseData(sortedJson)
        return MangasPage(list, false)
    }

    private fun getJsonArray(response: Response): JsonArray {
        val jsonData = response.body()!!.string()
        return JsonParser().parse(jsonData).asJsonArray
    }

    private fun parseData(jsonArray: List<JsonElement>): List<SManga> {
        var mutableList = mutableListOf<SManga>()
        jsonArray.forEach { json ->
            val manga = SManga.create()
            manga.url = MANGA_URL + json["id"].string
            json["title_name"].string
            manga.title = json["title_name"].string
            manga.description = cleanString(json["title_desc"].string)
            manga.status = getStatus(json["status"].string)
            manga.thumbnail_url = "https://" + json["cover_url"].string

            mutableList.add(manga)
        }
        return mutableList
    }

    private fun parseChapter(jsonElement: JsonElement): SChapter {
        val sChapter = SChapter.create()
        sChapter.url = CHAPTER_URL + jsonElement["id"].string
        sChapter.date_upload = jsonElement["creation_timestamp"].long * 1000
        val chapterName = mutableListOf<String>()

        if (!jsonElement["chapter_name"].string.startsWith("Chapter", true)) {
            if (jsonElement["chapter_volume"].string?.isNotBlank()) {
                chapterName.add("Vol: " + jsonElement["chapter_volume"].string)
            }
            if (jsonElement["chapter_number"].string?.isNotBlank()) {
                chapterName.add("Ch: " + jsonElement["chapter_number"].string + " - ")
            }
        }
        chapterName.add(jsonElement["chapter_name"].string)
        sChapter.name = cleanString(chapterName.joinToString(" "))
        return sChapter
    }

    private fun getStatus(status: String): Int {
        return when (status) {
            "Ongoing" -> SManga.ONGOING
            "Finished" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl$ALL_URL$query")!!.newBuilder()
        return GET(url.toString(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    private fun getJsonObject(response: Response): JsonObject {
        val jsonData = response.body()!!.string()
        return JsonParser().parse(jsonData).asJsonObject
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonObject = getJsonObject(response)
        val list = parseData(listOf(jsonObject))
        return list[0]
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = getJsonObject(response)
        var mutableChapters = mutableListOf<SChapter>()
        json["chapters"].asJsonArray.forEach { it ->
            mutableChapters.add(parseChapter(it))
        }
        return mutableChapters
    }


    override fun pageListParse(document: Document) = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> {
        val json = getJsonObject(response)

        val pages = mutableListOf<Page>()
        val array = json["page_url"].asJsonArray

        array.forEach {
            val url = "https://${it.asString}"
            pages.add(Page(pages.size, "", url))
        }

        return pages
    }

    private fun cleanString(description: String): String {
        return Jsoup.parseBodyFragment(description
                .replace("[list]", "")
                .replace("[/list]", "")
                .replace("[*]", "")
                .replace("""\[(\w+)[^\]]*](.*?)\[/\1]""".toRegex(), "$2")).text()
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
        private const val MANGA_URL = "/api/title/"
        private const val ALL_URL = "/api/titles/"
        private const val CHAPTER_URL = "/api/chapter/"
    }

}
