package eu.kanade.tachiyomi.extension.ru.risensteam

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class RisensTeam : HttpSource() {

    override val name = "Risens Team"

    override val baseUrl = "https://risens.team"

    override val lang = "ru"

    override val supportsLatest = false

    override val versionId: Int = 2

    private val gson by lazy { Gson() }

    // Popular (source only returns manga sorted by latest)

    override fun popularMangaRequest(page: Int): Request {
        return GET("https://risens.team/api/title/list?type=1", headers)
    }

    private fun mangaFromJson(json: JsonElement): SManga {
        return SManga.create().apply {
            url = "${json["id"].int}/${json["furl"].string}"
            title = json["title"].string
            thumbnail_url = baseUrl + json["poster"].string
            description = json["description"].nullString
            status = try { if (json["active"].int == 1) SManga.ONGOING else SManga.UNKNOWN } catch (_: Exception) { SManga.UNKNOWN }
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = gson.fromJson<JsonArray>(response.body()!!.string())
            .map { json -> mangaFromJson(json) }

        return MangasPage(mangas, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val rbody = RequestBody.create(MediaType.parse("application/json;charset=utf-8"), """{"queryString":"$query","limit":3}""")
        return POST("$baseUrl/api/title/search", headers, rbody)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(apiMangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun apiMangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/api/title/show/${manga.url.substringBefore("/")}")
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/title/${manga.url}")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return mangaFromJson(gson.fromJson<JsonObject>(response.body()!!.string()))
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request = apiMangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["entities"].asJsonArray.map { json ->
            SChapter.create().apply {
                url = json["id"].int.toString()
                name = listOfNotNull(json["label"].nullString, json["name"].nullString).joinToString(" - ")
                date_upload = json["updated_at"].toDate()
            }
        }
    }

    private val simpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    private fun JsonElement.toDate(): Long {
        val date = this.nullString ?: return 0
        return try {
            simpleDateFormat.parse(date)?.time ?: 0
        } catch (e: ParseException) {
            0
        }
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/api/yandex/chapter/${chapter.url}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return gson.fromJson<JsonArray>(response.body()!!.string())
            .mapIndexed { i, json -> Page(i, "", json.string) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")
}
