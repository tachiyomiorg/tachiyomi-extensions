package eu.kanade.tachiyomi.extension.ca.fansubscat

import com.github.salomonbrys.kotson.float
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class FansubsCat : HttpSource() {

    override val name = "Fansubs.cat"

    override val baseUrl = "https://manga.fansubs.cat"

    override val lang = "ca"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Tachiyomi/FansubsCat/${BuildConfig.VERSION_NAME}")

    override val client: OkHttpClient = network.client

    private val gson = Gson()

    private val apiBaseUrl = "https://api.fansubs.cat"

    private fun parseMangaFromJson(response: Response): MangasPage {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        val mangas = jsonObject["result"].asJsonArray.map { json ->
            SManga.create().apply {
                url = json["slug"].string
                title = json["name"].string
                thumbnail_url = json["thumbnail_url"].string
                author = json["author"].nullString
                description = json["synopsis"].nullString
                status = json["status"].string.toStatus()
                genre = json["genres"].nullString
            }
        }

        return MangasPage(mangas, mangas.size >= 20)
    }

    private fun parseChapterListFromJson(response: Response): List<SChapter> {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        return jsonObject["result"].asJsonArray.map { json ->
            SChapter.create().apply {
                url = json["id"].string
                name = json["title"].string
                chapter_number = json["number"].float
                scanlator = json["fansub"].string
                date_upload = json["created"].long
            }
        }
    }

    private fun parsePageListFromJson(response: Response): List<Page> {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        return jsonObject["result"].asJsonArray.mapIndexed { i, it ->
            Page(i, it["url"].asString, it["url"].asString)
        }
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiBaseUrl/manga/popular/$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiBaseUrl/manga/recent/$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$apiBaseUrl/manga/search/$page")!!.newBuilder()
            .addQueryParameter("query", query)
        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Details

    // Workaround to allow "Open in browser" to use the real URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(apiMangaDetailsRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    // Return the real URL for "Open in browser"
    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/${manga.url}", headers)

    private fun apiMangaDetailsRequest(manga: SManga): Request {
        return GET("$apiBaseUrl/manga/details/${manga.url.substringAfterLast('/')}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        return SManga.create().apply {
            url = jsonObject["result"]["slug"].string
            title = jsonObject["result"]["name"].string
            thumbnail_url = jsonObject["result"]["thumbnail_url"].string
            author = jsonObject["result"]["author"].nullString
            description = jsonObject["result"]["synopsis"].nullString
            status = jsonObject["result"]["status"].string.toStatus()
            genre = jsonObject["result"]["genres"].nullString
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("finished", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request = GET("$apiBaseUrl/manga/chapters/${manga.url.substringAfterLast('/')}", headers)

    override fun chapterListParse(response: Response): List<SChapter> = parseChapterListFromJson(response)

    // Pages

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiBaseUrl/manga/pages/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> = parsePageListFromJson(response)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")
}
