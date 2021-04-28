package eu.kanade.tachiyomi.extension.all.cubari

import android.os.Build
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable

open class Cubari(override val lang: String) : HttpSource() {

    final override val name = "Cubari"
    final override val baseUrl = "https://cubari.moe"
    final override val supportsLatest = true

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
            .build()!!
            .newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response -> latestUpdatesParse(response) }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return parseMangaList(JSONArray(response.body()!!.string()), SortType.UNPINNED)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/", headers)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newBuilder()
            .addInterceptor(RemoteStorageUtils.HomeInterceptor())
            .build()!!
            .newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response -> popularMangaParse(response) }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return parseMangaList(JSONArray(response.body()!!.string()), SortType.PINNED)
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
        return parseMangaFromApi(JSONObject(response.body()!!.string()), manga)
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
        val res = response.body()!!.string()
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
                var url = chapter.url.split("/")
                val source = url[2]
                val slug = url[3]

                GET("$baseUrl/read/api/$source/series/$slug/", headers)
            }
        }
    }

    private fun directPageListParse(response: Response): List<Page> {
        val res = response.body()!!.string()
        val pages = JSONArray(res)
        val pageArray = ArrayList<Page>()

        for (i in 0 until pages.length()) {
            val page = if (pages.optJSONObject(i) != null) {
                pages.getJSONObject(i).getString("src")
            } else {
                pages[i]
            }
            pageArray.add(Page(i + 1, "", page.toString()))
        }
        return pageArray
    }

    private fun seriesJsonPageListParse(response: Response, chapter: SChapter): List<Page> {
        val res = response.body()!!.string()
        val json = JSONObject(res)
        val groups = json.getJSONObject("groups")
        val groupIter = groups.keys()
        val groupMap = HashMap<String, String>()

        while (groupIter.hasNext()) {
            val groupKey = groupIter.next()
            groupMap[groups.getString(groupKey)] = groupKey
        }

        val chapters = json.getJSONObject("chapters")

        val pages = if (chapters.has(chapter.chapter_number.toString())) {
            chapters
                .getJSONObject(chapter.chapter_number.toString())
                .getJSONObject("groups")
                .getJSONArray(groupMap[chapter.scanlator])
        } else {
            chapters
                .getJSONObject(chapter.chapter_number.toInt().toString())
                .getJSONObject("groups")
                .getJSONArray(groupMap[chapter.scanlator])
        }
        val pageArray = ArrayList<Page>()
        for (i in 0 until pages.length()) {
            val page = if (pages.optJSONObject(i) != null) {
                pages.getJSONObject(i).getString("src")
            } else {
                pages[i]
            }
            pageArray.add(Page(i + 1, "", page.toString()))
        }
        return pageArray
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
                    .build()!!
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
        return parseSearchList(JSONObject(response.body()!!.string()), query)
    }

    // ------------- Helpers and whatnot ---------------

    private fun parseChapterList(payload: String, manga: SManga): List<SChapter> {
        val json = JSONObject(payload)
        val groups = json.getJSONObject("groups")
        val chapters = json.getJSONObject("chapters")

        val chapterList = ArrayList<SChapter>()

        val iter = chapters.keys()
        
        val seriesPrefs = Injekt.get<Application>().getSharedPreferences("source_${id}_time_found:$querySeries", 0)
        val seriesPrefsEditor = seriesPrefs.edit()

        while (iter.hasNext()) {
            val chapterNum = iter.next()
            val chapterObj = chapters.getJSONObject(chapterNum)
            val chapterGroups = chapterObj.getJSONObject("groups")
            val groupsIter = chapterGroups.keys()

            while (groupsIter.hasNext()) {
                val groupNum = groupsIter.next()
                val chapter = SChapter.create()

                chapter.scanlator = groups.getString(groupNum)
                
                //Api for gist (and some others maybe) doesn't give a "release_date" so we will use the Manga update time. 
                //So when new chapter comes the manga will go on top if sortinf is set to "Last Updated"
                //Code by ivaniskandar (Implemented on CatManga extension.)
                
                if (chapterObj.has("release_date")) {
                    chapter.date_upload =
                        chapterObj.getJSONObject("release_date").getLong(groupNum) * 1000
                } else {
                    val currentTimeMillis = System.currentTimeMillis()
                    if (!seriesPrefs.contains(chapterNum)) {
                        seriesPrefsEditor.putLong(chapterNum, currentTimeMillis)
                    }
                    chapter.date_upload = seriesPrefs.getLong(chapterNum, currentTimeMillis)
                }
                chapter.name = if (chapterObj.has("volume")) {
                    
                    "Vol. " + chapterObj.getString("volume") + " Ch. " + chapterNum + " - " + chapterObj.getString("title")
                    //Output "Vol. 1 Ch. 1 - Chapter Name"
                    
                } else {
                
                    "Ch. " + chapterNum + " - " + chapterObj.getString("title")
                    //Output "Ch. 1 - Chapter Name"
                    
                }
                chapter.chapter_number = chapterNum.toFloat()
                chapter.url =
                    if (chapterGroups.optJSONArray(groupNum) != null) {
                        "${manga.url}/$chapterNum/$groupNum"
                    } else {
                        chapterGroups.getString(groupNum)
                    }
                chapterList.add(chapter)
            }
        }

        seriesPrefsEditor.apply()
        return chapterList.reversed()
    }

    private fun parseMangaList(payload: JSONArray, sortType: SortType): MangasPage {
        val mangas = ArrayList<SManga>()

        for (i in 0 until payload.length()) {
            val json = payload.getJSONObject(i)
            val pinned = json.getBoolean("pinned")

            if (sortType == SortType.PINNED && pinned) {
                mangas.add(parseMangaFromRemoteStorage(json))
            } else if (sortType == SortType.UNPINNED && !pinned) {
                mangas.add(parseMangaFromRemoteStorage(json))
            }
        }

        return MangasPage(mangas, false)
    }

    private fun parseSearchList(payload: JSONObject, query: String): MangasPage {
        val mangas = ArrayList<SManga>()
        val tempManga = SManga.create()
        tempManga.url = "/read/$query"
        mangas.add(parseMangaFromApi(payload, tempManga))
        return MangasPage(mangas, false)
    }

    private fun parseMangaFromRemoteStorage(json: JSONObject): SManga {
        val manga = SManga.create()
        manga.title = json.getString("title")
        manga.artist = json.optString("artist", ARTIST_FALLBACK)
        manga.author = json.optString("author", AUTHOR_FALLBACK)
        manga.description = json.optString("description", DESCRIPTION_FALLBACK)
        manga.url = json.getString("url")
        manga.thumbnail_url = json.getString("coverUrl")

        return manga
    }

    private fun parseMangaFromApi(json: JSONObject, mangaReference: SManga): SManga {
        val manga = SManga.create()
        manga.title = json.getString("title")
        manga.artist = json.optString("artist", ARTIST_FALLBACK)
        manga.author = json.optString("author", AUTHOR_FALLBACK)
        manga.description = json.optString("description", DESCRIPTION_FALLBACK)
        manga.url = mangaReference.url
        manga.thumbnail_url = json.optString("cover", "")

        return manga
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
