package eu.kanade.tachiyomi.extension.all.lanraragi

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

open class LANraragi(override val lang: String) : ConfigurableSource, HttpSource() {

    override val name = "LANraragi"

    override val baseUrl: String
        get() = preferences.getString("hostname", "http://127.0.0.1:3000")

    override val supportsLatest = true

    private val apiKey: String
        get() = preferences.getString("apikey", "")

    override fun chapterListParse(response: Response): List<SChapter> {
        val id = response.request().url().queryParameter("id").toString()

        val uriBuilder = Uri.parse("$baseUrl/api/extract").buildUpon()
        uriBuilder.appendQueryParameter("id", id)
        if(apiKey.isNotEmpty()) {
            uriBuilder.appendQueryParameter("key", apiKey)
        }

        val chapters = ArrayList<SChapter>()
        chapters.add(SChapter.create().apply {
            val uri = uriBuilder.build()

            url = "${uri.encodedPath}?${uri.encodedQuery}"
            chapter_number = 1F
            name = "Chapter 1"
        })
        return chapters
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("imageUrlParse is unused: ${response.request().url().encodedPath()}")

    override fun latestUpdatesParse(response: Response) = genericMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList())

    override fun mangaDetailsParse(response: Response): SManga {
        return SManga.create()
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageList = Gson().fromJson<PageList>(response.body()!!.string())

        val pages = ArrayList<Page>()
        var i = 0
        pageList.pages.forEach { url ->
            val uri = Uri.parse("$baseUrl/${url.trimStart('.')}")

            pages.add(Page(i++, uri.toString(), uri.toString(), uri))
        }
        return pages
    }

    override fun popularMangaParse(response: Response) = genericMangaParse(response)

    override fun popularMangaRequest(page: Int)= searchMangaRequest(page, "", FilterList())

    override fun searchMangaParse(response: Response) = genericMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/api/archivelist").buildUpon()

        if(query.isNotEmpty()) {
            uri.appendQueryParameter("query", query)
        }

        if(apiKey.isNotEmpty()) {
            uri.appendQueryParameter("key", apiKey)
        }

        return GET(uri.toString())
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
                if(!hostname.startsWith("http://") && !hostname.startsWith("https://")) {
                    hostname = "http://$hostname"
                }

                summary = hostname
                preferences.edit().putString("hostname", hostname).commit()
            }
        }

        val apikeyPref = EditTextPreference(screen.context).apply {
            key = "API Key"
            title = "API Key"
            text = apiKey
            summary = apiKey.replace(Regex("."), "*")
            dialogTitle = "API Key"

            setOnPreferenceChangeListener { _, newValue ->
                summary = (newValue as String).replace(Regex("."), "*")
                preferences.edit().putString("apikey", newValue).commit()
            }
        }

        screen.addPreference(hostnamePref)
        screen.addPreference(apikeyPref)
    }

    private fun genericMangaParse(response: Response): MangasPage {
        val archives = Gson().fromJson<List<Archive>>(response.body()!!.string())

        val mangas = ArrayList<SManga>()
        val queries = response.request().url().queryParameter("query")?.trim()?.split(Regex(" (?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))

        archives.forEach archiveForEach@{ a ->
            val shortTags = ArrayList<String>()
            val longTags = HashMap<String, ArrayList<String>>()

            val tags = a.tags.trim().split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
            tags.forEach { t ->
                if(t.isNotEmpty()) {
                    val split = t.trim().trim('"').split(':', limit = 2)
                    if (split.size > 1) {
                        val parent = split[0]
                        val child = split[1]

                        shortTags.add(child)
                        if(!longTags.containsKey(parent)) {
                            longTags[parent] = ArrayList()
                        }
                        longTags[parent]!!.add(child)
                    } else {
                        val child = split[0]
                        shortTags.add(child)
                    }
                }
            }

            var tagsMatched = true
            var excludedTag = false
            var titleMatched = false
            queries?.forEach queryForEach@{ q ->
                if(q.isNotEmpty()) {
                    val exclusionMode = q.startsWith('-')
                    val split = q.trim().trimStart('-').trim('"').split(':', limit = 2)

                    if (split.size > 1) {
                        val parent = split[0]
                        val child = split[1]

                        run loop@{
                            longTags[parent]?.forEach { t ->
                                if(t.equals(child, true)) {
                                    if(exclusionMode) {
                                        excludedTag = true
                                    }
                                    return@loop
                                }
                            }

                            tagsMatched = false
                        }
                    } else {
                        val child = split[0]

                        run loop@{
                            shortTags.forEach { t ->
                                if(t.equals(child, true)) {
                                    if(exclusionMode) {
                                        excludedTag = true
                                    }
                                    return@loop
                                }
                            }

                            tagsMatched = false
                        }
                    }

                    if (!exclusionMode && a.title.contains(q, true)) {
                        titleMatched = true
                    }
                }
            }

            if(queries == null || ((tagsMatched || titleMatched) && !excludedTag)) {
                mangas.add(SManga.create().apply {
                    url = "/api/extract?id=${a.arcid}"
                    title = a.title
                    thumbnail_url = getThumbnailsUri(a.arcid)
                    artist = longTags["artist"]?.joinToString()
                    author = artist
                    genre = a.tags

                    if(apiKey.isNotEmpty()) {
                        url = "$url&apiKey=$apiKey"
                    }
                })
            }
        }

        return MangasPage(mangas, false)
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun getThumbnailsUri(id: String): String {
        val uri = Uri.parse("$baseUrl/api/thumbnail").buildUpon()
        uri.appendQueryParameter("id", id)

        if(apiKey.isNotEmpty()) {
            uri.appendQueryParameter("key", apiKey)
        }

        return uri.toString()
    }
}
