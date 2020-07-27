package eu.kanade.tachiyomi.extension.all.nhentai

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.getArtists
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.getGroups
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.getNumPages
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.getTagDescription
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.getTags
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.getTime
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
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

    private val rateLimitInterceptor = RateLimitInterceptor(4)
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private var displayFullTitle: Boolean = when (preferences.getString(TITLE_PREF, "full")) {
        "full" -> true
        else -> false
    }

    private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
    private fun String.shortenTitle() = this.replace(shortenTitleRegex, "").trim()

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val serverPref = androidx.preference.ListPreference(screen.context).apply {
            key = TITLE_PREF
            title = TITLE_PREF
            entries = arrayOf("Full Title", "Short Title")
            entryValues = arrayOf("full", "short")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                displayFullTitle = when (newValue) {
                    "full" -> true
                    else -> false
                }
                true
            }
        }

        if (!preferences.contains(TITLE_PREF))
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
                displayFullTitle = when (newValue) {
                    "full" -> true
                    else -> false
                }
                true
            }
        }

        if (!preferences.contains(TITLE_PREF))
            preferences.edit().putString(TITLE_PREF, "full").apply()

        screen.addPreference(serverPref)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/language/$nhLang/?page=$page", headers)

    override fun latestUpdatesSelector() = "#content .gallery"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a > div").text().replace("\"", "").let {
            if (displayFullTitle) it.trim() else it.shortenTitle()
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
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH)
                client.newCall(searchMangaByIdRequest(id))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, id) }
            }
            query.isQueryIdNumbers() -> {
                client.newCall(searchMangaByIdRequest(query))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, query) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    // The website redirects for any number <= 400000
    private fun String.isQueryIdNumbers(): Boolean {
        val int = this.toIntOrNull() ?: return false
        return int <= 400000
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val advQuery = combineQuery(filterList)
        val favoriteFilter = filterList.findInstance<FavoriteFilter>()
        val isOkayToSort = filterList.findInstance<UploadedFilter>()?.state?.isBlank() ?: true

        if (favoriteFilter?.state == true) {
            val url = HttpUrl.parse("$baseUrl/favorites")!!.newBuilder()
                .addQueryParameter("q", "$query $advQuery")
                .addQueryParameter("page", page.toString())

            return GET(url.toString(), headers)
        } else {
            val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
                .addQueryParameter("q", "$query +$nhLang $advQuery")
                .addQueryParameter("page", page.toString())

            if (isOkayToSort) {
                filterList.findInstance<SortFilter>()?.let { f ->
                    url.addQueryParameter("sort", f.toUriPart())
                }
            }

            return GET(url.toString(), headers)
        }
    }

    private fun combineQuery(filters: FilterList): String {
        val stringBuilder = StringBuilder()
        val advSearch = filters.filterIsInstance<AdvSearchEntryFilter>().flatMap { filter ->
            val splitState = filter.state.split(",").map(String::trim).filterNot(String::isBlank)
            splitState.map {
                AdvSearchEntry(filter.name, it.removePrefix("-"), it.startsWith("-"))
            }
        }

        advSearch.forEach { entry ->
            if (entry.exclude) stringBuilder.append("-")
            stringBuilder.append("${entry.name}:")
            stringBuilder.append(entry.text)
            stringBuilder.append(" ")
        }

        return stringBuilder.toString()
    }

    data class AdvSearchEntry(val name: String, val text: String, val exclude: Boolean)

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/g/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/g/$id/"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request().url().toString().contains("/login/")) {
            val document = response.asJsoup()
            if (document.select(".fa-sign-in").isNotEmpty()) {
                throw Exception("Log in via WebView to view favorites")
            }
        }

        return super.searchMangaParse(response)
    }

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val fullTitle = document.select("#info > h1").text().replace("\"", "").trim()

        return SManga.create().apply {
            title = if (displayFullTitle) fullTitle else fullTitle.shortenTitle()
            thumbnail_url = document.select("#cover > a > img").attr("data-src")
            status = SManga.COMPLETED
            artist = getArtists(document)
            author = artist
            // Some people want these additional details in description
            description = "Full English and Japanese titles:\n"
                .plus("$fullTitle\n")
                .plus("${document.select("div#info h2").text()}\n\n")
                .plus("Pages: ${getNumPages(document)}\n")
                .plus("Favorited by: ${document.select("div#info i.fa-heart + span span").text().removeSurrounding("(", ")")}\n")
                .plus(getTagDescription(document))
            genre = getTags(document)
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

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.thumbs a > img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src").replace("t.nh", "i.nh").replace("t.", "."))
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TagFilter(),
        CategoryFilter(),
        GroupFilter(),
        ArtistFilter(),
        ParodyFilter(),
        CharactersFilter(),
        Filter.Header("Uploaded valid units are h, d, w, m, y."),
        Filter.Header("example: (>20d)"),
        UploadedFilter(),

        Filter.Separator(),
        SortFilter(),
        Filter.Header("Sort is ignored if favorites only"),
        FavoriteFilter()
    )

    class TagFilter : AdvSearchEntryFilter("Tags")
    class CategoryFilter : AdvSearchEntryFilter("Categories")
    class GroupFilter : AdvSearchEntryFilter("Groups")
    class ArtistFilter : AdvSearchEntryFilter("Artists")
    class ParodyFilter : AdvSearchEntryFilter("Parodies")
    class CharactersFilter : AdvSearchEntryFilter("Characters")
    class UploadedFilter : AdvSearchEntryFilter("Uploaded")
    open class AdvSearchEntryFilter(name: String) : Filter.Text(name)

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    private class FavoriteFilter : Filter.CheckBox("Show favorites only", false)

    private class SortFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Popular: All Time", "popular"),
            Pair("Popular: Week", "popular-week"),
            Pair("Popular: Today", "popular-today"),
            Pair("Recent", "date")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        private const val TITLE_PREF = "Display manga title as:"
    }
}
