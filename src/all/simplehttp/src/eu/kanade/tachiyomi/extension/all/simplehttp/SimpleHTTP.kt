package eu.kanade.tachiyomi.extension.all.simplehttp

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

open class SimpleHTTP : ConfigurableSource, ParsedHttpSource() {

    override val name = "SimpleHTTP"
    override val lang = "en"
    override val supportsLatest = false

    override val baseUrl by lazy { getPrefBaseUrl() }
    val basePort by lazy { getPrefPort() }
    val basePath by lazy { getPrefPath() }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun clientBuilder(): OkHttpClient = network.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(null)
            .build()!!

    override fun popularMangaSelector() = "li a"
    override fun popularMangaNextPageSelector() = null

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl:$basePort/$basePath/")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.title = element.text().dropLast(1)

        val url = element.attr("href")
        manga.setUrlWithoutDomain(url)

        return manga
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return clientBuilder().newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { response ->
                    popularMangaParse(response)
                }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun chapterListSelector() = "li a"


    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return clientBuilder().newCall(GET("$baseUrl:$basePort/$basePath/" + manga.url))
                .asObservableSuccess()
                .map { response ->
                    chapterListParse(response)
                }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.absUrl("href").substringAfter("$baseUrl")

        chapter.name = element.text().dropLast(1)
        return chapter

    }

    override fun pageListParse(document: Document): List<Page> {

        val pageList = document.select("li a")
        val pages = mutableListOf<Page>()

        // TODO: Maybe look at the extension of the file?

        pageList.forEach {
            val url = it.absUrl("href")
            pages.add(Page(pages.size, "", url))
        }

        return pages
    }

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

        val basePortPref = EditTextPreference(screen.context).apply {
            key = PORT_TITLE
            title = PORT_TITLE
            summary = basePort
            this.setDefaultValue(PORT_DEFAULT)
            dialogTitle = PORT_TITLE

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(PORT_TITLE, newValue as String).commit()
                    Toast.makeText(screen.context, "Restart Tachiyomi to apply new setting."
                            , Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val basePathPref = EditTextPreference(screen.context).apply {
            key = PATH_TITLE
            title = PATH_TITLE
            summary = basePath
            this.setDefaultValue(PATH_DEFAULT)
            dialogTitle = PATH_TITLE

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(PATH_TITLE, newValue as String).commit()
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
        screen.addPreference(basePathPref)
        screen.addPreference(basePortPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)
    private fun getPrefPort(): String = preferences.getString(PORT_TITLE, PORT_DEFAULT)
    private fun getPrefPath(): String = preferences.getString(PATH_TITLE, PATH_DEFAULT)

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val ADDRESS_DEFAULT = "http://localhost"

        private const val PORT_TITLE = "Port"
        private const val PORT_DEFAULT = "80"

        private const val PATH_TITLE = "Path"
        private const val PATH_DEFAULT = ""

    }

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")
    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")
    override fun latestUpdatesSelector() = throw Exception("Not used")
    override fun latestUpdatesNextPageSelector() = throw Exception("Not used")
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = throw Exception("Not used")
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Not used")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")
    override fun searchMangaSelector() = throw Exception("Not used")
    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")
    override fun searchMangaNextPageSelector() = throw Exception("Not used")
    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")
    override fun mangaDetailsParse(document: Document) = throw Exception("Not Used")
}
