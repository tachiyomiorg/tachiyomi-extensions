package eu.kanade.tachiyomi.extension.all.komga

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eu.kanade.tachiyomi.extension.all.komga.dto.BookDto
import eu.kanade.tachiyomi.extension.all.komga.dto.PageWrapperDto
import eu.kanade.tachiyomi.extension.all.komga.dto.SerieDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.*
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.*
import java.util.concurrent.TimeUnit

open class Komga : ConfigurableSource, HttpSource() {
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/v1/series", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val series = Gson().fromJson<PageWrapperDto<SerieDto>>(response.body()?.charStream(), object : TypeToken<PageWrapperDto<SerieDto>>() {}.type)
        val mangas = series.content.map {
            SManga.create().apply {
                title = it.name
                artist = "Unknown"
                author = "Unknown"
                url = "/api/v1/series/${it.id}"
                description = "No description"
                status = SManga.UNKNOWN
                initialized = true
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage =
        popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage =
        popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val serie = Gson().fromJson(response.body()?.charStream(), SerieDto::class.java)
        return SManga.create().apply {
            title = serie.name
            artist = "Unknown"
            author = "Unknown"
            url = "/api/v1/series/${serie.id}"
            description = "No description"
            status = SManga.UNKNOWN
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}/books?sort=name,desc", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val books = Gson().fromJson<PageWrapperDto<BookDto>>(response.body()?.charStream(), object : TypeToken<PageWrapperDto<BookDto>>() {}.type)
        return books.content.map {
            SChapter.create().apply {
                chapter_number = (books.content.indexOf(it) + 1).toFloat()
                name = it.name
                url = "/api/v1/series/1/books/${it.id}"
                date_upload = Date().time
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl${chapter.url}/pages")

    override fun pageListParse(response: Response): List<Page> =
        emptyList()

    override fun imageUrlParse(response: Response): String {
        return "https://images.pexels.com/photos/67636/rose-blue-flower-rose-blooms-67636.jpeg"
    }

    override val name = "Komga"
    override val lang = "en"
    override val supportsLatest = false

    override val baseUrl by lazy { getPrefBaseUrl() }
    private val username by lazy { getPrefUsername() }
    private val password by lazy { getPrefPassword() }

    override fun headersBuilder(): Headers.Builder =
        Headers.Builder()
            .add("Authorization", Credentials.basic(username, password))

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun clientBuilder(): OkHttpClient = network.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cache(null)
        .build()!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {

        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = ADDRESS_TITLE
            title = ADDRESS_TITLE
            summary = baseUrl
            this.setDefaultValue(ADDRESS_DEFAULT)
            dialogTitle = ADDRESS_TITLE

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(ADDRESS_TITLE, newValue as String).commit()
                    Toast.makeText(screen.context, "Restart Tachiyomi to apply new setting."
                        , Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)

        val usernamePref = EditTextPreference(screen.context).apply {
            key = USERNAME_TITLE
            title = USERNAME_TITLE
            summary = username
            this.setDefaultValue(USERNAME_DEFAULT)
            dialogTitle = USERNAME_TITLE

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(USERNAME_TITLE, newValue as String).commit()
                    Toast.makeText(screen.context, "Restart Tachiyomi to apply new setting."
                        , Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(usernamePref)

        val passwordPref = EditTextPreference(screen.context).apply {
            key = PASSWORD_TITLE
            title = PASSWORD_TITLE
            summary = password
            this.setDefaultValue(PASSWORD_DEFAULT)
            dialogTitle = PASSWORD_TITLE

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(PASSWORD_TITLE, newValue as String).commit()
                    Toast.makeText(screen.context, "Restart Tachiyomi to apply new setting."
                        , Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(passwordPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)!!
    private fun getPrefUsername(): String = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private fun getPrefPassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val ADDRESS_DEFAULT = "http://192.168.1.156:8080"
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = "user"
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = "user"
    }
}
