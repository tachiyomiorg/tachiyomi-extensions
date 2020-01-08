package eu.kanade.tachiyomi.extension.all.mangadex

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import com.github.salomonbrys.kotson.forEach
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.keys
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
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
import org.jsoup.parser.Parser
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.collections.set

abstract class Mangadex(
    override val lang: String,
    private val internalLang: String,
    private val langCode: Int
) : ConfigurableSource, ParsedHttpSource() {

    override val name = "MangaDex"

    override val baseUrl = "https://mangadex.cc" // mangadex.org

    private val cdnUrl = "https://mangadex.cc"

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    private fun clientBuilder(): OkHttpClient = clientBuilder(getShowR18())

    private fun clientBuilder(r18Toggle: Int): OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .addNetworkInterceptor { chain ->
            val originalCookies = chain.request().header("Cookie") ?: ""
            val newReq = chain
                .request()
                .newBuilder()
                .header("Cookie", "$originalCookies; ${cookiesHeader(r18Toggle, langCode)}")
                .build()
            chain.proceed(newReq)
        }.build()!!

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi " + System.getProperty("http.agent"))
    }

    private fun cookiesHeader(r18Toggle: Int, langCode: Int): String {
        val cookies = mutableMapOf<String, String>()
        cookies["mangadex_h_toggle"] = r18Toggle.toString()
        cookies["mangadex_filter_langs"] = langCode.toString()
        return buildCookies(cookies)
    }

    private fun buildCookies(cookies: Map<String, String>) = cookies.entries.joinToString(separator = "; ", postfix = ";") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    override fun popularMangaSelector() = "div.manga-entry"

    override fun latestUpdatesSelector() = "tr a.manga_title"

    // url matches default SortFilter selection (Rating Descending)
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/titles/7/$page/", headersBuilder().build())
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/updates/$page", headersBuilder().build())
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.manga_title").first().let {
            val url = modifyMangaUrl(it.attr("href"))
            manga.setUrlWithoutDomain(url)
            manga.title = it.text().trim()
        }
        manga.thumbnail_url = formThumbUrl(manga.url)
        return manga
    }

    private fun modifyMangaUrl(url: String): String = url.replace("/title/", "/manga/").substringBeforeLast("/") + "/"

    private fun formThumbUrl(mangaUrl: String): String {
        var ext = ".jpg"

        if (getShowThumbnail() == LOW_QUALITY) {
            ext = ".thumb$ext"
        }

        return cdnUrl + "/images/manga/" + getMangaId(mangaUrl) + ext
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.let {
            manga.setUrlWithoutDomain(modifyMangaUrl(it.attr("href")))
            manga.title = it.text().trim()

        }
        manga.thumbnail_url = formThumbUrl(manga.url)

        return manga
    }

    override fun popularMangaNextPageSelector() = ".pagination li:not(.disabled) span[title*=last page]:not(disabled)"

    override fun latestUpdatesNextPageSelector() = ".pagination li:not(.disabled) span[title*=last page]:not(disabled)"

    override fun searchMangaNextPageSelector() = ".pagination li:not(.disabled) span[title*=last page]:not(disabled)"

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return clientBuilder().newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return clientBuilder().newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response)
            }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/manga/$realQuery/"
                    MangasPage(listOf(details), false)
                }
        } else {
            getSearchClient(filters).newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    private fun getSearchClient(filters: FilterList): OkHttpClient {
        filters.forEach { filter ->
            when (filter) {
                is R18 -> {
                    return when (filter.state) {
                        1 -> clientBuilder(ALL)
                        2 -> clientBuilder(ONLY_R18)
                        3 -> clientBuilder(NO_R18)
                        else -> clientBuilder()
                    }
                }
            }
        }
        return clientBuilder()
    }

    private var groupSearch = ""

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genresToInclude = mutableListOf<String>()
        val genresToExclude = mutableListOf<String>()

        // Do traditional search
        val url = HttpUrl.parse("$baseUrl/?page=search")!!.newBuilder()
            .addQueryParameter("p", page.toString())
            .addQueryParameter("title", query.replace(WHITESPACE_REGEX, " "))

        filters.forEach { filter ->
            when (filter) {
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                is Demographic -> {
                    val demographicToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.isIncluded()) {
                            demographicToInclude.add(content.id)
                        }
                    }
                    if (demographicToInclude.isNotEmpty()) {
                        url.addQueryParameter("demos", demographicToInclude.joinToString(","))
                    }
                }
                is PublicationStatus -> {
                    val publicationToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.isIncluded()) {
                            publicationToInclude.add(content.id)
                        }
                    }
                    if (publicationToInclude.isNotEmpty()) {
                        url.addQueryParameter("statuses", publicationToInclude.joinToString(","))
                    }
                }
                is OriginalLanguage -> {
                    if (filter.state != 0) {
                        val number: String = SOURCE_LANG_LIST.first { it.first == filter.values[filter.state] }.second
                        url.addQueryParameter("lang_id", number)
                    }
                }
                is TagInclusionMode -> {
                    url.addQueryParameter("tag_mode_inc", arrayOf("all", "any")[filter.state])
                }
                is TagExclusionMode -> {
                    url.addQueryParameter("tag_mode_exc", arrayOf("all", "any")[filter.state])
                }
                is ContentList -> {
                    filter.state.forEach { content ->
                        if (content.isExcluded()) {
                            genresToExclude.add(content.id)
                        } else if (content.isIncluded()) {
                            genresToInclude.add(content.id)
                        }
                    }
                }
                is FormatList -> {
                    filter.state.forEach { format ->
                        if (format.isExcluded()) {
                            genresToExclude.add(format.id)
                        } else if (format.isIncluded()) {
                            genresToInclude.add(format.id)
                        }
                    }
                }
                is GenreList -> {
                    filter.state.forEach { genre ->
                        if (genre.isExcluded()) {
                            genresToExclude.add(genre.id)
                        } else if (genre.isIncluded()) {
                            genresToInclude.add(genre.id)
                        }
                    }
                }
                is ThemeList -> {
                    filter.state.forEach { theme ->
                        if (theme.isExcluded()) {
                            genresToExclude.add(theme.id)
                        } else if (theme.isIncluded()) {
                            genresToInclude.add(theme.id)
                        }
                    }
                }
                is SortFilter -> {
                    if (filter.state != null) {
                        if (filter.state!!.ascending) {
                            url.addQueryParameter("s", sortables[filter.state!!.index].second.toString())
                        } else {
                            url.addQueryParameter("s", sortables[filter.state!!.index].third.toString())
                        }
                    }
                }
                is ScanGroup -> {
                    groupSearch = when {
                        filter.state.isNotEmpty() && page == 1 -> "$baseUrl/groups/0/1/${filter.state}"
                        filter.state.isNotEmpty() && page > 1 -> groupSearch.dropLast(1) + page
                        else -> ""
                    }
                }
            }
        }

        // Manually append genres list to avoid commas being encoded
        var urlToUse = url.toString()
        if (genresToInclude.isNotEmpty()) {
            urlToUse += "&tags_inc=" + genresToInclude.joinToString(",")
        }
        if (genresToExclude.isNotEmpty()) {
            urlToUse += "&tags_exc=" + genresToExclude.joinToString(",")
        }

        return if (groupSearch.isNotEmpty()) {
            GET(groupSearch, headersBuilder().build())
        } else {
            GET(urlToUse, headersBuilder().build())
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request().url().toString().contains("/groups/")) {
            response.asJsoup().select(".table > tbody:nth-child(2) > tr:nth-child(1) > td:nth-child(2) > a:nth-child(1)").attr("abs:href").let {
                return if (it.isNotEmpty()) {
                    groupSearch = "$it/manga/0/1"
                    super.searchMangaParse(client.newCall(GET(groupSearch, headersBuilder().build())).execute())
                } else {
                    MangasPage(emptyList(), false)
                }
            }
        } else {
            return super.searchMangaParse(response)
        }
    }

    override fun searchMangaSelector() = "div.manga-entry"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("a.manga_title").first().let {
            val url = modifyMangaUrl(it.attr("href"))
            manga.setUrlWithoutDomain(url)
            manga.title = it.text().trim()
        }

        manga.thumbnail_url = formThumbUrl(manga.url)

        return manga
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return clientBuilder().newCall(apiRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun apiRequest(manga: SManga): Request {
        return GET(baseUrl + API_MANGA + getMangaId(manga.url), headers)
    }

    private fun searchMangaByIdRequest(id: String): Request {
        return GET(baseUrl + API_MANGA + id, headers)
    }

    private fun getMangaId(url: String): String {
        val lastSection = url.trimEnd('/').substringAfterLast("/")
        return if (lastSection.toIntOrNull() != null) {
            lastSection
        } else {
            //this occurs if person has manga from before that had the id/name/
            url.trimEnd('/').substringBeforeLast("/").substringAfterLast("/")
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = SManga.create()
        val jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject
        val mangaJson = json.getAsJsonObject("manga")
        val chapterJson = json.getAsJsonObject("chapter")
        manga.title = cleanString(mangaJson.get("title").string)
        manga.thumbnail_url = cdnUrl + mangaJson.get("cover_url").string
        manga.description = cleanString(mangaJson.get("description").string)
        manga.author = mangaJson.get("author").string
        manga.artist = mangaJson.get("artist").string
        val status = mangaJson.get("status").int
        val finalChapterNumber = getFinalChapter(mangaJson)
        if ((status == 2 || status == 3) && chapterJson != null && isMangaCompleted(chapterJson, finalChapterNumber)) {
            manga.status = SManga.COMPLETED
        } else if (status == 2 && chapterJson != null && isOneshot(chapterJson, finalChapterNumber)) {
            manga.status = SManga.COMPLETED
        } else {
            manga.status = parseStatus(status)
        }

        val genres = (if (mangaJson.get("hentai").int == 1) listOf("Hentai") else listOf()) +
            mangaJson.get("genres").asJsonArray.mapNotNull { GENRES[it.toString()] }
        manga.genre = genres.joinToString(", ")

        return manga
    }

    // Remove bbcode tags as well as parses any html characters in description or chapter name to actual characters for example &hearts; will show ♥
    private fun cleanString(string: String): String {
        val bbRegex = """\[(\w+)[^]]*](.*?)\[/\1]""".toRegex()
        var intermediate = string
            .replace("[list]", "")
            .replace("[/list]", "")
            .replace("[*]", "")
        // Recursively remove nested bbcode
        while (bbRegex.containsMatchIn(intermediate)) {
            intermediate = intermediate.replace(bbRegex, "$2")
        }
        return Parser.unescapeEntities(intermediate, false)
    }

    override fun mangaDetailsParse(document: Document) = throw Exception("Not Used")

    override fun chapterListSelector() = ""

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return clientBuilder().newCall(apiRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response)
            }
    }

    private fun getFinalChapter(jsonObj: JsonObject): String = jsonObj.get("last_chapter").string.trim()

    private fun isOneshot(chapterJson: JsonObject, lastChapter: String): Boolean {
        val chapter = chapterJson.takeIf { it.size() > 0 }?.get(chapterJson.keys().elementAt(0))?.obj?.get("title")?.string
        return if (chapter != null) {
            chapter == "Oneshot" || chapter.isEmpty() && lastChapter == "0"
        } else {
            false
        }
    }

    private fun isMangaCompleted(chapterJson: JsonObject, finalChapterNumber: String): Boolean {
        val count = chapterJson.entrySet()
            .filter { it.value.asJsonObject.get("lang_code").string == internalLang }
            .filter { doesFinalChapterExist(finalChapterNumber, it.value) }.count()
        return count != 0
    }

    private fun doesFinalChapterExist(finalChapterNumber: String, chapterJson: JsonElement) = finalChapterNumber.isNotEmpty() && finalChapterNumber == chapterJson["chapter"].string.trim()

    override fun chapterListParse(response: Response): List<SChapter> {
        val now = Date().time
        val jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject
        val mangaJson = json.getAsJsonObject("manga")
        val status = mangaJson.get("status").int

        val finalChapterNumber = getFinalChapter(mangaJson)
        val chapterJson = json.getAsJsonObject("chapter")
        val chapters = mutableListOf<SChapter>()

        // Skip chapters that don't match the desired language, or are future releases
        chapterJson?.forEach { key, jsonElement ->
            val chapterElement = jsonElement.asJsonObject
            if (chapterElement.get("lang_code").string == internalLang && (chapterElement.get("timestamp").asLong * 1000) <= now) {
                chapters.add(chapterFromJson(key, chapterElement, finalChapterNumber, status))
            }
        }
        return chapters
    }

    private fun chapterFromJson(chapterId: String, chapterJson: JsonObject, finalChapterNumber: String, status: Int): SChapter {
        val chapter = SChapter.create()
        chapter.url = API_CHAPTER + chapterId
        val chapterName = mutableListOf<String>()
        // Build chapter name
        if (chapterJson.get("volume").string.isNotBlank()) {
            chapterName.add("Vol." + chapterJson.get("volume").string)
        }
        if (chapterJson.get("chapter").string.isNotBlank()) {
            chapterName.add("Ch." + chapterJson.get("chapter").string)
        }
        if (chapterJson.get("title").string.isNotBlank()) {
            if (chapterName.isNotEmpty()) {
                chapterName.add("-")
            }
            chapterName.add(chapterJson.get("title").string)
        }
        //if volume, chapter and title is empty its a oneshot
        if (chapterName.isEmpty()) {
            chapterName.add("Oneshot")
        }
        if ((status == 2 || status == 3) && doesFinalChapterExist(finalChapterNumber, chapterJson)) {
            chapterName.add("[END]")
        }

        chapter.name = cleanString(chapterName.joinToString(" "))
        // Convert from unix time
        chapter.date_upload = chapterJson.get("timestamp").long * 1000
        val scanlatorName = mutableListOf<String>()
        if (!chapterJson.get("group_name").nullString.isNullOrBlank()) {
            scanlatorName.add(chapterJson.get("group_name").string)
        }
        if (!chapterJson.get("group_name_2").nullString.isNullOrBlank()) {
            scanlatorName.add(chapterJson.get("group_name_2").string)
        }
        if (!chapterJson.get("group_name_3").nullString.isNullOrBlank()) {
            scanlatorName.add(chapterJson.get("group_name_3").string)
        }
        chapter.scanlator = cleanString(scanlatorName.joinToString(" & "))

        return chapter
    }

    override fun chapterFromElement(element: Element) = throw Exception("Not used")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    if (response.code() == 451) {
                        error("Error 451: Log in to view manga; contact MangaDex if error persists.")
                    } else {
                        throw Exception("HTTP error ${response.code()}")
                    }
                }
            }
            .map { response ->
                pageListParse(response)
            }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.scanlator == "MangaPlus") {
            throw Exception("Chapter is licensed; use the MangaPlus extension")
        }

        val server = getServer()
        return GET("$baseUrl${chapter.url}?server=$server", headers)
    }

    override fun pageListParse(document: Document) = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> {
        val jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject

        val pages = mutableListOf<Page>()

        val hash = json.get("hash").string
        val pageArray = json.getAsJsonArray("page_array")
        val server = json.get("server").string

        pageArray.forEach {
            val url = "$server$hash/${it.asString}"
            pages.add(Page(pages.size, "", getImageUrl(url)))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = ""

    private fun parseStatus(status: Int) = when (status) {
        1 -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    private fun getImageUrl(attr: String): String {
        // Some images are hosted elsewhere
        if (attr.startsWith("http")) {
            return attr
        }
        return baseUrl + attr
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val myPref = ListPreference(screen.context).apply {
            key = SHOW_R18_PREF_Title
            title = SHOW_R18_PREF_Title

            title = SHOW_R18_PREF_Title
            entries = arrayOf("Show No R18+", "Show All", "Show Only R18+")
            entryValues = arrayOf("0", "1", "2")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                preferences.edit().putInt(SHOW_R18_PREF, index).commit()
            }
        }
        val thumbsPref = ListPreference(screen.context).apply {
            key = SHOW_THUMBNAIL_PREF_Title
            title = SHOW_THUMBNAIL_PREF_Title
            entries = arrayOf("Show high quality", "Show low quality")
            entryValues = arrayOf("0", "1")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                preferences.edit().putInt(SHOW_THUMBNAIL_PREF, index).commit()
            }
        }
        val serverPref = ListPreference(screen.context).apply {
            key = SERVER_PREF_Title
            title = SERVER_PREF_Title
            entries = SERVER_PREF_ENTRIES
            entryValues = SERVER_PREF_ENTRY_VALUES
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(SERVER_PREF, entry).commit()
            }
        }

        screen.addPreference(myPref)
        screen.addPreference(thumbsPref)
        screen.addPreference(serverPref)
    }

    private fun getShowR18(): Int = preferences.getInt(SHOW_R18_PREF, 0)
    private fun getShowThumbnail(): Int = preferences.getInt(SHOW_THUMBNAIL_PREF, 0)
    private fun getServer(): String {
        val default = SERVER_PREF_ENTRY_VALUES.first()
        return preferences.getString(SERVER_PREF, default).takeIf { it in SERVER_PREF_ENTRY_VALUES }
            ?: default
    }

    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Tag(val id: String, name: String) : Filter.TriState(name)
    private class Demographic(demographics: List<Tag>) : Filter.Group<Tag>("Demographic", demographics)
    private class PublicationStatus(publications: List<Tag>) : Filter.Group<Tag>("Publication", publications)
    private class ContentList(contents: List<Tag>) : Filter.Group<Tag>("Content", contents)
    private class FormatList(formats: List<Tag>) : Filter.Group<Tag>("Format", formats)
    private class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
    private class R18 : Filter.Select<String>("R18+", arrayOf("Default", "Show all", "Show only", "Show none"))
    private class ScanGroup(name: String) : Filter.Text(name)

    private fun getDemographic() = listOf(
        Tag("1", "Shounen"),
        Tag("2", "Shoujo"),
        Tag("3", "Seinen"),
        Tag("4", "Josei")
    ).sortedWith(compareBy { it.name })

    private fun getPublicationStatus() = listOf(
        Tag("1", "Ongoing"),
        Tag("2", "Completed"),
        Tag("3", "Cancelled"),
        Tag("4", "Hiatus")
    ).sortedWith(compareBy { it.name })

    private class ThemeList(themes: List<Tag>) : Filter.Group<Tag>("Themes", themes)
    private class TagInclusionMode : Filter.Select<String>("Tag inclusion mode", arrayOf("All (and)", "Any (or)"), 0)
    private class TagExclusionMode : Filter.Select<String>("Tag exclusion mode", arrayOf("All (and)", "Any (or)"), 1)

    // default selection (Rating Descending) matches popularMangaRequest url
    class SortFilter : Filter.Sort("Sort",
        sortables.map { it.first }.toTypedArray(),
        Selection(3, false))

    private class OriginalLanguage : Filter.Select<String>("Original Language", SOURCE_LANG_LIST.map { it.first }.toTypedArray())

    override fun getFilterList() = FilterList(
        TextField("Author", "author"),
        TextField("Artist", "artist"),
        R18(),
        SortFilter(),
        Demographic(getDemographic()),
        PublicationStatus(getPublicationStatus()),
        OriginalLanguage(),
        ContentList(getContentList()),
        FormatList(getFormatList()),
        GenreList(getGenreList()),
        ThemeList(getThemeList()),
        TagInclusionMode(),
        TagExclusionMode(),
        Filter.Separator(),
        Filter.Header("Group search ignores other inputs"),
        ScanGroup("Search for manga by scanlator group")
    )

    private fun getContentList() = listOf(
        Tag("9", "Ecchi"),
        Tag("32", "Smut"),
        Tag("49", "Gore"),
        Tag("50", "Sexual Violence")
    ).sortedWith(compareBy { it.name })

    private fun getFormatList() = listOf(
        Tag("1", "4-koma"),
        Tag("4", "Award Winning"),
        Tag("7", "Doujinshi"),
        Tag("21", "Oneshot"),
        Tag("36", "Long Strip"),
        Tag("42", "Adaptation"),
        Tag("43", "Anthology"),
        Tag("44", "Web Comic"),
        Tag("45", "Full Color"),
        Tag("46", "User Created"),
        Tag("47", "Official Colored"),
        Tag("48", "Fan Colored")
    ).sortedWith(compareBy { it.name })

    private fun getGenreList() = listOf(
        Tag("2", "Action"),
        Tag("3", "Adventure"),
        Tag("5", "Comedy"),
        Tag("8", "Drama"),
        Tag("10", "Fantasy"),
        Tag("13", "Historical"),
        Tag("14", "Horror"),
        Tag("17", "Mecha"),
        Tag("18", "Medical"),
        Tag("20", "Mystery"),
        Tag("22", "Psychological"),
        Tag("23", "Romance"),
        Tag("25", "Sci-Fi"),
        Tag("28", "Shoujo Ai"),
        Tag("30", "Shounen Ai"),
        Tag("31", "Slice of Life"),
        Tag("33", "Sports"),
        Tag("35", "Tragedy"),
        Tag("37", "Yaoi"),
        Tag("38", "Yuri"),
        Tag("41", "Isekai"),
        Tag("51", "Crime"),
        Tag("52", "Magical Girls"),
        Tag("53", "Philosophical"),
        Tag("54", "Superhero"),
        Tag("55", "Thriller"),
        Tag("56", "Wuxia")
    ).sortedWith(compareBy { it.name })

    private fun getThemeList() = listOf(
        Tag("6", "Cooking"),
        Tag("11", "Gyaru"),
        Tag("12", "Harem"),
        Tag("16", "Martial Arts"),
        Tag("19", "Music"),
        Tag("24", "School Life"),
        Tag("34", "Supernatural"),
        Tag("40", "Video Games"),
        Tag("57", "Aliens"),
        Tag("58", "Animals"),
        Tag("59", "Crossdressing"),
        Tag("60", "Demons"),
        Tag("61", "Delinquents"),
        Tag("62", "Genderswap"),
        Tag("63", "Ghosts"),
        Tag("64", "Monster Girls"),
        Tag("65", "Loli"),
        Tag("66", "Magic"),
        Tag("67", "Military"),
        Tag("68", "Monsters"),
        Tag("69", "Ninja"),
        Tag("70", "Office Workers"),
        Tag("71", "Police"),
        Tag("72", "Post-Apocalyptic"),
        Tag("73", "Reincarnation"),
        Tag("74", "Reverse Harem"),
        Tag("75", "Samurai"),
        Tag("76", "Shota"),
        Tag("77", "Survival"),
        Tag("78", "Time Travel"),
        Tag("79", "Vampires"),
        Tag("80", "Traditional Games"),
        Tag("81", "Virtual Reality"),
        Tag("82", "Zombies"),
        Tag("83", "Incest")
    ).sortedWith(compareBy { it.name })

    private val GENRES = (getContentList() + getFormatList() + getGenreList() + getThemeList()).map { it.id to it.name }.toMap()

    companion object {
        private val WHITESPACE_REGEX = "\\s".toRegex()

        // This number matches to the cookie
        private const val NO_R18 = 0
        private const val ALL = 1
        private const val ONLY_R18 = 2

        private const val SHOW_R18_PREF_Title = "Default R18 Setting"
        private const val SHOW_R18_PREF = "showR18Default"

        private const val LOW_QUALITY = 1

        private const val SHOW_THUMBNAIL_PREF_Title = "Default thumbnail quality"
        private const val SHOW_THUMBNAIL_PREF = "showThumbnailDefault"

        private const val SERVER_PREF_Title = "Image server"
        private const val SERVER_PREF = "imageServer"
        private val SERVER_PREF_ENTRIES = arrayOf("Automatic", "NA/EU 1", "NA/EU 2", "Rest of the world")
        private val SERVER_PREF_ENTRY_VALUES = arrayOf("0", "na", "na2", "row")

        private const val API_MANGA = "/api/manga/"
        private const val API_CHAPTER = "/api/chapter/"

        const val PREFIX_ID_SEARCH = "id:"

        private val sortables = listOf(
            Triple("Update date", 0, 1),
            Triple("Alphabetically", 2, 3),
            Triple("Number of comments", 4, 5),
            Triple("Rating", 6, 7),
            Triple("Views", 8, 9),
            Triple("Follows", 10, 11))

        private val SOURCE_LANG_LIST = listOf(
            Pair("All", "0"),
            Pair("Japanese", "2"),
            Pair("English", "1"),
            Pair("Polish", "3"),
            Pair("German", "8"),
            Pair("French", "10"),
            Pair("Vietnamese", "12"),
            Pair("Chinese", "21"),
            Pair("Indonesian", "27"),
            Pair("Korean", "28"),
            Pair("Spanish (LATAM)", "29"),
            Pair("Thai", "32"),
            Pair("Filipino", "34"))
    }
}
