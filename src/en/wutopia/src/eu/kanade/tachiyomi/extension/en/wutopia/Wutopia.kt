package eu.kanade.tachiyomi.extension.en.wutopia

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

class Wutopia : HttpSource() {

    override val name = "Wutopia"

    override val baseUrl = "https://www.wutopiacomics.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded")
        .add("platform", "10")

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val body = RequestBody.create(null, "pageNo=$page&pageSize=15&cartoonTypeId=&isFinish=&payState=&order=0")
        return POST("$baseUrl/mobile/cartoon-collection/search-fuzzy", headers, body)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val json = gson.fromJson<JsonObject>(response.body()!!.string())

        val mangas = json["list"].asJsonArray.map {
            SManga.create().apply {
                title = it["name"].asString
                url = it["id"].asString
                thumbnail_url = it["picUrlWebp"].asString
            }
        }

        return MangasPage(mangas, json["hasNext"].asBoolean)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val body = RequestBody.create(null, "type=8&pageNo=$page&pageSize=15")
        return POST("$baseUrl/mobile/home-page/query", headers, body)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = RequestBody.create(null, "pageNo=$page&pageSize=15&keyword=$query")
        return POST("$baseUrl/mobile/cartoon-collection/search-fuzzy", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        val body = RequestBody.create(null, "id=${manga.url}&linkId=0")
        return POST("$baseUrl/mobile/cartoon-collection/get", headers, body)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return gson.fromJson<JsonObject>(response.body()!!.string())["cartoon"].let { json ->
            SManga.create().apply {
                thumbnail_url = json["acrossPicUrlWebp"].asString
                author = json["author"].asString
                genre = json["cartoonTypes"].asJsonArray.joinToString { it["name"].asString }
                description = json["content"].asString
                title = json["name"].asString
                status = json["isFinishStr"].asString.toStatus()
            }
        }
    }

    private fun String.toStatus() = when (this) {
        "完结" -> SManga.COMPLETED
        "连载" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val body = RequestBody.create(null, "id=${manga.url}&pageSize=99999&pageNo=1&sort=0&linkId=0")
        return POST("$baseUrl/mobile/cartoon-collection/list-chapter", headers, body)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["list"].asJsonArray.map { json ->
            SChapter.create().apply {
                url = json["id"].asString
                name = json["name"].asString.let { if (it.isNotEmpty()) it else "Chapter " + json["chapterIndex"].asString }
                date_upload = json["modifyTime"].asLong
            }
        }.reversed()
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val body = RequestBody.create(null, "id=${chapter.url}&linkId=0")
        return POST("$baseUrl/mobile/chapter/get", headers, body)
    }

    override fun pageListParse(response: Response): List<Page> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["chapter"]["picList"].asJsonArray.mapIndexed { i, json ->
            Page(i, "", json["picUrl"].asString)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
