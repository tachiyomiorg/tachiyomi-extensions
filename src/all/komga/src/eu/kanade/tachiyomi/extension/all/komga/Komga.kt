package eu.kanade.tachiyomi.extension.all.komga

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.extension.all.komga.dto.*
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.*
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.*

open class Komga : ConfigurableSource, HttpSource() {
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/v1/series?page=${page - 1}", headers)

    override fun popularMangaParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api/v1/series/latest?page=${page - 1}", headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/api/v1/series?search=$query&page=${page - 1}")!!.newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is LibraryGroup -> {
                    val libraryToInclude = mutableListOf<Long>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            libraryToInclude.add(content.id)
                        }
                    }
                    if (libraryToInclude.isNotEmpty()) {
                        url.addQueryParameter("library_id", libraryToInclude.joinToString(","))
                    }
                }
            }
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val series = gson.fromJson<SeriesDto>(response.body()?.charStream()!!)
        return series.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}/books?size=1000", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val page = gson.fromJson<PageWrapperDto<BookDto>>(response.body()?.charStream()!!)
        val chapterListUrl = response.request().url().newBuilder()
            .removeAllQueryParameters("size").build().toString()

        return page.content.mapIndexed { i, book ->
            SChapter.create().apply {
                chapter_number = (i + 1).toFloat()
                name = "${book.name} (${book.size})"
                url = "$chapterListUrl/${book.id}"
                date_upload = parseDate(book.lastModified)
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET("${chapter.url}/pages")

    override fun pageListParse(response: Response): List<Page> {
        val pages = gson.fromJson<List<PageDto>>(response.body()?.charStream()!!)
        return pages.map {
            val url = "${response.request().url()}/${it.number}" +
                if (!supportedImageTypes.contains(it.mediaType)) {
                    "?convert=png"
                } else {
                    ""
                }
            Page(
                index = it.number - 1,
                imageUrl = url
            )
        }
    }

    private fun processSeriesPage(response: Response): MangasPage {
        val page = gson.fromJson<PageWrapperDto<SeriesDto>>(response.body()?.charStream()!!)
        val mangas = page.content.map {
            it.toSManga()
        }
        return MangasPage(mangas, !page.last)
    }

    private fun SeriesDto.toSManga(): SManga =
        SManga.create().apply {
            title = this@toSManga.name
            url = "/api/v1/series/${this@toSManga.id}"
            thumbnail_url = "$baseUrl/api/v1/series/${this@toSManga.id}/thumbnail"
            status = SManga.UNKNOWN
        }

    private fun parseDate(date: String?): Long =
        if (date == null)
            Date().time
        else {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(date).time
            } catch (ex: Exception) {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S", Locale.US).parse(date).time
                } catch (ex: Exception) {
                    Date().time
                }
            }
        }

    override fun imageUrlParse(response: Response): String = ""

    private class LibraryFilter(val id: Long, name: String) : Filter.CheckBox(name, false)
    private class LibraryGroup(libraries: List<LibraryFilter>) : Filter.Group<LibraryFilter>("Libraries", libraries)

    override fun getFilterList(): FilterList =
        FilterList(
            LibraryGroup(libraries.map { LibraryFilter(it.id, it.name) }.sortedBy { it.name })
        )

    private var libraries = emptyList<LibraryDto>()

    override val name = "Komga"
    override val lang = "en"
    override val supportsLatest = true

    override val baseUrl by lazy { getPrefBaseUrl() }
    private val username by lazy { getPrefUsername() }
    private val password by lazy { getPrefPassword() }
    private val gson by lazy { Gson() }

    override fun headersBuilder(): Headers.Builder =
        Headers.Builder()
            .add("User-Agent", "Tachiyomi Komga v${BuildConfig.VERSION_NAME}")

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

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, baseUrl))
        screen.addPreference(screen.editTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, username))
        screen.addPreference(screen.editTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, password))
    }

    private fun PreferenceScreen.editTextPreference(title: String, default: String, value: String): EditTextPreference {
        return EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value
            this.setDefaultValue(default)
            dialogTitle = title

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting."
                        , Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    private fun getPrefBaseUrl(): String = preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)!!
    private fun getPrefUsername(): String = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private fun getPrefPassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!

    init {
        Single.fromCallable {
            client.newCall(GET("$baseUrl/api/v1/libraries", headers)).execute()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                libraries = try {
                    gson.fromJson(it.body()?.charStream()!!)
                } catch (e: Exception) {
                    emptyList()
                }
            }, {})

    }

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val ADDRESS_DEFAULT = ""
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = ""
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = ""

        private val supportedImageTypes = listOf("image/jpeg", "image/png", "image/gif", "image/webp")
    }
}
