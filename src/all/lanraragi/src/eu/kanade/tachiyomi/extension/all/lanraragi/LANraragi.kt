package eu.kanade.tachiyomi.extension.all.lanraragi

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.util.Base64
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import eu.kanade.tachiyomi.extension.all.lanraragi.model.Archive
import eu.kanade.tachiyomi.extension.all.lanraragi.model.ArchivePage
import eu.kanade.tachiyomi.extension.all.lanraragi.model.ArchiveSearchResult
import eu.kanade.tachiyomi.extension.all.lanraragi.model.Category
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import okhttp3.Response
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

open class LANraragi : ConfigurableSource, HttpSource() {

    override val baseUrl: String
        get() = preferences.getString("hostname", "http://127.0.0.1:3000")!!

    override val lang = "all"

    override val name = "LANraragi"

    override val supportsLatest = true

    private val apiKey: String
        get() = preferences.getString("apiKey", "")!!

    private val latestNamespacePref: String
        get() = preferences.getString("latestNamespacePref", DEFAULT_SORT_BY_NS)!!

    private val gson: Gson = Gson()

    override fun mangaDetailsParse(response: Response): SManga {
        val id = getId(response)

        return SManga.create().apply {
            thumbnail_url = getThumbnailUri(id)
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        // Upgrade the LRR reader URL to the API metadata endpoint
        // without breaking WebView (i.e. for management).

        val id = manga.url.split('=').last()
        val uri = getApiUriBuilder("/api/archives/$id/metadata").build()

        return GET(uri.toString(), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val archive = gson.fromJson<Archive>(response.body()!!.string())
        val uri = getApiUriBuilder("/api/archives/${archive.arcid}/extract")

        return listOf(
            SChapter.create().apply {
                val uriBuild = uri.build()

                url = uriBuild.toString()
                chapter_number = 1F
                name = "Chapter"

                getDateAdded(archive.tags).toLongOrNull()?.let {
                    date_upload = it
                }
            }
        )
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return POST(chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val archivePage = gson.fromJson<ArchivePage>(response.body()!!.string())

        return archivePage.pages.mapIndexed { index, url ->
            val uri = Uri.parse("${baseUrl}${url.trimStart('.')}")
            Page(index, uri.toString(), uri.toString(), uri)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("imageUrlParse is unused")

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(page, "", FilterList())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val filters = mutableListOf<Filter<*>>()
        val prefNewOnly = preferences.getBoolean("latestNewOnly", false)

        if (prefNewOnly) filters.add(NewArchivesOnly(true))

        if (latestNamespacePref.isNotBlank()) {
            filters.add(SortByNamespace(latestNamespacePref))
            filters.add(DescendingOrder(true))
        }

        return searchMangaRequest(page, "", FilterList(filters))
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    private var lastResultCount: Int = 100
    private var lastRecordsFiltered: Int = 0
    private var maxResultCount: Int = 0
    private var totalRecords: Int = 0

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = getApiUriBuilder("/api/search")
        var startPageOffset = 0

        filters.forEach { filter ->
            when (filter) {
                is StartingPage -> {
                    startPageOffset = filter.state.toIntOrNull() ?: 1

                    // Exception for API wrapping around and user input of 0
                    if (startPageOffset > 0) {
                        startPageOffset -= 1
                    }
                }
                is NewArchivesOnly -> if (filter.state) uri.appendQueryParameter("newonly", "true")
                is UntaggedArchivesOnly -> if (filter.state) uri.appendQueryParameter("untaggedonly", "true")
                is DescendingOrder -> if (filter.state) uri.appendQueryParameter("order", "desc")
                is SortByNamespace -> if (filter.state.isNotEmpty()) uri.appendQueryParameter("sortby", filter.state.trim())
                is CategorySelect -> if (filter.state > 0) uri.appendQueryParameter("category", filter.toUriPart())
            }
        }

        uri.appendQueryParameter("start", ((page - 1 + startPageOffset) * maxResultCount).toString())

        if (query.isNotEmpty()) {
            uri.appendQueryParameter("filter", query)
        }

        return GET(uri.toString(), headers, CacheControl.FORCE_NETWORK)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonResult = gson.fromJson<ArchiveSearchResult>(response.body()!!.string())
        val currentStart = getStart(response)

        lastResultCount = jsonResult.data.size
        maxResultCount = if (lastResultCount >= maxResultCount) lastResultCount else maxResultCount
        lastRecordsFiltered = jsonResult.recordsFiltered
        totalRecords = jsonResult.recordsTotal

        return MangasPage(
            jsonResult.data.map {
                SManga.create().apply {
                    url = "/reader?id=${it.arcid}"
                    title = it.title
                    thumbnail_url = getThumbnailUri(it.arcid)
                    genre = it.tags
                    artist = getArtist(it.tags)
                    author = artist
                }
            },
            currentStart + jsonResult.data.size < jsonResult.recordsFiltered
        )
    }

    override fun headersBuilder() = Headers.Builder().apply {
        if (apiKey.isNotEmpty()) {
            val apiKey64 = Base64.encodeToString(apiKey.toByteArray(), Base64.DEFAULT).trim()
            add("Authorization", "Bearer $apiKey64")
        }
    }

    private class DescendingOrder(overrideState: Boolean = false) : Filter.CheckBox("Descending Order", overrideState)
    private class NewArchivesOnly(overrideState: Boolean = false) : Filter.CheckBox("New Archives Only", overrideState)
    private class UntaggedArchivesOnly : Filter.CheckBox("Untagged Archives Only", false)
    private class StartingPage(stats: String) : Filter.Text("Starting Page$stats", "")
    private class SortByNamespace(defaultText: String = "") : Filter.Text("Sort by (namespace)", defaultText)
    private class CategorySelect(categories: Array<Pair<String?, String>>) : UriPartFilter("Category", categories)

    override fun getFilterList() = FilterList(
        CategorySelect(getCategoryPairs(categories)),
        Filter.Separator(),
        DescendingOrder(),
        NewArchivesOnly(),
        UntaggedArchivesOnly(),
        StartingPage(startingPageStats()),
        SortByNamespace()
    )

    private var categories = emptyList<Category>()

    // Preferences
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val hostnamePref = EditTextPreference(screen.context).apply {
            key = "Hostname"
            title = "Hostname"
            text = baseUrl
            summary = baseUrl
            dialogTitle = "Hostname"

            setOnPreferenceChangeListener { _, newValue ->
                var hostname = newValue as String
                if (!hostname.startsWith("http://") && !hostname.startsWith("https://")) {
                    hostname = "http://$hostname"
                }

                this.apply {
                    text = hostname
                    summary = hostname
                }

                preferences.edit().putString("hostname", hostname).commit()
            }
        }

        val apiKeyPref = EditTextPreference(screen.context).apply {
            key = "API Key"
            title = "API Key"
            text = apiKey
            summary = "Required if No-Fun Mode is enabled."
            dialogTitle = "API Key"

            setOnPreferenceChangeListener { _, newValue ->
                val apiKey = newValue as String

                this.apply {
                    text = apiKey
                    summary = "Required if No-Fun Mode is enabled."
                }

                preferences.edit().putString("apiKey", newValue).commit()
            }
        }

        val latestNewOnlyPref = CheckBoxPreference(screen.context).apply {
            key = "latestNewOnly"
            title = "Latest - New Only"
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("latestNewOnly", checkValue).commit()
            }
        }

        val latestNamespacePref = EditTextPreference(screen.context).apply {
            key = "latestNamespacePref"
            title = "Latest - Sort by Namespace"
            text = latestNamespacePref
            summary = "Sort by the given namespace for Latest, such as date_added."
            dialogTitle = "Latest - Sort by Namespace"
            setDefaultValue(DEFAULT_SORT_BY_NS)

            setOnPreferenceChangeListener { _, newValue ->
                val latestNamespacePref = newValue as String

                this.apply {
                    text = latestNamespacePref
                }

                preferences.edit().putString("latestNamespacePref", newValue).commit()
            }
        }

        screen.addPreference(hostnamePref)
        screen.addPreference(apiKeyPref)
        screen.addPreference(latestNewOnlyPref)
        screen.addPreference(latestNamespacePref)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val hostnamePref = androidx.preference.EditTextPreference(screen.context).apply {
            key = "Hostname"
            title = "Hostname"
            text = baseUrl
            summary = baseUrl
            dialogTitle = "Hostname"

            setOnPreferenceChangeListener { _, newValue ->
                var hostname = newValue as String
                if (!hostname.startsWith("http://") && !hostname.startsWith("https://")) {
                    hostname = "http://$hostname"
                }

                this.apply {
                    text = hostname
                    summary = hostname
                }

                preferences.edit().putString("hostname", hostname).commit()
            }
        }

        val apiKeyPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = "API Key"
            title = "API Key"
            text = apiKey
            summary = "Required if No-Fun Mode is enabled."
            dialogTitle = "API Key"

            setOnPreferenceChangeListener { _, newValue ->
                val apiKey = newValue as String

                this.apply {
                    text = apiKey
                    summary = "Required if No-Fun Mode is enabled."
                }

                preferences.edit().putString("apiKey", newValue).commit()
            }
        }

        val latestNewOnlyPref = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = "latestNewOnly"
            title = "Latest - New Only"
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("latestNewOnly", checkValue).commit()
            }
        }

