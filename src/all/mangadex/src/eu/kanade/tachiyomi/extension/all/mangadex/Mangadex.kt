package eu.kanade.tachiyomi.extension.all.mangadex

import com.github.salomonbrys.kotson.forEach
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.TimeUnit

open class Mangadex(override val lang: String, private val internalLang: String, val pageStart: Int) : ParsedHttpSource() {

    override val name = "MangaDex"

    override val baseUrl = "https://mangadex.org"

    override val supportsLatest = true

    override val client = clientBuilder(ALL)

    private fun clientBuilder(r18Toggle: Int): OkHttpClient = network.cloudflareClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val newReq = chain
                        .request()
                        .newBuilder()
                        .addHeader("Cookie", cookiesHeader(r18Toggle))
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
                        .build()
                chain.proceed(newReq)
            }.build()!!

    private fun cookiesHeader(r18Toggle: Int): String {
        val cookies = mutableMapOf<String, String>()
        cookies["mangadex_h_toggle"] = r18Toggle.toString()
        return buildCookies(cookies)
    }

    private fun buildCookies(cookies: Map<String, String>) = cookies.entries.joinToString(separator = "; ", postfix = ";") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    override fun popularMangaSelector() = ".table-responsive tbody tr"

    override fun latestUpdatesSelector() = ".table-responsive tbody tr a.manga_title[href*=manga]"

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page != 1) "/" + ((page * 100) - 100) else ""
        return GET("$baseUrl/titles$pageStr", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page != 1) "/" + ((page * 20)) else ""
        return GET("$baseUrl/$pageStart$pageStr", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a[href*=manga]").first().let {
            val url = removeMangaNameFromUrl(it.attr("href"))
            manga.setUrlWithoutDomain(url)
            manga.thumbnail_url = baseUrl + "/images" + manga.url.substringBeforeLast("/") + ".jpg"
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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return getSearchClient(filters).newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
    }

    /* get search client based off r18 filter.  This will always return default client builder now until r18 solution is found or login is add
     */
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
        val genres = mutableListOf<String>()
        if (byGenre != null) {
            byGenre as GenreList
            byGenre.state.forEach { genre ->
                when (genre.state) {
                    true -> genres.add(genre.id)
                }
            }
        }
        //do browse by letter if set
        val byLetter = filters.find { it is ByLetter }

        if (byLetter != null && (byLetter as ByLetter).state.first().state != 0) {
            val s = byLetter.state.first().values[byLetter.state.first().state]
            val pageStr = if (page != 1) (((page - 1) * 100)).toString() else "0"
            val url = HttpUrl.parse("$baseUrl/titles/")!!.newBuilder().addPathSegment(s).addPathSegment(pageStr)
            return GET(url.toString(), headers)

        } else {
            //do traditional search
            val url = HttpUrl.parse("$baseUrl/?page=search")!!.newBuilder().addQueryParameter("title", query)
            filters.forEach { filter ->
                when (filter) {
                    is TextField -> url.addQueryParameter(filter.key, filter.state)
                }
            }
            if (genres.isNotEmpty()) url.addQueryParameter("genres", genres.joinToString(","))

            return GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = ".table.table-striped.table-hover.table-condensed tbody tr"

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(apiRequest(manga))
                .asObservableSuccess()
                .map { response ->
                    mangaDetailsParse(response).apply { initialized = true }
                }
    }

    private fun apiRequest(manga: SManga): Request {
        return GET(baseUrl + URL + getMangaId(manga.url), headers)
    }

    private fun getMangaId(url: String) = url.trimEnd('/').substringAfterLast("/")

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = SManga.create()
        var jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject
        val mangaJson = json.getAsJsonObject("manga")
        manga.thumbnail_url = baseUrl + mangaJson.get("cover_url").string
        manga.description = cleanString(mangaJson.get("description").string)
        manga.author = mangaJson.get("author").string
        manga.artist = mangaJson.get("artist").string
        manga.status = parseStatus(mangaJson.get("status").int)
        var genres = mutableListOf<String>()
        mangaJson.get("genres").asJsonArray.forEach { it ->
            getGenre(it.int)?.let { name ->
                genres.add(name)
            }
        }
        manga.genre = genres.joinToString(", ")
        return manga
    }

    private fun cleanString(description: String): String {
        return Jsoup.parseBodyFragment(description.replace("[list]", "").replace("[/list]", "").replace("[*]", "").replace("""\[(\w+)[^\]]*](.*?)\[/\1]""".toRegex(), "$2")).text()
    }


    private fun getGenre(int: Int): String? = GENRE_MAP.getValue(int)?.name

    override fun mangaDetailsParse(document: Document) = throw Exception("Not Used")

    override fun chapterListSelector() = ""

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(apiRequest(manga))
                .asObservableSuccess()
                .map { response ->
                    chapterListParse(response)
                }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val now = Date().time
        var jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject
        val chapterJson = json.getAsJsonObject("chapter")
        val chapters = mutableListOf<SChapter>()

        chapterJson?.forEach { key, jsonElement ->
            val chapterElement = jsonElement.asJsonObject
            if (chapterElement.get("lang_code").string == internalLang && chapterElement.get("timestamp").asLong <= now) {
                chapterElement.toString()
                chapters.add(chapterFromJson(key, chapterElement))
            }
        }
        return chapters
    }

    private fun chapterFromJson(chapterId: String, chapterJson: JsonObject): SChapter {
        val chapter = SChapter.create()
        chapter.url = BASE_CHAPTER + chapterId
        var chapterName = mutableListOf<String>()
        if (chapterJson.get("volume").string.isNotBlank()) {
            chapterName.add("Vol." + chapterJson.get("volume").string)
        }
        if (chapterJson.get("chapter").string.isNotBlank()) {
            chapterName.add("Ch." + chapterJson.get("chapter").string)
        }
        if (chapterJson.get("title").string.isNotBlank()) {
            chapterName.add(chapterJson.get("title").string)
        }

        chapter.name = cleanString(chapterName.joinToString(" "))
        chapter.date_upload = chapterJson.get("timestamp").long * 1000
        chapter.scanlator = chapterJson.get("group_name").string
        return chapter
    }

    override fun chapterFromElement(element: Element) = throw Exception("Not used")

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val url = document.baseUri()

        val dataUrl = document.select("script").last().html().substringAfter("dataurl = '").substringBefore("';")
        val imageUrl = document.select("script").last().html().substringAfter("page_array = [").substringBefore("];")
        val listImageUrls = imageUrl.replace("'", "").split(",")
        val server = document.select("script").last().html().substringAfter("server = '").substringBefore("';")

        listImageUrls.filter { it.isNotBlank() }.forEach {
            val url = "$server$dataUrl/$it"
            pages.add(Page(pages.size, "", getImageUrl(url)))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = ""

    private fun parseStatus(status: Int) = when (status) {
        1 -> SManga.ONGOING
        2 -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun getImageUrl(attr: String): String {
        //some images are hosted elsewhere
        if (attr.startsWith("http")) {
            return attr
        }
        return baseUrl + attr
    }

    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Genre(val id: String, name: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class R18 : Filter.Select<String>("R18+", arrayOf("Show all", "Show only", "Show none"))
    private class ByLetter(letters: List<Letters>) : Filter.Group<Letters>("Browse by Letter only", letters)
    private class Letters : Filter.Select<String>("Letter", arrayOf("", "~", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"))

    override fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Artist", "artist"),
            R18(),
            GenreList(getGenreList()),
            ByLetter(listOf(Letters()))
    )


    private fun getGenreList() = GENRE_MAP.values.toList()

    companion object {
        //this number matches to the cookie
        private const val NO_R18 = 0
        private const val ALL = 1
        private const val ONLY_R18 = 2
        private const val URL = "/api/3640f3fb/"
        private const val BASE_CHAPTER = "/chapter/"
        private val GENRE_MAP = mapOf(
                1 to Genre("1", "4-koma"),
                2 to Genre("2", "Action"),
                3 to Genre("3", "Adventure"),
                4 to Genre("4", "Award Winning"),
                5 to Genre("5", "Comedy"),
                6 to Genre("6", "Cooking"),
                7 to Genre("7", "Doujinshi"),
                8 to Genre("8", "Drama"),
                9 to Genre("9", "Ecchi"),
                10 to Genre("10", "Fantasy"),
                11 to Genre("11", "Gender Bender"),
                12 to Genre("12", "Harem"),
                13 to Genre("13", "Historical"),
                14 to Genre("14", "Horror"),
                15 to Genre("15", "Josei"),
                16 to Genre("16", "Martial Arts"),
                17 to Genre("17", "Mecha"),
                18 to Genre("18", "Medical"),
                19 to Genre("19", "Music"),
                20 to Genre("20", "Mystery"),
                21 to Genre("21", "Oneshot"),
                22 to Genre("22", "Psychological"),
                23 to Genre("23", "Romance"),
                24 to Genre("24", "School Life"),
                25 to Genre("25", "Sci-Fi"),
                26 to Genre("26", "Seinen"),
                27 to Genre("27", "Shoujo"),
                28 to Genre("28", "Shoujo Ai"),
                29 to Genre("29", "Shounen"),
                30 to Genre("30", "Shounen Ai"),
                31 to Genre("31", "Slice of Life"),
                32 to Genre("32", "Smut"),
                33 to Genre("33", "Sports"),
                34 to Genre("34", "Supernatural"),
                35 to Genre("35", "Tragedy"),
                36 to Genre("36", "Webtoon"),
                37 to Genre("37", "Yaoi"),
                38 to Genre("38", "Yuri"),
                39 to Genre("39", "[no chapters]"),
                40 to Genre("40", "Game"))
    }
}