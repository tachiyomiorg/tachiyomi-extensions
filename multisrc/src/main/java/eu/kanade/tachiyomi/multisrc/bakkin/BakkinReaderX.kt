package eu.kanade.tachiyomi.multisrc.bakkin

import android.app.Application
import android.os.Build
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class BakkinReaderX(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ConfigurableSource, HttpSource() {
    override val supportsLatest = false

    private val userAgent = "Mozilla/5.0 (" +
        "Android ${Build.VERSION.RELEASE}; Mobile) " +
        "Tachiyomi/${BuildConfig.VERSION_NAME}"

    protected val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)!!
    }

    private val json by lazy { Injekt.get<Json>() }

    private val mainUrl
        get() = baseUrl + "/main.php" +
            if (preferences.getBoolean("fullsize", false)) "?fullsize" else ""

    private var seriesCache = emptyList<Series>()

    private fun <R> observableSeries(block: (List<Series>) -> R) =
        if (seriesCache.isNotEmpty()) Observable.just(block(seriesCache))
        else client.newCall(GET(mainUrl, headers)).asObservableSuccess().map {
            seriesCache = json.parseToJsonElement(it.body!!.string())
                .jsonObject.values.map(json::decodeFromJsonElement)
            block(seriesCache)
        }

    override fun headersBuilder() = Headers.Builder().add("User-Agent", userAgent)

    // Request the actual manga URL for the webview
    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl#m=${manga.url}", headers)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        observableSeries { series ->
            series.map {
                SManga.create().apply {
                    url = it.dir
                    title = it.toString()
                    thumbnail_url = baseUrl + it.cover
                }
            }.let { MangasPage(it, false) }
        }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        observableSeries { series ->
            series.first { it.dir == manga.url }.let {
                SManga.create().apply {
                    url = it.dir
                    title = it.toString()
                    thumbnail_url = baseUrl + it.cover
                    initialized = true
                    author = it.author
                    status = when (it.status) {
                        "Ongoing" -> SManga.ONGOING
                        "Completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }
        }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        observableSeries { series ->
            series.first { it.dir == manga.url }.mapIndexed { idx, chapter ->
                SChapter.create().apply {
                    url = chapter.dir
                    name = chapter.toString()
                    chapter_number = idx.toFloat()
                    date_upload = -1L
                }
            }
        }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        observableSeries { series ->
            series.flatten().first { it.dir == chapter.url }
                .mapIndexed { idx, page -> Page(idx, "", "$baseUrl$page") }
        }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(
            CheckBoxPreference(screen.context).apply {
                key = "fullsize"
                summary = "View fullsize images"
                setDefaultValue(false)

                setOnPreferenceChangeListener { _, newValue ->
                    preferences.edit().putBoolean(key, newValue as Boolean).commit()
                }
            }
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("Search is not supported by this source.")

    override fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used!")

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used!")

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used!")

    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used!")

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used!")

    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException("Not used!")

    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException("Not used!")

    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("Not used!")

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used!")
}