        val latestNamespacePref = androidx.preference.EditTextPreference(screen.context).apply {
            key = "latestNamespacePref"
            title = "Latest - Sort by Namespace"
            text = latestNamespacePref
            summary = "Sort by the given namespace for Latest, such as date_added."
            dialogTitle = "Latest - Sort by Namespace"
            setDefaultValue(DEFAULT_SORT_BY_NS)

            setOnPreferenceChangeListener { _, newValue ->
                val latestNamespacePref = newValue as String

                this.apply {
                    text = latestNamespacePref
                }

                preferences.edit().putString("latestNamespacePref", newValue).commit()
            }
        }

        screen.addPreference(hostnamePref)
        screen.addPreference(apiKeyPref)
        screen.addPreference(latestNewOnlyPref)
        screen.addPreference(latestNamespacePref)
    }

    // Helper
    protected open class UriPartFilter(displayName: String, val vals: Array<Pair<String?, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    private fun getCategoryPairs(categories: List<Category>): Array<Pair<String?, String>> {
        // Empty pair to disable. Sort by pinned status then name for convenience.
        // Web client sort is pinned > last_used but reflects between page changes.

        val pin = "\uD83D\uDCCC "

        return listOf(Pair("", ""))
            .plus(
                categories
                    .sortedWith(compareByDescending<Category> { it.pinned }.thenBy { it.name })
                    .map {
                        val pinned = if (it.pinned == "1") pin else ""
                        Pair(it.id, "$pinned${it.name}")
                    }
            )
            .toTypedArray()
    }

    private fun startingPageStats(): String {
        return if (maxResultCount > 0 && totalRecords > 0) " ($maxResultCount / $lastRecordsFiltered items)" else ""
    }

    private fun getApiUriBuilder(path: String): Uri.Builder {
        val uri = Uri.parse("$baseUrl$path").buildUpon()

        return uri
    }

    private fun getThumbnailUri(id: String): String {
        val uri = getApiUriBuilder("/api/archives/$id/thumbnail")

        return uri.toString()
    }

    private fun getTopResponse(response: Response): Response {
        return if (response.priorResponse() == null) response else getTopResponse(response.priorResponse()!!)
    }

    private fun getId(response: Response): String {
        return getTopResponse(response).request().url().queryParameter("id").toString()
    }

    private fun getStart(response: Response): Int {
        return getTopResponse(response).request().url().queryParameter("start")!!.toInt()
    }

    private fun getNSTag(tags: String?, tag: String): List<String>? {
        tags?.split(',')?.forEach {
            if (it.contains(':')) {
                val temp = it.trim().split(":", limit = 2)
                if (temp[0].equals(tag, true)) return temp
            }
        }

        return null
    }

    private fun getArtist(tags: String?): String {
        getNSTag(tags, "artist")?.let {
            return it[1]
        }

        return "N/A"
    }

    private fun getDateAdded(tags: String?): String {
        getNSTag(tags, "date_added")?.let {
            // Pad Date Added NS to milliseconds
            return it[1].padEnd(13, '0')
        }

        return ""
    }

    // Headers (currently auth) are done in headersBuilder
    override val client: OkHttpClient = network.client.newBuilder().build()

    init {
        Single.fromCallable {
            client.newCall(GET("$baseUrl/api/categories", headers)).execute()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response ->
                    categories = try {
                        gson.fromJson(response.body()?.charStream()!!)
                    } catch (e: Exception) {
                        emptyList()
                    }
                },
                {}
            )
    }

    companion object {
        private const val DEFAULT_SORT_BY_NS = "date_added"
    }
}
