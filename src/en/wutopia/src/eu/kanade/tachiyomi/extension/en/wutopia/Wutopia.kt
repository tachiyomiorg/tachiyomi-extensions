package eu.kanade.tachiyomi.extension.en.wutopia

import android.app.Application
import android.content.SharedPreferences
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Wutopia : ConfigurableSource, HttpSource() {

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
        val json = gson.fromJson<JsonObject>(response.body!!.string())

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

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(apiMangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun apiMangaDetailsRequest(manga: SManga): Request {
        val body = RequestBody.create(null, "id=${manga.url}&linkId=0")
        return POST("$baseUrl/mobile/cartoon-collection/get", headers, body)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/#/mobile/cartoon/detail-cartoon/${manga.url}")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return gson.fromJson<JsonObject>(response.body!!.string())["cartoon"].let { json ->
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
        return POST("$baseUrl/mobile/cartoon-collection/list-chapter", headers, body, CacheControl.FORCE_NETWORK)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return gson.fromJson<JsonObject>(response.body!!.string())["list"].asJsonArray
            .let { json ->
                if (chapterListPref() == "free") json.filter { it["isPayed"].bool } else json
            }
            .map { json ->
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
        return gson.fromJson<JsonObject>(response.body!!.string())["chapter"]["picList"].asJsonArray.mapIndexed { i, json ->
            Page(i, "", json["picUrl"].asString)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Preferences

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val chapterListPref = androidx.preference.ListPreference(screen.context).apply {
            key = SHOW_LOCKED_CHAPTERS_Title
            title = SHOW_LOCKED_CHAPTERS_Title
            entries = prefsEntries
            entryValues = prefsEntryValues
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(SHOW_LOCKED_CHAPTERS, entry).commit()
            }
        }
        screen.addPreference(chapterListPref)
    }

    private fun chapterListPref() = preferences.getString(SHOW_LOCKED_CHAPTERS, "free")

    companion object {
        private const val SHOW_LOCKED_CHAPTERS_Title = "Wutopia requires login/payment for some chapters"
        private const val SHOW_LOCKED_CHAPTERS = "WUTOPIA_LOCKED_CHAPTERS"
        private val prefsEntries = arrayOf("Show all chapters (including pay-to-read)", "Only show free chapters")
        private val prefsEntryValues = arrayOf("all", "free")
    }
}
