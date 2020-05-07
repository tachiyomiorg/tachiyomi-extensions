package eu.kanade.tachiyomi.extension.en.readmanhwa

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class ReadManhwa : HttpSource() {

    override val name = "ReadManhwa"

    override val baseUrl = "https://www.readmanhwa.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    private fun parseMangaFromJson(response: Response): MangasPage {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        val mangas = jsonObject["data"].asJsonArray.map { json ->
            SManga.create().apply {
                title = json["title"].string
                thumbnail_url = json["image_url"].string
                url = json["slug"].string
            }
        }

        return MangasPage(mangas, jsonObject["current_page"].int < jsonObject["last_page"].int)
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/comics?page=$page&q=&sort=popularity&order=desc&duration=week", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/comics?page=$page&q=&sort=uploaded_at&order=desc&duration=day", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/api/comics?page=$page&q=$query&sort=uploaded_at&order=desc&duration=day", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/api/comics/${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        return SManga.create().apply {
            description = jsonObject["description"].string
            status = jsonObject["status"].string.toStatus()
            thumbnail_url = jsonObject["image_url"].string
            genre = jsonObject["tags"].asJsonArray.joinToString { it["name"].string }
            artist = jsonObject["artists"].asJsonArray.joinToString { it["name"].string }
            author = jsonObject["authors"].asJsonArray.joinToString { it["name"].string }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("complete", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga.url)
            }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/api/comics/${manga.url}/chapters", headers)
    }

    private fun chapterListParse(response: Response, titleSlug: String): List<SChapter> {
        return gson.fromJson<JsonArray>(response.body()!!.string()).map { json ->
            SChapter.create().apply {
                name = json["name"].string
                url = "$titleSlug/${json["slug"].string}"
                date_upload = json["added_at"].string.let { dateString ->
                    if (dateString.contains("ago")) {
                        val trimmedDate = dateString.substringBefore(" ago").removeSuffix("s").split(" ")
                        val calendar = Calendar.getInstance()
                        when (trimmedDate[1]) {
                            "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }.timeInMillis
                            "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }.timeInMillis
                            "minute" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }.timeInMillis
                            "second" -> calendar.apply { add(Calendar.SECOND, -trimmedDate[0].toInt()) }.timeInMillis
                            else -> 0L
                        }
                    } else {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString).time
                    }
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/api/comics/${chapter.url}/images", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["images"].asJsonArray.mapIndexed { i, json ->
            Page(i, "", json["source_url"].string)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")
}
