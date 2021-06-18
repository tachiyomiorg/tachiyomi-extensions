package eu.kanade.tachiyomi.multisrc.guya

import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.HashMap

abstract class Guya(
    override val name: String,
    override val baseUrl: String,
    override val lang: String) : ConfigurableSource, HttpSource() {

    override val supportsLatest = false

    private val scanlatorCacheUrl = "$baseUrl/api/get_all_groups/"

    override fun headersBuilder() = Headers.Builder().apply {
        add(
            "User-Agent",
            "(Android ${Build.VERSION.RELEASE}; " +
                "${Build.MANUFACTURER} ${Build.MODEL}) " +
                "Tachiyomi/${BuildConfig.VERSION_NAME} " +
                Build.ID
        )
    }

    private val scanlators: ScanlatorStore = ScanlatorStore()

    // Preferences configuration
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }
    private val scanlatorPreference = "SCANLATOR_PREFERENCE"

    // Request builder for the "browse" page of the manga
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/get_all_series/", headers)
    }

    // Gets the response object from the request
    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.body!!.string()
        return parseManga(JSONObject(res))
    }

    // Overridden to use our overload
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return when {
            manga.url.startsWith(PROXY_PREFIX) -> {
                client.newCall(proxyChapterListRequest(manga))
                    .asObservableSuccess()
                    .map { response ->
                        proxyMangaDetailsParse(response, manga)
                    }
            }
            else -> {
                client.newCall(chapterListRequest(manga))
                    .asObservableSuccess()
                    .map { response ->
                        mangaDetailsParse(response, manga)
                    }
            }
        }
    }

    // Called when the series is loaded, or when opening in browser
    override fun mangaDetailsRequest(manga: SManga): Request {
        return when {
            manga.url.startsWith(PROXY_PREFIX) -> proxySeriesRequest(manga.url, false)
            else -> GET("$baseUrl/reader/series/${manga.url}/", headers)
        }
    }

    // Stub
    override fun mangaDetailsParse(response: Response): SManga {
        throw Exception("Unused")
    }

    private fun mangaDetailsParse(response: Response, manga: SManga): SManga {
        val res = response.body!!.string()
        return parseMangaFromJson(JSONObject(res), "", manga.title)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return when {
            manga.url.startsWith(PROXY_PREFIX) -> {
                client.newCall(proxyChapterListRequest(manga))
                    .asObservableSuccess()
                    .map { response ->
                        proxyChapterListParse(response, manga)
                    }
            }
            else -> {
                client.newCall(chapterListRequest(manga))
                    .asObservableSuccess()
                    .map { response ->
                        chapterListParse(response, manga)
                    }
            }
        }
    }

    // Gets the chapter list based on the series being viewed
    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/api/series/${manga.url}/", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw Exception("Unused")
    }

    // Called after the request
    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val res = response.body!!.string()
        return parseChapterList(res, manga)
    }

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return when {
            chapter.url.startsWith(PROXY_PREFIX) -> {
                client.newCall(proxyPageListRequest(chapter))
                    .asObservableSuccess()
                    .map { response ->
                        proxyPageListParse(response, chapter)
                    }
            }
            else -> {
                client.newCall(pageListRequest(chapter))
                    .asObservableSuccess()
                    .map { response ->
                        pageListParse(response, chapter)
                    }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/api/series/${chapter.url.split("/")[0]}/", headers)
    }

    // Stub
    override fun pageListParse(response: Response): List<Page> {
        throw Exception("Unused")
    }

    private fun pageListParse(response: Response, chapter: SChapter): List<Page> {
        val res = response.body!!.string()

        val json = JSONObject(res)
        val chapterNum = chapter.name.split(" - ")[0]
        val pages = json.getJSONObject("chapters")
            .getJSONObject(chapterNum)
            .getJSONObject("groups")
        val metadata = JSONObject()

        metadata.put("chapter", chapterNum)
        metadata.put("scanlator", scanlators.getKeyFromValue(chapter.scanlator.toString()))
        metadata.put("slug", json.getString("slug"))
        metadata.put(
            "folder",
            json.getJSONObject("chapters")
                .getJSONObject(chapterNum)
                .getString("folder")
        )

        return parsePageFromJson(pages, metadata)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(SLUG_PREFIX) -> {
                val slug = query.removePrefix(SLUG_PREFIX)
                client.newCall(searchMangaRequest(page, query, filters))
                    .asObservableSuccess()
                    .map { response ->
                        searchMangaParseWithSlug(response, slug)
                    }
            }
            query.startsWith(PROXY_PREFIX) && query.contains("/") -> {
                client.newCall(proxySearchMangaRequest(page, query, filters))
                    .asObservableSuccess()
                    .map { response ->
                        proxySearchMangaParse(response, query)
                    }
            }
            else -> {
                client.newCall(searchMangaRequest(page, query, filters))
                    .asObservableSuccess()
                    .map { response ->
                        searchMangaParse(response, query)
                    }
            }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/api/get_all_series/", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw Exception("Unused.")
    }

    private fun searchMangaParseWithSlug(response: Response, slug: String): MangasPage {
        val results = JSONObject(response.body!!.string())
        val mangaIter = results.keys()
        val truncatedJSON = JSONObject()

        while (mangaIter.hasNext()) {
            val mangaTitle = mangaIter.next()
            val mangaDetails = results.getJSONObject(mangaTitle)

            if (mangaDetails.get("slug") == slug) {
                truncatedJSON.put(mangaTitle, mangaDetails)
            }
        }

        return parseManga(truncatedJSON)
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val res = response.body!!.string()
        val json = JSONObject(res)
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

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val preference = androidx.preference.ListPreference(screen.context).apply {
            key = "preferred_scanlator"
            title = "Preferred scanlator"
            entries = arrayOf<String>()
            entryValues = arrayOf<String>()
            for (key in scanlators.keys()) {
                entries += scanlators.getValueFromKey(key)
                entryValues += key
            }
            summary = "Current: %s\n\n" +
                "This setting sets the scanlation group to prioritize " +
                "on chapter refresh/update. It will get the next available if " +
                "your preferred scanlator isn't an option (yet)."

            this.setDefaultValue("1")

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue.toString()
                preferences.edit().putString(scanlatorPreference, selected).commit()
            }
        }

        screen.addPreference(preference)
    }

    // ---------------- Proxy methods ------------------

    private fun proxySeriesRequest(query: String, api: Boolean = true): Request {
        val res = query.removePrefix(PROXY_PREFIX)
        val options = res.split("/")
        val proxyType = options[0]
        val slug = options[1]
        return if (api) {
            GET("$baseUrl/proxy/api/$proxyType/series/$slug/", headers)
        } else {
            GET("$baseUrl/proxy/$proxyType/$slug/", headers)
        }
    }

    private fun proxyMangaDetailsParse(response: Response, manga: SManga): SManga {
        return mangaDetailsParse(response, manga)
    }

    private fun proxyChapterListRequest(manga: SManga): Request {
        return proxySeriesRequest(manga.url)
    }

    private fun proxyChapterListParse(response: Response, manga: SManga): List<SChapter> {
        return chapterListParse(response, manga)
    }

    private fun proxyPageListRequest(chapter: SChapter): Request {
        val proxyUrl = chapter.url.removePrefix(PROXY_PREFIX)
        return when {
            proxyUrl.startsWith(NESTED_PROXY_API_PREFIX) -> {
                GET("$baseUrl$proxyUrl", headers)
            }
            else -> proxySeriesRequest(chapter.url)
        }
    }

    private fun proxyPageListParse(response: Response, chapter: SChapter): List<Page> {
        val res = response.body!!.string()
        val pages = if (chapter.url.removePrefix(PROXY_PREFIX).startsWith(NESTED_PROXY_API_PREFIX)) {
            JSONArray(res)
        } else {
            val json = JSONObject(res)
            val metadata = chapter.url.split("/").takeLast(2)
            val chapterNum = metadata[0]
            val groupNum = metadata[1]
            json.getJSONObject("chapters")
                .getJSONObject(chapterNum)
                .getJSONObject("groups")
                .getJSONArray(groupNum)
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

    private fun proxySearchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return proxySeriesRequest(query)
    }

    private fun proxySearchMangaParse(response: Response, query: String): MangasPage {
        return MangasPage(
            arrayListOf(parseMangaFromJson(JSONObject(response.body!!.string()), query)),
            false
        )
    }

    // ------------- Helpers and whatnot ---------------

    private fun parseChapterList(payload: String, manga: SManga): List<SChapter> {
        val sortKey = "preferred_sort"
        val response = JSONObject(payload)
        val chapters = response.getJSONObject("chapters")
        val mapping = response.getJSONObject("groups")

        val chapterList = ArrayList<SChapter>()

        val iter = chapters.keys()

        while (iter.hasNext()) {
            val chapterNum = iter.next()
            val chapterObj = chapters.getJSONObject(chapterNum)
            when {
                chapterObj.has(sortKey) -> {
                    chapterList.add(
                        parseChapterFromJson(
                            chapterObj,
                            chapterNum,
                            chapterObj.getJSONArray(sortKey),
                            response.getString("slug")
                        )
                    )
                }
                response.has(sortKey) -> {
                    chapterList.add(
                        parseChapterFromJson(
                            chapterObj,
                            chapterNum,
                            response.getJSONArray(sortKey),
                            response.getString("slug")
                        )
                    )
                }
                else -> {
                    val groups = chapterObj.getJSONObject("groups")
                    val groupsIter = groups.keys()

                    while (groupsIter.hasNext()) {
                        val chapter = SChapter.create()
                        val groupNum = groupsIter.next()

                        chapter.scanlator = mapping.getString(groupNum)
                        if (chapterObj.has("release_date")) {
                            chapter.date_upload =
                                chapterObj.getJSONObject("release_date").getLong(groupNum) * 1000
                        }
                        chapter.name = chapterNum + " - " + chapterObj.getString("title")
                        chapter.chapter_number = chapterNum.toFloat()
                        chapter.url =
                            if (groups.optJSONArray(groupNum) != null) {
                                val mangaUrl = manga.url
                                "$mangaUrl/$chapterNum/$groupNum"
                            } else {
                                val url = groups.getString(groupNum)
                                "$PROXY_PREFIX$url"
                            }
                        chapterList.add(chapter)
                    }
                }
            }
        }

        return chapterList.reversed()
    }

    // Helper function to get all the listings
    private fun parseManga(payload: JSONObject): MangasPage {
        val mangas = ArrayList<SManga>()

        val iter = payload.keys()

        while (iter.hasNext()) {
            val series = iter.next()
            val json = payload.getJSONObject(series)
            val manga = parseMangaFromJson(json, "", series)
            mangas.add(manga)
        }

        return MangasPage(mangas, false)
    }

    // Takes a json of the manga to parse
    private fun parseMangaFromJson(json: JSONObject, slug: String, title: String = ""): SManga {
        val manga = SManga.create()
        manga.title = if (title.isNotEmpty()) title else json.getString("title")
        manga.artist = json.optString("artist", "Unknown")
        manga.author = json.optString("author", "Unknown")
        manga.description = json.optString("description", "None")
        manga.url = if (slug.startsWith(PROXY_PREFIX)) slug else json.getString("slug")

        val cover = json.optString("cover", "")
        manga.thumbnail_url = if (cover.startsWith("http")) cover else "$baseUrl/$cover"
        return manga
    }

    private fun parseChapterFromJson(json: JSONObject, num: String, sort: JSONArray, slug: String): SChapter {
        val chapter = SChapter.create()

        // Get the scanlator info based on group ranking; do it first since we need it later
        val firstGroupId = getBestScanlator(json.getJSONObject("groups"), sort)
        chapter.scanlator = scanlators.getValueFromKey(firstGroupId)
        chapter.date_upload = json.getJSONObject("release_date").getLong(firstGroupId) * 1000
        chapter.name = num + " - " + json.getString("title")
        chapter.chapter_number = num.toFloat()
        chapter.url = "$slug/$num/$firstGroupId"

        return chapter
    }

    private fun parsePageFromJson(json: JSONObject, metadata: JSONObject): List<Page> {
        val pages = json.getJSONArray(metadata.getString("scanlator"))
        val pageArray = ArrayList<Page>()

        for (i in 0 until pages.length()) {
            val page = Page(
                i + 1,
                "",
                pageBuilder(
                    metadata.getString("slug"),
                    metadata.getString("folder"),
                    pages[i].toString(),
                    metadata.getString("scanlator")
                )
            )
            pageArray.add(page)
        }

        return pageArray
    }

    private fun getBestScanlator(json: JSONObject, sort: JSONArray): String {
        val preferred = preferences.getString(scanlatorPreference, null)

        if (preferred != null && json.has(preferred)) {
            return preferred
        } else {
            for (i in 0 until sort.length()) {
                if (json.has(sort.get(i).toString())) {
                    return sort.get(i).toString()
                }
            }
            // If all fails, fall-back to the next available key
            return json.keys().next()
        }
    }

    private fun pageBuilder(slug: String, folder: String, filename: String, groupId: String): String {
        return "$baseUrl/media/manga/$slug/chapters/$folder/$groupId/$filename"
    }

    inner class ScanlatorStore {
        private val scanlatorMap = HashMap<String, String>()
        private val TOTAL_RETRIES = 10
        private var retryCount = 0

        init {
            update(false)
        }

        fun getKeyFromValue(value: String): String {
            update()
            for (key in scanlatorMap.keys) {
                if (scanlatorMap[key].equals(value)) {
                    return key
                }
            }
            // Fall back to value as key if endpoint fails
            return value
        }

        fun getValueFromKey(key: String): String {
            update()
            // Fallback to key as value if endpoint fails
            return if (!scanlatorMap[key].isNullOrEmpty())
                scanlatorMap[key].toString() else key
        }

        fun keys(): MutableSet<String> {
            update()
            return scanlatorMap.keys
        }

        private fun onResponse(response: Response) {
            if (!response.isSuccessful) {
                retryCount++
            } else {
                val json = JSONObject(response.body!!.string())
                val iter = json.keys()
                while (iter.hasNext()) {
                    val scanId = iter.next()
                    scanlatorMap[scanId] = json.getString(scanId)
                }
            }
        }

        private fun onError(error: Throwable) {
            // Do nothing for now
        }

        private fun update(blocking: Boolean = true) {
            if (scanlatorMap.isEmpty() && retryCount < TOTAL_RETRIES) {
                try {
                    val call = client.newCall(GET(scanlatorCacheUrl, headers))
                        .asObservable()

                    if (blocking) {
                        call.toBlocking()
                            .subscribe({ res -> onResponse(res) }, { err -> onError(err) })
                    } else {
                        call.subscribeOn(Schedulers.io())
                            .subscribe({ res -> onResponse(res) }, { err -> onError(err) })
                    }
                } catch (e: Exception) {
                    // Prevent the extension from failing to load
                }
            }
        }
    }

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

    companion object {
        const val SLUG_PREFIX = "slug:"
        const val PROXY_PREFIX = "proxy:"
        const val NESTED_PROXY_API_PREFIX = "/proxy/api/"
    }
}
