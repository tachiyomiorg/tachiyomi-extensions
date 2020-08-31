package eu.kanade.tachiyomi.extension.all.elimangas

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response

open class EliMangasProvider(
    _name: String,
    private val srcId: Int,
    private val allCatId: Int,
    override val lang: String
) : HttpSource() {

    override val supportsLatest = false

    override val name = "$_name (via EliMangas)"

    override val baseUrl = "https://www.elimangas.com"

    private val gson = Gson()

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/mangas/top/$allCatId?page=$page")

    override fun popularMangaParse(response: Response): MangasPage {
        val json = gson.fromJson<JsonArray>(response.body()!!.string()).asJsonArray

        return MangasPage(json.map {
            SManga.create().apply {
                val id = it["id"].asString
                url = id
                title = it["name"].asString
                thumbnail_url = "https://www.elimangas.com/images/$id.jpg"
            }
        }, json.size() >= 30)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Unused")
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Unused")

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/mangas/search/$query?isCensored=false&provider=$srcId")
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/mangas/${manga.url}")

    override fun mangaDetailsParse(response: Response): SManga {
        return gson.fromJson<JsonObject>(response.body()!!.string()).let { json ->
            SManga.create().apply {
                title = json["name"].asString
                description = json["synopsis"].asString
                genre = json["categories"].asJsonArray.joinToString { it["label"].asString }
                status = if (json["isComplete"].asBoolean) SManga.COMPLETED else SManga.UNKNOWN
                thumbnail_url = "https://www.elimangas.com/images/${json["id"].asString}.jpg"
            }
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> =
        gson.fromJson<JsonObject>(response.body()!!.string())["chapters"].asJsonArray
            .map { json ->
                SChapter.create().apply {
                    url = json["id"].asInt.toString()
                    name = json["name"].asString
                    date_upload = json["timestamp"].asLong
                }
            }.reversed()

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api/mangas/images/${chapter.url}")

    override fun pageListParse(response: Response): List<Page> =
        gson.fromJson<JsonObject>(response.body()!!.string())["urls"].asJsonArray.mapIndexed { i, url -> Page(i, "", url.asString) }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Unused")
}
