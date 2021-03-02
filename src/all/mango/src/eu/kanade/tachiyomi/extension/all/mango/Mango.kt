package eu.kanade.tachiyomi.extension.all.mango

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.text.InputType
import android.widget.Toast
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import info.debatty.java.stringsimilarity.JaroWinkler
import info.debatty.java.stringsimilarity.Levenshtein

class Mango : ConfigurableSource, HttpSource() {

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/library", headersBuilder().build())

    // Our popular manga are just our library of manga
    override fun popularMangaParse(response: Response): MangasPage {
        val result = gson.fromJson<JsonObject>(response.body()!!.string())
        val mangas = result["titles"].asJsonArray
        return MangasPage(
            mangas.asJsonArray.map {
                SManga.create().apply {
                    url = "/book/" + it["id"].asString
                    title = it["display_name"].asString
                    thumbnail_url = baseUrl + it["cover_url"].asString
                }
            },
            false
        )
    }

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    // Default is to just return the whole library for searching
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    // Here the best we can do is just match manga based on their titles
    private fun searchMangaParse(response: Response, query: String): MangasPage {

        val queryLower = query.toLowerCase()
        val mangas = popularMangaParse(response).mangas
        val exactMatch = mangas.firstOrNull { it.title.toLowerCase() == queryLower }
        if (exactMatch != null) {
            return MangasPage(listOf(exactMatch), false)
        }

        // Text distance algorithms
        // Code is originally from here:
        // https://github.com/CarlosEsco/Neko/blob/987d1485203a1ec1b5037dcf9b4bdea24f2cf8a3/app/src/main/java/eu/kanade/tachiyomi/source/online/MergeSource.kt
        val textDistance = Levenshtein()
        val textDistance2 = JaroWinkler()

        // Take results that potentially start the same
        val results = mangas.filter {
            val title = it.title.toLowerCase()
            val query2 = queryLower.take(7)
            (title.startsWith(query2, true) || title.contains(query2, true))
        }.sortedBy { textDistance.distance(queryLower, it.title.toLowerCase()) }

        // Take similar results
        val results2 = mangas.map { Pair(textDistance2.distance(it.title.toLowerCase(), query), it) }
            .filter { it.first < 0.3 }.sortedBy { it.first }.map { it.second }
        val combinedResults = results.union(results2)

        // Finally return the list
        return MangasPage(combinedResults.toList(), false)
    }

    // Stub
    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(baseUrl + "/api" + manga.url, headers)

    // This will just return the same thing as the main library endpoint
    override fun mangaDetailsParse(response: Response): SManga {
        val result = gson.fromJson<JsonObject>(response.body()!!.string())
        return SManga.create().apply {
            url = "/book/" + result["id"].asString
            title = result["display_name"].asString
            thumbnail_url = baseUrl + result["cover_url"].asString
        }
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET(baseUrl + "/api" + manga.url, headers)

    // The chapter url will contain how many pages the chapter contains for our page list endpoint
    override fun chapterListParse(response: Response): List<SChapter> {
        val result = gson.fromJson<JsonObject>(response.body()!!.string())
        return result["entries"].asJsonArray.map { obj ->
            SChapter.create().apply {
                name = obj["display_name"].asString
                url = "/page/${obj["title_id"].asString}/${obj["id"].asString}/${obj["pages"].asString}/"
                date_upload = 1000L * obj["mtime"].asLong
                chapter_number = name.replace(Regex("[^0-9]"), "").toFloat()
            }
        }.sortedByDescending { it.chapter_number }
    }

    // Stub
    override fun pageListRequest(chapter: SChapter): Request =
        throw UnsupportedOperationException("Not used")

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val splitUrl = chapter.url.split("/").toMutableList()
        val numPages = splitUrl.removeAt(splitUrl.size - 2).toInt()
        val baseUrlChapter = splitUrl.joinToString("/")
        val pages = mutableListOf<Page>()
        for (i in 0..numPages) {
            pages.add(
                Page(
                    index = i,
                    imageUrl = "$baseUrl/api$baseUrlChapter$i"
                )
            )
        }
        return Observable.just(pages)
    }

    // Stub
    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String = ""
    override fun getFilterList(): FilterList = FilterList()

    override val name = "Mango"
    override val lang = "en"
    override val supportsLatest = false

    override val baseUrl by lazy { getPrefBaseUrl() }
    private val username by lazy { getPrefUsername() }
    private val password by lazy { getPrefPassword() }
    private val gson by lazy { Gson() }

    override fun headersBuilder(): Headers.Builder =
        Headers.Builder()
            .add("User-Agent", "Tachiyomi Mango v${BuildConfig.VERSION_NAME}")

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient =
        network.client.newBuilder()
            .authenticator { _, response ->
                if (response.request().header("Authorization") != null) {
                    null // Give up, we've already failed to authenticate.
                } else {
                    response.request().newBuilder()
                        .addHeader("Authorization", Credentials.basic(username, password))
                        .build()
                }
            }
            .build()

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, baseUrl))
        screen.addPreference(screen.editTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, username))
        screen.addPreference(screen.editTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, password, true))
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(title: String, default: String, value: String, isPassword: Boolean = false): androidx.preference.EditTextPreference {
        return androidx.preference.EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value
            this.setDefaultValue(default)
            dialogTitle = title

            if (isPassword) {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(screen.supportEditTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, baseUrl))
        screen.addPreference(screen.supportEditTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, username))
        screen.addPreference(screen.supportEditTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, password))
    }

    private fun PreferenceScreen.supportEditTextPreference(title: String, default: String, value: String): EditTextPreference {
        return EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value
            this.setDefaultValue(default)
            dialogTitle = title
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    // We strip the last slash since we will append it above
    private fun getPrefBaseUrl(): String {
        var path = preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)!!
        if (path.last() == '/') {
            path = path.substring(0, path.length - 1)
        }
        return path
    }
    private fun getPrefUsername(): String = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private fun getPrefPassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val ADDRESS_DEFAULT = ""
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = ""
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = ""
    }
}
