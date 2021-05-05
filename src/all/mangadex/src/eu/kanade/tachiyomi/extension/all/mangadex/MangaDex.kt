package eu.kanade.tachiyomi.extension.all.mangadex

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

abstract class MangaDex(override val lang: String) : ConfigurableSource, HttpSource() {
    override val name = "MangaDex"
    override val baseUrl = "https://www.mangadex.org"

    // after mvp comes out make current popular becomes latest (mvp doesnt have a browse page)
    override val supportsLatest = false

    private val helper = MangaDexHelper()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi " + System.getProperty("http.agent"))
    }

    override val client =
        network.client.newBuilder().addNetworkInterceptor(mdRateLimitInterceptor).addInterceptor(
            coverInterceptor
        ).addInterceptor(MdAtHomeReportInterceptor(network.client, headersBuilder().build()))
            .build()

    // POPULAR Manga Section

    override fun popularMangaRequest(page: Int): Request {
        return GET(
            url = "${MDConstants.apiMangaUrl}?order[updatedAt]=desc&limit=${MDConstants.mangaLimit}&offset=${
            helper.getMangaListOffset(
                page
            )
            }",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (response.isSuccessful.not()) {
            throw Exception("Error getting popular manga http code: ${response.code}")
        }

        val mangaListResponse = JsonParser.parseString(response.body!!.string()).obj
        val hasMoreResults =
            (mangaListResponse["limit"].int + mangaListResponse["offset"].int) < mangaListResponse["total"].int

        val mangaList = mangaListResponse["results"].array.map { helper.createManga(it) }
        return MangasPage(mangaList, hasMoreResults)
    }

    // LATEST section  API can't sort by date yet so not implemented
    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    // SEARCH section

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(MDConstants.prefixIdSearch)) {
            val url = MDConstants.apiMangaUrl.toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("ids[]", query.removePrefix(MDConstants.prefixIdSearch))
            return GET(url.toString(), headers, CacheControl.FORCE_NETWORK)
        }

        val tempUrl = MDConstants.apiMangaUrl.toHttpUrlOrNull()!!.newBuilder()

        tempUrl.apply {
            addQueryParameter("limit", MDConstants.mangaLimit.toString())
            addQueryParameter("offset", (helper.getMangaListOffset(page)))
            val actualQuery = query.replace(MDConstants.whitespaceRegex, " ")
            if (actualQuery.isNotBlank()) {
                addQueryParameter("title", actualQuery)
            }
        }

        val finalUrl = helper.mdFilters.addFiltersToUrl(tempUrl, filters)

        return GET(finalUrl, headers, CacheControl.FORCE_NETWORK)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Manga Details section

    // Shenanigans to allow "open in webview" to show a webpage instead of JSON
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(apiMangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("${baseUrl}${manga.url}", headers)
    }
    /**
     * get manga details url throws exception if the url is the old format so people migrate
     */
    fun apiMangaDetailsRequest(manga: SManga): Request {
        if (!helper.containsUuid(manga.url.trim())) {
            throw Exception("Manga ID format has changed, migrate from MangaDex to MangaDex to continue reading")
        }
        return GET("${MDConstants.apiUrl}${manga.url}", headers, CacheControl.FORCE_NETWORK)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = JsonParser.parseString(response.body!!.string()).obj
        return helper.createManga(manga, client)
    }

    // Chapter list section
    /**
     * get chapter list if manga url is old format throws exception
     */
    override fun chapterListRequest(manga: SManga): Request {
        if (!helper.containsUuid(manga.url)) {
            throw Exception("Manga ID format has changed, migrate from MangaDex to MangaDex to continue reading")
        }
        return actualChapterListRequest(helper.getUUIDFromUrl(manga.url), 0)
    }

    /**
     * Required because api is paged
     */
    private fun actualChapterListRequest(mangaId: String, offset: Int) =
        GET(
            url = helper.getChapterEndpoint(mangaId, offset, lang),
            headers = headers,
            cache = CacheControl.FORCE_NETWORK
        )

    override fun chapterListParse(response: Response): List<SChapter> {

        if (response.isSuccessful.not()) {
            throw Exception("Error getting chapter list http code: ${response.code}")
        }
        try {
            val chapterListResponse = JsonParser.parseString(response.body!!.string()).obj

            val chapterListResults = chapterListResponse["results"].array.map { it.obj }.toMutableList()

            val mangaId =
                response.request.url.toString().substringBefore("/feed")
                    .substringAfter(MDConstants.apiMangaUrl)

            val limit = chapterListResponse["limit"].int

            var offset = chapterListResponse["offset"].int

            var hasMoreResults = (limit + offset) < chapterListResponse["total"].int

            // max results that can be returned is 500 so need to make more api calls if limit+offset > total chapters
            while (hasMoreResults) {
                offset += limit
                val newResponse =
                    client.newCall(actualChapterListRequest(mangaId, offset)).execute()
                val newChapterListJson = JsonParser.parseString(newResponse.body!!.string()).obj
                chapterListResults.addAll(newChapterListJson["results"].array.map { it.obj })
                hasMoreResults = (limit + offset) < newChapterListJson["total"].int
            }

            val groupMap = helper.createGroupMap(chapterListResults.toList(), client)

            val now = Date().time

            return chapterListResults.map { helper.createChapter(it, groupMap) }
                .filter { it.date_upload <= now && "MangaPlus" != it.scanlator }
        } catch (e: Exception) {
            Log.e("MangaDex", "error parsing chapter list", e)
            throw(e)
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (!helper.containsUuid(chapter.url)) {
            throw Exception("Chapter ID format has changed, migrate from MangaDex to MangaDex to continue reading")
        }
        return GET(MDConstants.apiUrl + chapter.url, headers, CacheControl.FORCE_NETWORK)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterJson = JsonParser.parseString(response.body!!.string()).obj["data"]
        val atHomeRequestUrl = "${MDConstants.apiUrl}/at-home/server/${chapterJson["id"].string}"

        val host =
            helper.getMdAtHomeUrl(atHomeRequestUrl, client, headers, CacheControl.FORCE_NETWORK)

        val usingDataSaver = preferences.getInt(MDConstants.dataSaverPref, 0) == 1

        // have to add the time, and url to the page because pages timeout within 30mins now
        val now = Date().time
        val hash = chapterJson["attributes"]["hash"].string
        val pageSuffix = if (usingDataSaver) {
            chapterJson["attributes"]["dataSaver"].array.map { "/data-saver/$hash/${it.string}" }
        } else {
            chapterJson["attributes"]["data"].array.map { "/data/$hash/${it.string}" }
        }

        return pageSuffix.mapIndexed { index, imgUrl ->
            val mdAtHomeMetadataUrl = "$host,$atHomeRequestUrl,$now"
            Page(index, mdAtHomeMetadataUrl, imgUrl)
        }
    }

    override fun imageRequest(page: Page): Request {
        return helper.getValidImageUrlForPage(page, headers, client)
    }

    override fun imageUrlParse(response: Response): String = ""

    // mangadex is mvp no settings yet
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val dataSaverPref = androidx.preference.ListPreference(screen.context).apply {
            key = MDConstants.dataSaverPref
            title = MDConstants.dataSaverPrefTitle
            entries = arrayOf("Disable", "Enable")
            entryValues = arrayOf("0", "1")
            summary = "%s"
            setDefaultValue("0")

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                preferences.edit().putInt(MDConstants.dataSaverPref, index).commit()
            }
        }

        screen.addPreference(dataSaverPref)
    }

    override fun getFilterList(): FilterList = helper.mdFilters.getMDFilterList()
}
