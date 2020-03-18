package eu.kanade.tachiyomi.extension.all.nhentai

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getArtists
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getGroups
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getTags
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getTime
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

open class NHentai(
    override val lang: String,
    private val nhLang: String
) : ConfigurableSource, ParsedHttpSource() {

    final override val baseUrl = "https://nhentai.net"

    override val name = "NHentai"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi/${BuildConfig.VERSION_NAME} ${System.getProperty("http.agent")}")
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private var displayFullTitle: Boolean = when(preferences.getString(TITLE_PREF, "full")){
        "full" -> true
        else -> false
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val serverPref = androidx.preference.ListPreference(screen.context).apply {
            key = TITLE_PREF
            title = TITLE_PREF
            entries = arrayOf("Full Title", "Short Title")
            entryValues = arrayOf("full", "short")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                displayFullTitle = when(newValue){
                    "full" -> true
                    else -> false
                }
                true
            }
        }

        if(!preferences.contains(TITLE_PREF))
            preferences.edit().putString(TITLE_PREF, "full").apply()

        screen.addPreference(serverPref)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val serverPref = ListPreference(screen.context).apply {
            key = TITLE_PREF
            title = TITLE_PREF
            entries = arrayOf("Full Title", "Short Title")
            entryValues = arrayOf("full", "short")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                displayFullTitle = when(newValue){
                    "full" -> true
                    else -> false
                }
                true
            }
        }

        if(!preferences.contains(TITLE_PREF))
            preferences.edit().putString(TITLE_PREF, "full").apply()

        screen.addPreference(serverPref)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/language/$nhLang/?page=$page", headers)

    override fun latestUpdatesSelector() = "#content .gallery"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a > div").text().replace("\"", "").let {
            if (displayFullTitle) it.trim() else it.substringAfter("]").substringBefore("[").trim()
        }
        thumbnail_url = element.select(".cover img").first().let { img ->
            if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")
        }
    }

    override fun latestUpdatesNextPageSelector() = "#content > section.pagination > a.next"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/language/$nhLang/popular?page=$page", headers)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            return super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
            .addQueryParameter("q", "$query +$nhLang")
            .addQueryParameter("page", page.toString())

        for (filter in if (filters.isEmpty()) getFilterList() else filters) {
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.values[filter.state].toLowerCase())
            }
        }
        return GET(url.toString(), headers)
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/g/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/g/$id/"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val fullTitle = document.select("#info > h1").text().replace("\"", "").trim()

        return SManga.create().apply {
            title = if (displayFullTitle) fullTitle else fullTitle.substringAfter("]").substringBefore("[").trim()
            thumbnail_url = document.select("#cover > a > img").attr("data-src")
            status = SManga.COMPLETED
            artist = getArtists(document)
            author = artist
            // Some people want these additional details in description
            description = "Full English and Japanese titles:\n"
                .plus("$fullTitle\n")
                .plus("${document.select("div#info h2").text()}\n\n")
                .plus("Length: ${document.select("div#info div:contains(pages)").text()}\n")
                .plus("Favorited by: ${document.select("div#info i.fa-heart + span span").text().removeSurrounding("(", ")")}\n")
                .plus("Categories: ${document.select("div.field-name:contains(Categories) span.tags a").first()?.ownText()}\n\n")
                .plus(getTags(document))
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(SChapter.create().apply {
            name = "Chapter"
            scanlator = getGroups(document)
            date_upload = getTime(document)
            setUrlWithoutDomain(response.request().url().encodedPath())
        })
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(document: Document): List<Page> {
        val pageElements = document.select("#thumbnail-container > div")
        val pageList = mutableListOf<Page>()

        pageElements.forEach {
            Page(pageList.size).run {
                this.imageUrl = it.select("a > img").attr("data-src").replace("t.nh", "i.nh").replace("t.", ".")

                pageList.add(pageList.size, this)
            }
        }

        return pageList
    }

    override fun getFilterList(): FilterList = FilterList(SortFilter())

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        private const val TITLE_PREF = "Display manga title as:"
    }

    private class SortFilter : Filter.Select<String>("Sort", arrayOf("Popular", "Date"))

}
