package eu.kanade.tachiyomi.extension.all.cubari

import android.app.Application
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

open class Cubari(override val lang: String) : HttpSource() {

    final override val name = "Cubari"

    final override val baseUrl = "https://cubari.moe"

    final override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun headersBuilder() = Headers.Builder().apply {
        add(
            "User-Agent",
            "(Android ${Build.VERSION.RELEASE}; " +
                "${Build.MANUFACTURER} ${Build.MODEL}) " +
                "Tachiyomi/${BuildConfig.VERSION_NAME} " +
                Build.ID
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/", headers)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newBuilder()
            .addInterceptor(RemoteStorageUtils.HomeInterceptor())
            .build()
            .newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response -> latestUpdatesParse(response) }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.parseToJsonElement(response.body!!.string()).jsonArray
        return parseMangaList(result, SortType.UNPINNED)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/", headers)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newBuilder()
            .addInterceptor(RemoteStorageUtils.HomeInterceptor())
            .build()
            .newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response -> popularMangaParse(response) }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.parseToJsonElement(response.body!!.string()).jsonArray
        return parseMangaList(result, SortType.PINNED)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response -> mangaDetailsParse(response, manga) }
    }

    // Called when the series is loaded, or when opening in browser
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        throw Exception("Unused")
    }

    private fun mangaDetailsParse(response: Response, manga: SManga): SManga {
        val result = json.parseToJsonElement(response.body!!.string()).jsonObject
        return parseManga(result, manga)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response -> chapterListParse(response, manga) }
    }

    // Gets the chapter list based on the series being viewed
    override fun chapterListRequest(manga: SManga): Request {
        val urlComponents = manga.url.split("/")
        val source = urlComponents[2]
        val slug = urlComponents[3]

        return GET("$baseUrl/read/api/$source/series/$slug/", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw Exception("Unused")
    }

    // Called after the request
    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val res = response.body!!.string()
        return parseChapterList(res, manga)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return when {
            chapter.url.contains("/chapter/") -> {
                client.newCall(pageListRequest(chapter))
                    .asObservableSuccess()
                    .map { response ->
                        directPageListParse(response)
                    }
            }
            else -> {
                client.newCall(pageListRequest(chapter))
                    .asObservableSuccess()
                    .map { response ->
                        seriesJsonPageListParse(response, chapter)
                    }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return when {
            chapter.url.contains("/chapter/") -> {
                GET("$baseUrl${chapter.url}", headers)
            }
            else -> {
                val url = chapter.url.split("/")
                val source = url[2]
                val slug = url[3]

                GET("$baseUrl/read/api/$source/series/$slug/", headers)
            }
        }
    }

    private fun directPageListParse(response: Response): List<Page> {
        val res = response.body!!.string()
        val pages = json.parseToJsonElement(res).jsonArray

        return pages.mapIndexed { i, jsonEl ->
            val page = if (jsonEl is JsonObject) {
                jsonEl.jsonObject["src"]!!.jsonPrimitive.content
            } else {
                jsonEl.jsonPrimitive.content
            }

            Page(i, "", page)
        }
    }

    private fun seriesJsonPageListParse(response: Response, chapter: SChapter): List<Page> {
        val jsonObj = json.parseToJsonElement(response.body!!.string()).jsonObject
        val groups = jsonObj["groups"]!!.jsonObject
        val groupMap = groups.entries
            .map { Pair(it.value.jsonPrimitive.content, it.key) }
            .toMap()

        val chapters = jsonObj["chapters"]!!.jsonObject

        val pages = if (chapters[chapter.chapter_number.toString()] != null) {
            chapters[chapter.chapter_number.toString()]!!
                .jsonObject["groups"]!!
                .jsonObject[groupMap[chapter.scanlator]]!!
                .jsonArray
        } else {
            chapters[chapter.chapter_number.toInt().toString()]!!
                .jsonObject["groups"]!!
                .jsonObject[groupMap[chapter.scanlator]]!!
                .jsonArray
        }

        return pages.mapIndexed { i, jsonEl ->
            val page = if (jsonEl is JsonObject) {
                jsonEl.jsonObject["src"]!!.jsonPrimitive.content
            } else {
                jsonEl.jsonPrimitive.content
            }

            Page(i, "", page)
        }
    }

    // Stub
    override fun pageListParse(response: Response): List<Page> {
        throw Exception("Unused")
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PROXY_PREFIX) -> {
                val trimmedQuery = query.removePrefix(PROXY_PREFIX)
                // Only tag for recently read on search
                client.newBuilder()
                    .addInterceptor(RemoteStorageUtils.TagInterceptor())
                    .build()
                    .newCall(searchMangaRequest(page, trimmedQuery, filters))
                    .asObservableSuccess()
                    .map { response ->
                        searchMangaParse(response, trimmedQuery)
                    }
            }
            else -> throw Exception(SEARCH_FALLBACK_MSG)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        try {
            val queryFragments = query.split("/")
            val source = queryFragments[0]
            val slug = queryFragments[1]

            return GET("$baseUrl/read/api/$source/series/$slug/", headers)
        } catch (e: Exception) {
            throw Exception(SEARCH_FALLBACK_MSG)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw Exception("Unused")
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val result = json.parseToJsonElement(response.body!!.string()).jsonObject
        return parseSearchList(result, query)
    }

    // ------------- Helpers and whatnot ---------------

    private fun parseChapterList(payload: String, manga: SManga): List<SChapter> {
        val jsonObj = json.parseToJsonElement(payload).jsonObject
        val groups = jsonObj["groups"]!!.jsonObject
        val chapters = jsonObj["chapters"]!!.jsonObject
        val seriesSlug = jsonObj["slug"]!!.jsonPrimitive.content

        val seriesPrefs = Injekt.get<Application>().getSharedPreferences("source_${id}_updateTime:$seriesSlug", 0)
        val seriesPrefsEditor = seriesPrefs.edit()

        val chapterList = chapters.entries.flatMap { chapterEntry ->
            val chapterNum = chapterEntry.key
            val chapterObj = chapterEntry.value.jsonObject
            val chapterGroups = chapterObj["groups"]!!.jsonObject
            val volume = chapterObj["volume"]!!.jsonPrimitive.content

            chapterGroups.entries.map { groupEntry ->
                val groupNum = groupEntry.key

                SChapter.create().apply {
                    scanlator = groups[groupNum]!!.jsonPrimitive.content
                    chapter_number = chapterNum.toFloatOrNull() ?: -1f

                    if (chapterObj["release_date"]!!.jsonObject[groupNum] != null) {
                        val temp = chapterObj["release_date"]!!.jsonObject[groupNum]!!.jsonPrimitive.double
                        date_upload = temp.toLong() * 1000
                    } else {
                        val currentTimeMillis = System.currentTimeMillis()

                        if (!seriesPrefs.contains(chapterNum)) {
                            seriesPrefsEditor.putLong(chapterNum, currentTimeMillis)
                        }

                        date_upload = seriesPrefs.getLong(chapterNum, currentTimeMillis)
                    }

                    name = if (volume.isNotEmpty() && volume != "Uncategorized") {
                        // Output "Vol. 1 Ch. 1 - Chapter Name"
                        "Vol. " + chapterObj["volume"]!!.jsonPrimitive.content + " Ch. " +
                            chapterNum + " - " + chapterObj["title"]!!.jsonPrimitive.content
                    } else {
                        // Output "Ch. 1 - Chapter Name"
                        "Ch. " + chapterNum + " - " + chapterObj["title"]!!.jsonPrimitive.content
                    }

                    url = if (chapterGroups[groupNum] is JsonArray) {
                        "${manga.url}/$chapterNum/$groupNum"
                    } else {
                        chapterGroups[groupNum]!!.jsonPrimitive.content
                    }
                }
            }
        }

        seriesPrefsEditor.apply()

        return chapterList.sortedByDescending { it.chapter_number }
    }

    private fun parseMangaList(payload: JsonArray, sortType: SortType): MangasPage {
        val mangaList = payload.mapNotNull { jsonEl ->
            val jsonObj = jsonEl.jsonObject
            val pinned = jsonObj["pinned"]!!.jsonPrimitive.boolean

            if (sortType == SortType.PINNED && pinned) {
                parseManga(jsonObj)
            } else if (sortType == SortType.UNPINNED && !pinned) {
                parseManga(jsonObj)
            } else {
                null
            }
        }

        return MangasPage(mangaList, false)
    }

    private fun parseSearchList(payload: JsonObject, query: String): MangasPage {
        val tempManga = SManga.create().apply {
            url = "/read/$query"
        }

        val mangaList = listOf(parseManga(payload, tempManga))

        return MangasPage(mangaList, false)
    }

    private fun parseManga(jsonObj: JsonObject, mangaReference: SManga? = null): SManga =
        SManga.create().apply {
            title = jsonObj["title"]!!.jsonPrimitive.content
            artist = jsonObj["artist"]?.jsonPrimitive?.content ?: ARTIST_FALLBACK
            author = jsonObj["author"]?.jsonPrimitive?.content ?: AUTHOR_FALLBACK
            description = jsonObj["description"]?.jsonPrimitive?.content ?: DESCRIPTION_FALLBACK
            url = mangaReference?.url ?: jsonObj["url"]!!.jsonPrimitive.content
            thumbnail_url = jsonObj["coverUrl"]?.jsonPrimitive?.content
                ?: jsonObj["cover"]?.jsonPrimitive?.content ?: ""
        }

    // ----------------- Things we aren't supporting -----------------

    override fun imageUrlParse(response: Response): String {
        throw Exception("imageUrlParse not supported.")
    }

    companion object {
        const val PROXY_PREFIX = "cubari:"
        const val AUTHOR_FALLBACK = "Unknown"
        const val ARTIST_FALLBACK = "Unknown"
        const val DESCRIPTION_FALLBACK = "No description."
        const val SEARCH_FALLBACK_MSG = "Unable to parse. Is your query in the format of $PROXY_PREFIX<source>/<slug>?"

        enum class SortType {
            PINNED,
            UNPINNED
        }
    }
}
