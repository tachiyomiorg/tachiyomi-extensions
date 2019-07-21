package eu.kanade.tachiyomi.extension.en.guya

import android.app.Application
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import okhttp3.*
import org.json.JSONObject
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

open class Guya() : ConfigurableSource, HttpSource() {

    override val name = "Guya"
    override val baseUrl = "https://guya.moe"
    override val supportsLatest = false
    override val lang = "en"

    private val SCANLATORS = HashMap<String, String>()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }
    private val SCANLATOR_PREFERENCE = "SCANLATOR_PREFERENCE"

    init {
        // Update the scanlator list if we need to on initialization
        updateScanlators()
    }

    // Request builder for the "browse" page of the manga
    override fun popularMangaRequest(page: Int): Request {
        updateScanlators()
        return GET("$baseUrl/api/get_all_series")
    }

    // Gets the response object from the request
    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.body()!!.string()
        return parseManga(JSONObject(res))
    }

    // Overridden to use our overload
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        updateScanlators()
        return clientBuilder().newCall(GET("$baseUrl/api/get_all_series/"))
            .asObservableSuccess()
            .map {response ->
                mangaDetailsParse(response, manga)
            }
    }

    // Called when the series is loaded, or when opening in browser
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/reader/series/" + manga.url)
    }

    // Stub
    override fun mangaDetailsParse(response: Response): SManga {
        throw Exception("Unused")
    }

    private fun mangaDetailsParse(response: Response, manga: SManga): SManga {
        val res = response.body()!!.string()
        return parseMangaFromJson(JSONObject(res).getJSONObject(manga.title), manga.title)
    }

    // Gets the chapter list based on the series being viewed
    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/api/series/" + manga.url)
    }

    // Called after the request
    override fun chapterListParse(response: Response): List<SChapter> {
        val res = response.body()!!.string()
        return parseChapterList(res)
    }

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        updateScanlators()
        return clientBuilder().newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response, chapter)
            }
    }

    // Stub
    override fun pageListParse(response: Response): List<Page> {
        throw Exception("Unused")
    }

    private fun pageListParse(response: Response, chapter: SChapter): List<Page> {
        val res = response.body()!!.string()

        val json = JSONObject(res)
        val chapterNum = chapter.name.split(" - ")[0]
        val pages = json.getJSONObject("chapters")
            .getJSONObject(chapterNum)
            .getJSONObject("groups")
        val metadata = JSONObject()

        metadata.put("chapter", chapterNum)
        metadata.put("scanlator", getIdFromScanlatorName(chapter.scanlator.toString()))
        metadata.put("slug", json.getString("slug"))
        metadata.put("folder", json.getJSONObject("chapters")
            .getJSONObject(chapterNum)
            .getString("folder"))

        return parsePageFromJson(pages, metadata)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/api/get_all_series")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw Exception("Unused.")
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val res = response.body()!!.string()
        var json = JSONObject(res)
        val truncatedJSON = JSONObject()

        val iter = json.keys()

        while (iter.hasNext()) {
            val candidate = iter.next()
            if (candidate.contains(query.toRegex(RegexOption.IGNORE_CASE))) {
                truncatedJSON.put(candidate, json.get(candidate))
            }
        }

        return parseManga(truncatedJSON)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val preference = ListPreference(screen.context).apply {
            key = "preferred_scanlator"
            title = "Preferred scanlator"
            var scanlators = arrayOf<String>()
            var scanlatorsIndex = arrayOf<String>()
            for (key in SCANLATORS.keys) {
                scanlators += SCANLATORS[key].toString()
                scanlatorsIndex += key
            }
            entries = scanlators
            entryValues = scanlatorsIndex
            summary = "%s"
            this.setDefaultValue(1)

            setOnPreferenceChangeListener{_, newValue ->
                val selected = newValue.toString()
                val index = (this.findIndexOfValue(selected) + 1).toString()
                preferences.edit().putString(SCANLATOR_PREFERENCE, index).commit()
            }
        }

        screen.addPreference(preference)
    }

    // ------------- Helpers and whatnot ---------------

    private fun parseChapterList(payload: String): List<SChapter> {
        val response = JSONObject(payload)
        val chapters = response.getJSONObject("chapters")

        val chapterList = ArrayList<SChapter>()

        val iter = chapters.keys()

        while (iter.hasNext()) {
            val chapter = iter.next()
            val chapterObj = chapters.getJSONObject(chapter)
            chapterList.add(parseChapterFromJson(chapterObj, chapter, response.getString("slug")))
        }

        return chapterList.reversed()
    }

    // Helper function to get all the listings
    private fun parseManga(payload: JSONObject) : MangasPage {
        val mangas = ArrayList<SManga>()

        val iter = payload.keys()

        while (iter.hasNext()) {
            val series = iter.next()
            val json = payload.getJSONObject(series)
            val manga = parseMangaFromJson(json, series)
            mangas.add(manga)
        }

        return MangasPage(mangas, false)
    }

    // Takes a json of the manga to parse
    private fun parseMangaFromJson(json: JSONObject, title: String): SManga {
        val manga = SManga.create()
        manga.title = title
        manga.artist = json.getString("artist")
        manga.author = json.getString("author")
        manga.description = json.getString("description")
        manga.url = json.getString("slug")
        manga.thumbnail_url = "$baseUrl/" + json.getString("cover")
        return manga
    }

    private fun parseChapterFromJson(json: JSONObject, num: String, slug: String): SChapter {
        val chapter = SChapter.create()

        // Get the scanlator info based on group ranking; do it first since we need it later
        val firstGroupId = getBestScanlator(json.getJSONObject("groups"))
        chapter.scanlator = SCANLATORS[firstGroupId]
        chapter.name = num + " - " + json.getString("title")
        chapter.chapter_number = num.toFloat()
        chapter.url = "/api/series/$slug/$num/1"

        return chapter
    }

    private fun parsePageFromJson(json: JSONObject, metadata: JSONObject): List<Page> {
        val pages = json.getJSONArray(metadata.getString("scanlator"))
        val pageArray = ArrayList<Page>()

        for (i in 0 until pages.length()) {
            val page = Page(i + 1, "", pageBuilder(metadata.getString("slug"),
                metadata.getString("folder"),
                pages[i].toString(),
                metadata.getString("scanlator")))
            pageArray.add(page)
        }

        return pageArray
    }

    private fun getIdFromScanlatorName(scanlator: String): String {
        val keys = SCANLATORS.keys

        for (key in keys) {
            if (SCANLATORS[key].equals(scanlator)) {
                return key
            }
        }
        throw Exception("Cannot find scanlator group!")
    }

    private fun getBestScanlator(json: JSONObject): String {
        val preferred = preferences.getString(SCANLATOR_PREFERENCE, null)

        return if (preferred != null && json.has(preferred)) {
            preferred
        } else {
            json.keys().next()
        }
    }

    private fun pageBuilder(slug: String, folder: String, filename: String, groupId: String): String {
        return "$baseUrl/media/manga/$slug/chapters/$folder/$groupId/$filename"
    }

    // Called on every other request function to make sure we
    // have the most up to date scanlator array
    private fun updateScanlators() {
        if (SCANLATORS.isEmpty()) {
            clientBuilder().newCall(GET("$baseUrl/api/get_all_groups")).enqueue(
                object: Callback {
                    override fun onResponse(call: Call, response: Response) {
                        val json = JSONObject(response.body()!!.string())
                        val iter = json.keys()
                        while (iter.hasNext()) {
                            val scanId = iter.next()
                            SCANLATORS[scanId] = json.getString(scanId)
                        }
                    }
                    override fun onFailure(call: Call, e: IOException) {}
                }
            )
        }
    }

    private fun clientBuilder(): OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()!!

    // ----------------- Things we aren't supporting -----------------

    override fun imageUrlParse(response: Response): String {
        throw Exception("imageUrlParse not supported.")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw Exception("Latest updates not supported.")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw Exception("Latest updates not supported.")
    }

}
