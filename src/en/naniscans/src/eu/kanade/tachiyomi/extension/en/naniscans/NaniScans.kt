package eu.kanade.tachiyomi.extension.en.naniscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class NaniScans : HttpSource() {

    override val name = "NANI? Scans"

    override val baseUrl = "https://naniscans.com"

    override val lang = "en"

    override val supportsLatest = true

    override val versionId = 2

    private val dateParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    private val json: Json by injectLazy()

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val titlesJson = json.parseToJsonElement(response.body!!.string()).jsonArray

        val mangaList = titlesJson
            .mapNotNull {
                val manga = it.jsonObject

                if (manga["type"]!!.jsonPrimitive.content != "Comic") {
                    return@mapNotNull null
                }

                val date = manga["updatedAt"]!!.jsonPrimitive.content.let { datePrimitive ->
                    if (datePrimitive == "null") "2018-04-10T17:38:56" else datePrimitive
                }

                Pair(dateParser.parse(date)!!.time, getBareSManga(manga))
            }
            .sortedByDescending { it.first }
            .map { it.second }

        return MangasPage(mangaList, false)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/titles")

    override fun popularMangaParse(response: Response): MangasPage {
        val titlesJson = json.parseToJsonElement(response.body!!.string()).jsonArray

        val mangaList = titlesJson.mapNotNull {
            val manga = it.jsonObject

            if (manga["type"]!!.jsonPrimitive.content == "Comic") {
                getBareSManga(manga)
            } else {
                null
            }
        }

        return MangasPage(mangaList, false)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/titles/search?term=$query")

    // Workaround to allow "Open in browser" to use the real URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(chapterListRequest(manga)).asObservableSuccess().map { mangaDetailsParse(it).apply { initialized = true } }

    // Return the real URL for "Open in browser"
    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/titles/${manga.url}")

    override fun mangaDetailsParse(response: Response): SManga {
        val titleJson = json.parseToJsonElement(response.body!!.string()).jsonObject

        if (titleJson["type"]!!.jsonPrimitive.content != "Comic")
            throw UnsupportedOperationException("Tachiyomi only supports Comics.")

        return SManga.create().apply {
            title = titleJson["name"]!!.jsonPrimitive.content
            artist = titleJson["artist"]!!.jsonPrimitive.content
            author = titleJson["author"]!!.jsonPrimitive.content
            description = titleJson["synopsis"]!!.jsonPrimitive.content
            status = getStatus(titleJson["status"]!!.jsonPrimitive.content)
            thumbnail_url = "$baseUrl${titleJson["coverUrl"]!!.jsonPrimitive.content}"
            genre = titleJson["tags"]!!.jsonArray.joinToString { it.jsonPrimitive.content }
            url = titleJson["id"]!!.jsonPrimitive.content
        }
    }

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/api/titles/${manga.url}")

    override fun chapterListParse(response: Response): List<SChapter> {
        val titleJson = json.parseToJsonElement(response.body!!.string()).jsonObject

        if (titleJson["type"]!!.jsonPrimitive.content != "Comic")
            throw UnsupportedOperationException("Tachiyomi only supports Comics.")

        val chaptersJson = titleJson["chapters"]!!.jsonArray

        return titleJson["chapters"]!!.jsonArray.map {
            val chapter = it.jsonObject

            SChapter.create().apply {
                chapter_number = chapter["number"]!!.jsonPrimitive.content.toFloatOrNull() ?: -1f
                name = getChapterTitle(chapter)
                date_upload = dateParser.parse(chapter["releaseDate"]!!.jsonPrimitive.content)!!.time
                url = "${titleJson["id"]!!.jsonPrimitive.content} ${chapter["id"]!!.jsonPrimitive.content}"
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api/chapters/${chapter.url.substring(37, 73)}")

    override fun pageListParse(response: Response): List<Page> {
        val jsonObject = json.parseToJsonElement(response.body!!.string()).jsonObject

        return jsonObject["pages"]!!.jsonArray.map {
            val item = it.jsonObject
            Page(item["number"]!!.jsonPrimitive.int, "", "$baseUrl${item["pageUrl"]!!.jsonPrimitive.content}")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not Used.")

    private fun getStatus(status: String): Int = when (status) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun getChapterTitle(chapter: JsonObject): String {
        val chapterName = mutableListOf<String>()

        if (chapter["volume"]!!.jsonPrimitive.content != "null") {
            chapterName.add("Vol." + chapter["volume"]!!.jsonPrimitive.content)
        }

        if (chapter["number"]!!.jsonPrimitive.content != "null") {
            chapterName.add("Ch." + chapter["number"]!!.jsonPrimitive.content)
        }

        if (chapter["name"]!!.jsonPrimitive.content != "null") {
            if (chapterName.isNotEmpty()) {
                chapterName.add("-")
            }

            chapterName.add(chapter["name"]!!.jsonPrimitive.content)
        }

        if (chapterName.isEmpty()) {
            chapterName.add("Oneshot")
        }

        return chapterName.joinToString(" ")
    }

    private fun getBareSManga(manga: JsonObject): SManga = SManga.create().apply {
        title = manga["name"]!!.jsonPrimitive.content
        thumbnail_url = "$baseUrl${manga["coverUrl"]!!.jsonPrimitive.content}"
        url = manga["id"]!!.jsonPrimitive.content
    }
}
