package eu.kanade.tachiyomi.extension.ar.gmanga

import com.github.salomonbrys.kotson.addAll
import com.github.salomonbrys.kotson.addProperty
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullString
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

class Gmanga : HttpSource() {

    private val domain: String = "gmanga.me"

    override val baseUrl: String = "https://$domain"

    override val lang: String = "ar"

    override val name: String = "GMANGA"

    override val supportsLatest: Boolean = true

    private val gson = Gson()

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/mangas/$mangaId/releases", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = decryptResponse(response)
        val allChapters = data["rows"][0]["rows"].asJsonArray.map { it.asJsonArray }
        val chaptersByNumber = allChapters.groupBy { it.asJsonArray[6].asFloat }

        return chaptersByNumber.map { (number: Float, versions: List<JsonArray>) ->
            SChapter.create().apply {
                chapter_number = number

                val mostViewedScan = versions.maxByOrNull { it[4].asLong }!!
                val chapterName = mostViewedScan[8].asString.let { if (it.trim() != "") " - $it" else "" }

                url = "/r/${mostViewedScan[0]}"
                name = "${number.toInt()}$chapterName"
                date_upload = mostViewedScan[3].asLong * 1000
                scanlator = mostViewedScan[10].asString
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = gson.fromJson<JsonObject>(response.asJsoup().select(".js-react-on-rails-component").html())
        return MangasPage(
            data["mangaDataAction"]["newMangas"].asJsonArray.map {
                SManga.create().apply {
                    url = "/mangas/${it["id"].asString}"
                    title = it["title"].asString
                    val thumbnail = "medium_${it["cover"].asString.substringBeforeLast(".")}.webp"
                    thumbnail_url = "https://media.$domain/uploads/manga/cover/${it["id"].asString}/$thumbnail"
                }
            },
            false
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/mangas/latest", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = gson.fromJson<JsonObject>(response.asJsoup().select(".js-react-on-rails-component").html())
        val mangaData = data["mangaDataAction"]["mangaData"].asJsonObject
        return SManga.create().apply {
            description = mangaData["summary"].nullString ?: ""
            artist = mangaData["artists"].asJsonArray.joinToString(", ") { it.asJsonObject["name"].asString }
            author = mangaData["authors"].asJsonArray.joinToString(", ") { it.asJsonObject["name"].asString }
            genre = mangaData["categories"].asJsonArray.joinToString(", ") { it.asJsonObject["name"].asString }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request().url().toString()
        val data = gson.fromJson<JsonObject>(response.asJsoup().select(".js-react-on-rails-component").html())
        val releaseData = data["readerDataAction"]["readerData"]["release"].asJsonObject
        return releaseData["webp_pages"].asJsonArray.map { it.asString }.mapIndexed { index, pageUri ->
            Page(index, "$url#page_$index", "https://media.$domain/uploads/releases/${releaseData["storage_key"].asString}/mq_webp/$pageUri")
        }
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList())

    override fun searchMangaParse(response: Response): MangasPage {
        val data = decryptResponse(response)
        val mangas = data["mangas"].asJsonArray
        return MangasPage(
            mangas.asJsonArray.map {
                SManga.create().apply {
                    url = "/mangas/${it["id"].asString}"
                    title = it["title"].asString
                    val thumbnail = "medium_${it["cover"].asString.substringBeforeLast(".")}.webp"
                    thumbnail_url = "https://media.$domain/uploads/manga/cover/${it["id"].asString}/$thumbnail"
                }
            },
            mangas.size() == 50
        )
    }

    private fun decryptResponse(response: Response): JsonObject {
        val encryptedData = gson.fromJson<JsonObject>(response.body()!!.string())["data"].asString
        val decryptedData = decrypt(encryptedData)
        return gson.fromJson(decryptedData)
    }

    private fun buildSearchRequestBody(page: Int, query: String = ""): RequestBody {

        val body = JsonObject().apply {
            addProperty("title", query)
            addProperty("oneshot", false)
            addProperty("page", page)
            addProperty(
                "manga_types",
                JsonObject().apply {
                    addProperty("include", JsonArray().apply { addAll("1", "2", "3", "4", "5", "6", "7", "8") })
                    addProperty("exclude", JsonArray())
                }
            )
            addProperty(
                "story_status",
                JsonObject().apply {
                    addProperty("include", JsonArray())
                    addProperty("exclude", JsonArray())
                }
            )
            addProperty(
                "translation_status",
                JsonObject().apply {
                    addProperty("include", JsonArray())
                    addProperty("exclude", JsonArray().apply { addAll("3") })
                }
            )
            addProperty(
                "categories",
                JsonObject().apply {
                    addProperty("include", JsonArray().apply { addAll(JsonNull.INSTANCE) })
                    addProperty("exclude", JsonArray())
                }
            )
            addProperty(
                "chapters",
                JsonObject().apply {
                    addProperty("min", "")
                    addProperty("max", "")
                }
            )
            addProperty(
                "dates",
                JsonObject().apply {
                    addProperty("start", JsonNull.INSTANCE)
                    addProperty("end", JsonNull.INSTANCE)
                }
            )
        }

        return RequestBody.create(MEDIA_TYPE, body.toString())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = buildSearchRequestBody(page)
        return POST("$baseUrl/api/mangas/search", headers, body)
    }

    companion object {
        private val MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
    }
}
