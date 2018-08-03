package eu.kanade.tachiyomi.extension.all.mangadex

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.TimeUnit

open class Mangadex(override val lang: String, private val internalLang: String, private val langCode: Int) : ConfigurableSource, ParsedHttpSource() {


    override val name = "MangaDex"

    override val baseUrl = "https://mangadex.org"

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val whitespaceRegex = "\\s".toRegex()

    private fun clientBuilder(): OkHttpClient = clientBuilder(getShowR18())


    private fun clientBuilder(r18Toggle: Int): OkHttpClient = network.cloudflareClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val newReq = chain
                        .request()
                        .newBuilder()
                        .addHeader("Cookie", cookiesHeader(r18Toggle, langCode))
                        .build()
                chain.proceed(newReq)
            }.build()!!

    private fun cookiesHeader(r18Toggle: Int, langCode: Int): String {
        val cookies = mutableMapOf<String, String>()
        cookies["mangadex_h_toggle"] = r18Toggle.toString()
        cookies["mangadex_filter_langs"] = langCode.toString()
        return buildCookies(cookies)
    }

    private fun buildCookies(cookies: Map<String, String>) = cookies.entries.joinToString(separator = "; ", postfix = ";") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    override fun popularMangaSelector() = "div.col-sm-6"

    override fun latestUpdatesSelector() = ".table-responsive tbody tr a.manga_title[href*=manga]"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/titles/0/$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/0/$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("div.large_logo img").first().let {
            manga.thumbnail_url = baseUrl + it.attr("src")
        }
        element.select("a.manga_title").first().let {
            val url = removeMangaNameFromUrl(it.attr("href"))
            manga.setUrlWithoutDomain(url)
            manga.title = it.text().trim()
            manga.author = it?.text()?.trim()
        }
        return manga
    }

    private fun removeMangaNameFromUrl(url: String): String = url.substringBeforeLast("/") + "/"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.let {
            manga.setUrlWithoutDomain(removeMangaNameFromUrl(it.attr("href")))
            manga.title = it.text().trim()

        }
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
        return getSearchClient(filters).newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
    }

    private fun getSearchClient(filters: FilterList): OkHttpClient {
        filters.forEach { filter ->
            when (filter) {
                is R18 -> {
                    return when (filter.state) {
                        1 -> clientBuilder(ONLY_R18)
                        2 -> clientBuilder(NO_R18)
                        else -> clientBuilder(ALL)
                    }
                }
            }
        }
        return clientBuilder(ALL)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val byGenre = filters.find { it is GenreList }
        val genresToInclude = mutableListOf<String>()
        val genresToExclude = mutableListOf<String>()

        if (byGenre != null) {
            byGenre as GenreList
            byGenre.state.forEach { genre ->
                if (genre.isExcluded()) {
                    genresToExclude.add(genre.id)
                } else if (genre.isIncluded()) {
                    genresToInclude.add(genre.id)
                }
            }
        }
        //do traditional search
        val url = HttpUrl.parse("$baseUrl/?page=search")!!.newBuilder().addQueryParameter("s", "0").addQueryParameter("p", page.toString()).addQueryParameter("title", query.replace(whitespaceRegex, " "))
        filters.forEach { filter ->
            when (filter) {
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                is Demographic -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("demo", filter.state.toString())
                    }
                }
                is OriginalLanguage -> {
                    if (filter.state != 0) {
                        val number: String = SOURCE_LANG_LIST.first { it -> it.first == filter.values[filter.state] }?.second
                        url.addQueryParameter("source_lang", number)
                    }
                }
            }
        }

        if (genresToInclude.isNotEmpty()) {
            url.addQueryParameter("genres_inc", genresToInclude.joinToString(","))
        }
        if (genresToExclude.isNotEmpty()) {
            url.addQueryParameter("genres_exc", genresToExclude.joinToString(","))
        }
        return GET(url.toString(), headers)

    }

    override fun searchMangaSelector() = "div.col-sm-6"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("div.large_logo img").first().let {
            manga.thumbnail_url = baseUrl + it.attr("src")
        }
        element.select("a.manga_title").first().let {
            val url = removeMangaNameFromUrl(it.attr("href"))
            manga.setUrlWithoutDomain(url)
            manga.title = it.text().trim()
            manga.author = it?.text()?.trim()
        }
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
        var jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject
        val mangaJson = json.getAsJsonObject("manga")
        val chapterJson = json.getAsJsonObject("chapter")
        manga.title = baseUrl + mangaJson.get("title").string
        manga.thumbnail_url = baseUrl + mangaJson.get("cover_url").string
        manga.description = cleanString(mangaJson.get("description").string)
        manga.author = mangaJson.get("author").string
        manga.artist = mangaJson.get("artist").string
        val status = mangaJson.get("status").int
        val finalChapterNumber = getFinalChapter(mangaJson)
        if ((status == 2 || status == 3) && chapterJson != null && isMangaCompleted(finalChapterNumber, chapterJson)) {
            manga.status = SManga.COMPLETED
        } else {
            manga.status = parseStatus(status)
        }

        var genres = mutableListOf<String>()

        mangaJson.get("genres").asJsonArray.forEach { id ->
            GENRE_LIST.find { it -> it.id == id.string }?.let { genre ->
                genres.add(genre.name)
            }
        }
        manga.genre = genres.joinToString(", ")

        return manga
    }

    //remove bbcode as well as parses any html characters in description or chapter name to actual characters for example &hearts will show a heart
    private fun cleanString(description: String): String {
        return Jsoup.parseBodyFragment(description.replace("[list]", "").replace("[/list]", "").replace("[*]", "").replace("""\[(\w+)[^\]]*](.*?)\[/\1]""".toRegex(), "$2")).text()
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

    private fun getFinalChapter(jsonObj: JsonObject) = jsonObj.get("last_chapter").string.trim()

    private fun isMangaCompleted(finalChapterNumber: String, chapterJson: JsonObject): Boolean {
        val count = chapterJson.entrySet()
                .filter { it -> it.value.asJsonObject.get("lang_code").string == internalLang }
                .filter { it -> doesFinalChapterExist(finalChapterNumber, it.value) }?.count()
        return when (count) {
            0 -> false
            else -> true
        }
    }

    private fun doesFinalChapterExist(finalChapterNumber: String, chapterJson: JsonElement) = finalChapterNumber.isNotEmpty() && finalChapterNumber == chapterJson.get("chapter").string.trim()

    override fun chapterListParse(response: Response): List<SChapter> {
        val now = Date().time
        val jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject
        val mangaJson = json.getAsJsonObject("manga")
        val status = mangaJson.get("status").int


        var finalChapterNumber = getFinalChapter(mangaJson)
        val chapterJson = json.getAsJsonObject("chapter")
        val chapters = mutableListOf<SChapter>()

        //skip chapters that dont match the desired language, or are future releases
        chapterJson?.forEach { key, jsonElement ->
            val chapterElement = jsonElement.asJsonObject
            if (chapterElement.get("lang_code").string == internalLang && (chapterElement.get("timestamp").asLong * 1000) <= now) {
                chapterElement.toString()
                chapters.add(chapterFromJson(key, chapterElement, finalChapterNumber, status))
            }
        }
        return chapters
    }

    private fun chapterFromJson(chapterId: String, chapterJson: JsonObject, finalChapterNumber: String, status: Int): SChapter {
        val chapter = SChapter.create()
        chapter.url = API_CHAPTER + chapterId
        val chapterName = mutableListOf<String>()
        //build chapter name
        if (chapterJson.get("volume").string.isNotBlank()) {
            chapterName.add("Vol." + chapterJson.get("volume").string)
        }
        if (chapterJson.get("chapter").string.isNotBlank()) {
            chapterName.add("Ch." + chapterJson.get("chapter").string)
        }
        if (chapterJson.get("title").string.isNotBlank()) {
            if (!chapterName.isEmpty()) {
                chapterName.add("-")
            }
            chapterName.add(chapterJson.get("title").string)
        }
        if ((status == 2 || status == 3) && doesFinalChapterExist(finalChapterNumber, chapterJson)) {
            chapterName.add("[END]")
        }

        chapter.name = cleanString(chapterName.joinToString(" "))
        //convert from unix time
        chapter.date_upload = chapterJson.get("timestamp").long * 1000
        var scanlatorName = mutableListOf<String>()
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

    override fun pageListParse(document: Document) = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> {
        var jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject

        val pages = mutableListOf<Page>()

        val hash = json.get("server").string
        val pageArray = json.getAsJsonArray("page_array")
        val server = json.get("server").string

        pageArray.forEach {
            val url = "$server$hash/$it"
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
        //some images are hosted elsewhere
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
        screen.addPreference(myPref)
    }

    private fun getShowR18(): Int = preferences.getInt(SHOW_R18_PREF, 0)


    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Genre(val id: String, name: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class R18 : Filter.Select<String>("R18+", arrayOf("Show all", "Show only", "Show none"))
    private class Demographic : Filter.Select<String>("Demographic", arrayOf("All", "Shounen", "Shoujo", "Seinen", "Josei"))
    private class OriginalLanguage : Filter.Select<String>("Original Language", SOURCE_LANG_LIST.map { it -> it.first }.toTypedArray())


    override fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Artist", "artist"),
            R18(),
            Demographic(),
            OriginalLanguage(),
            GenreList(GENRE_LIST)
    )


    companion object {
        //this number matches to the cookie
        private const val NO_R18 = 0
        private const val ALL = 1
        private const val ONLY_R18 = 2
        private const val SHOW_R18_PREF_Title = "Default R18 Setting"
        private const val SHOW_R18_PREF = "showR18Default"
        private const val API_MANGA = "/api/manga/"
        private const val API_CHAPTER = "/api/chapter/"
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
                Pair("Filipino", "34")
        )
        private val GENRE_LIST = listOf(
                Genre("1", "4-koma"),
                Genre("2", "Action"),
                Genre("3", "Adventure"),
                Genre("4", "Award Winning"),
                Genre("5", "Comedy"),
                Genre("6", "Cooking"),
                Genre("7", "Doujinshi"),
                Genre("8", "Drama"),
                Genre("9", "Ecchi"),
                Genre("10", "Fantasy"),
                Genre("11", "Gender Bender"),
                Genre("12", "Harem"),
                Genre("13", "Historical"),
                Genre("14", "Horror"),
                Genre("15", "Josei"),
                Genre("16", "Martial Arts"),
                Genre("17", "Mecha"),
                Genre("18", "Medical"),
                Genre("19", "Music"),
                Genre("20", "Mystery"),
                Genre("21", "Oneshot"),
                Genre("22", "Psychological"),
                Genre("23", "Romance"),
                Genre("24", "School Life"),
                Genre("25", "Sci-Fi"),
                Genre("26", "Seinen"),
                Genre("27", "Shoujo"),
                Genre("28", "Shoujo Ai"),
                Genre("29", "Shounen"),
                Genre("30", "Shounen Ai"),
                Genre("31", "Slice of Life"),
                Genre("32", "Smut"),
                Genre("33", "Sports"),
                Genre("34", "Supernatural"),
                Genre("35", "Tragedy"),
                Genre("36", "Webtoon"),
                Genre("37", "Yaoi"),
                Genre("38", "Yuri"),
                Genre("39", "[no chapters]"),
                Genre("40", "Game"),
                Genre("41", "Isekai"))

    }
}