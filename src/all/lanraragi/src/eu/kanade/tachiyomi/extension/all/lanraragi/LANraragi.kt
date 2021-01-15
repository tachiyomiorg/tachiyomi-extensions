package eu.kanade.tachiyomi.extension.all.lanraragi

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.util.Base64
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
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

    private val gson: Gson = Gson()

    override fun mangaDetailsParse(response: Response): SManga {
        val id = getId(response)

        return SManga.create().apply {
            thumbnail_url = getThumbnailUri(id)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val id = getId(response)

        val uri = getApiUriBuilder("/api/archives/$id/extract")

        return listOf(
            SChapter.create().apply {
                val uriBuild = uri.build()

                url = uriBuild.toString()
                chapter_number = 1F
                name = "Chapter"
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
        return searchMangaRequest(page, "", FilterList())
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    private var lastResultCount: Int = 100

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
                is CategoryGroup -> uri.appendQueryParameter("category", filter.state.first { it.state }.id)
            }
        }

        uri.appendQueryParameter("start", ((page - 1 + startPageOffset) * lastResultCount).toString())

        if (query.isNotEmpty()) {
            uri.appendQueryParameter("filter", query)
        }

        return GET(uri.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonResult = gson.fromJson<ArchiveSearchResult>(response.body()!!.string())
        val currentStart = getStart(response)

        lastResultCount = jsonResult.data.size

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

    private class DescendingOrder : Filter.CheckBox("Descending Order", false)
    private class NewArchivesOnly : Filter.CheckBox("New Archives Only", false)
    private class UntaggedArchivesOnly : Filter.CheckBox("Untagged Archives Only", false)
    private class StartingPage(lastResultCount: String) : Filter.Text("Starting Page (per: $lastResultCount)", "")
    private class SortByNamespace : Filter.Text("Sort by (namespace)", "")
    private class CategoryList(val id: String, name: String) : Filter.CheckBox(name, false)
    private class CategoryGroup(categories: List<CategoryList>) : Filter.Group<CategoryList>("Category", categories)

    override fun getFilterList() = FilterList(
        CategoryGroup(
            categories
                .sortedWith(compareByDescending<Category> { it.pinned }.thenBy { it.name })
                .map { CategoryList(it.id, it.name) }
        ),
        Filter.Separator(),
        DescendingOrder(),
        NewArchivesOnly(),
        UntaggedArchivesOnly(),
        StartingPage(lastResultCount.toString()),
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
            summary = apiKey
            dialogTitle = "API Key"

            setOnPreferenceChangeListener { _, newValue ->
                val apiKey = newValue as String

                this.apply {
                    text = apiKey
                    summary = apiKey
                }

                preferences.edit().putString("apiKey", newValue).commit()
            }
        }

        screen.addPreference(hostnamePref)
        screen.addPreference(apiKeyPref)
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
            summary = apiKey
            dialogTitle = "API Key"

            setOnPreferenceChangeListener { _, newValue ->
                val apiKey = newValue as String

                this.apply {
                    text = apiKey
                    summary = apiKey
                }

                preferences.edit().putString("apiKey", newValue).commit()
            }
        }

        screen.addPreference(hostnamePref)
        screen.addPreference(apiKeyPref)
    }

    // Helper
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

    private fun getArtist(tags: String): String {
        tags.split(',').forEach {
            if (it.contains(':')) {
                val temp = it.trim().split(':')

                if (temp[0].equals("artist", true)) return temp[1]
            }
        }

        return "N/A"
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
}
